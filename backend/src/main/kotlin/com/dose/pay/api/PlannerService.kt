package com.dose.pay.api

import com.dose.pay.domain.Calculator
import com.dose.pay.domain.PaymentMethodEntity
import com.dose.pay.domain.PaymentMethodRepository
import com.dose.pay.domain.TransactionEntity
import com.dose.pay.domain.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 业务异常：携带 HTTP 状态码与中文 detail，由全局异常处理器统一转换。
 *
 * `errors` 用于 422 场景，携带与原 pydantic 同形的明细（loc/msg/type/ctx/input）。
 */
class ApiException(
    val status: Int,
    override val message: String,
    val errors: List<Map<String, Any?>> = emptyList(),
) : RuntimeException(message) {
    companion object {
        fun badRequest(detail: String) = ApiException(400, detail)
        fun notFound(detail: String) = ApiException(404, detail)
        fun conflict(detail: String) = ApiException(409, detail)

        /** 构造 pydantic `literal_error` 形状的 422 */
        fun literalError(field: String, label: String, input: String, allowed: List<String>): ApiException {
            val expectedText = allowed.joinToString(" or ") { "'$it'" }
            val allowedText = allowed.joinToString(" / ")
            val error = linkedMapOf<String, Any?>(
                "ctx" to linkedMapOf("expected" to expectedText),
                "input" to input,
                "loc" to listOf("body", field),
                "msg" to "Input should be $expectedText",
                "type" to "literal_error",
            )
            return ApiException(422, "请求参数格式错误：$label：取值必须是：$allowedText", listOf(error))
        }
    }
}

/**
 * 支付方式 + 消费流水服务。
 *
 * 所有校验文案与业务规则沿用原 FastAPI 后端，保证前端零改动可用。
 */
@Service
class PlannerService(
    private val methodRepo: PaymentMethodRepository,
    private val txnRepo: TransactionRepository,
) {

    companion object {
        const val DEFAULT_TOP_N = 3
        const val DEFAULT_TXN_LIMIT = 50
        const val MAX_TXN_LIMIT = 200
        const val MAX_TXN_NOTE = 50

        private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    // -------------------- 金额 / 日期校验 --------------------

    /**
     * 金额字符串 → BigDecimal（两位小数）；非法 → 400。
     *
     * - 空白 / 非数字 → "{label}请输入有效金额"
     * - 负数 → "{label}不能小于 0"
     */
    fun parseAmount(value: String?, label: String): BigDecimal {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("${label}请输入有效金额")
        }
        val amount = try {
            BigDecimal(value.trim())
        } catch (e: NumberFormatException) {
            throw ApiException.badRequest("${label}请输入有效金额")
        }
        if (amount < BigDecimal.ZERO) {
            throw ApiException.badRequest("${label}不能小于 0")
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN)
    }

    /** ISO 日期字符串 → LocalDate；非法 → 400 */
    fun parseDate(value: String?, label: String): LocalDate {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("${label}不能为空，格式 YYYY-MM-DD")
        }
        return try {
            LocalDate.parse(value.trim())
        } catch (e: Exception) {
            throw ApiException.badRequest("${label}格式不正确，请使用 YYYY-MM-DD")
        }
    }

    // -------------------- 支付方式 CRUD --------------------

    fun listMethods(): List<MethodDTO> =
        methodRepo.findAllByActiveTrueOrderByIdAsc().map { it.toDto() }

    @Transactional
    fun createMethod(input: MethodInput): MethodDTO {
        val entity = buildMethodEntity(input)
        methodRepo.findByName(entity.name)?.let {
            throw ApiException.conflict("支付方式名称「${entity.name}」已存在")
        }
        return methodRepo.save(entity).toDto()
    }

    @Transactional
    fun updateMethod(methodId: Long, input: MethodInput): MethodDTO {
        val existing = methodRepo.findById(methodId).orElse(null)
            ?: throw ApiException.notFound("支付方式 $methodId 不存在")

        val payload = buildMethodEntity(input)
        // 与他行重名 → 409（改成自己同名允许）
        methodRepo.findByName(payload.name)?.let { found ->
            if (found.id != methodId) {
                throw ApiException.conflict("支付方式名称「${payload.name}」已被其他记录使用")
            }
        }

        // active 与 created_at 保持不变
        existing.name = payload.name
        existing.methodType = payload.methodType
        existing.creditLimit = payload.creditLimit
        existing.currentUsed = payload.currentUsed
        existing.statementDay = payload.statementDay
        existing.dueDay = payload.dueDay
        existing.dueDaysAfterStatement = payload.dueDaysAfterStatement
        existing.statementDayInclusive = payload.statementDayInclusive

        return methodRepo.save(existing).toDto()
    }

    @Transactional
    fun deleteMethod(methodId: Long) {
        methodRepo.findById(methodId).orElse(null)
            ?: throw ApiException.notFound("支付方式 $methodId 不存在")

        val txnCount = txnRepo.countByPaymentMethodId(methodId)
        if (txnCount > 0) {
            throw ApiException.conflict("该卡还有 $txnCount 笔流水，请先删除流水")
        }
        methodRepo.deleteById(methodId)
    }

    /** 请求体 → 实体，同时执行业务校验（失败 → 400 / 422） */
    private fun buildMethodEntity(input: MethodInput): PaymentMethodEntity {
        val name = input.name.trim()
        if (name.isEmpty()) {
            throw ApiException.badRequest("名称不能为空")
        }
        if (input.methodType !in Calculator.METHOD_TYPES) {
            throw ApiException.literalError("method_type", "类型", input.methodType, Calculator.METHOD_TYPES)
        }

        val currentUsed = parseAmount(input.currentUsed, "当前使用金额")
        val limitText = input.creditLimit?.trim()
        val creditLimit = if (limitText.isNullOrEmpty()) null else parseAmount(limitText, "信用额度")

        if (input.methodType == Calculator.CREDIT_CARD) {
            if (creditLimit == null) {
                throw ApiException.badRequest("信用卡必须填写信用额度")
            }
            if (input.statementDay == null) {
                throw ApiException.badRequest("信用卡必须填写账单日")
            }
            if (input.dueDay == null && input.dueDaysAfterStatement == null) {
                throw ApiException.badRequest("信用卡必须填写还款日或账单日后还款天数")
            }
        }

        return PaymentMethodEntity(
            name = name,
            methodType = input.methodType,
            creditLimit = creditLimit,
            currentUsed = currentUsed,
            statementDay = input.statementDay,
            dueDay = input.dueDay,
            dueDaysAfterStatement = input.dueDaysAfterStatement,
            statementDayInclusive = input.statementDayInclusive,
            active = true,
            createdAt = LocalDateTime.now(),
        )
    }

    // -------------------- 推荐（只读，不落库） --------------------

    fun recommend(request: RecommendRequest): RecommendResponse {
        val paymentDate = parseDate(request.paymentDate, "支付日期")
        val amount = parseAmount(request.amount, "支付金额")
        if (amount <= BigDecimal.ZERO) {
            throw ApiException.badRequest("支付金额必须大于 0")
        }
        val topN = request.topN ?: DEFAULT_TOP_N

        val methods = methodRepo.findAllByActiveTrueOrderByIdAsc().map { it.toDomain() }
        val results = try {
            Calculator.recommend(methods, amount, paymentDate, topN)
        } catch (e: IllegalArgumentException) {
            throw ApiException.badRequest(e.message ?: "计算失败")
        }

        return RecommendResponse(
            results = results.map { item ->
                RecItem(
                    paymentMethodId = item.paymentMethodId,
                    paymentMethod = item.paymentMethod,
                    currentUsed = Calculator.formatAmount(item.currentUsed) ?: "0.00",
                    afterUsed = Calculator.formatAmount(item.afterUsed) ?: "0.00",
                    creditLimit = Calculator.formatAmount(item.creditLimit),
                    statementDay = item.statementDay,
                    dueDate = item.dueDate.toString(),
                    occupiedDays = item.occupiedDays,
                    reason = item.reason,
                )
            }
        )
    }

    // -------------------- 看板：按账单周期聚合（只读） --------------------

    /**
     * 看板口径：**当前账单周期**的消费合计，不是自然月。
     *
     * 一条消费落在哪一期，由它的还款日决定（[Calculator.calculateDueDate]），
     * 因此 (上一还款日, 本还款日] 正好覆盖本期的全部消费：
     * - 上一还款日当天之后刷的 → 还款日落在本期，计入；
     * - 还款日当天刷的 → 还款日已经滚到下一期，不计入本期。
     *
     * **只统计信用卡**：储蓄卡 / 电子钱包没有账单周期（还款日 = 刷卡当天），
     * 混进来会让「本期」这个词失去意义，所以它们不进看板，也不进合计。
     * 信用卡但缺还款规则的同样跳过（无法归期）。
     *
     * 各卡周期长短不一（28–31 天）且互不相同，所以总量是「各卡本期之和」，
     * 不能理解成同一个日期区间内的合计。
     */
    fun cycleSummary(): CycleSummaryDTO {
        val today = LocalDate.now()
        val methods = methodRepo.findAllByActiveTrueOrderByIdAsc()
            .map { it.toDomain() }
            .filter { it.methodType == Calculator.CREDIT_CARD }
        val txnsByCard: Map<Long, List<TransactionEntity>> =
            if (methods.isEmpty()) emptyMap() else txnRepo.findAll().groupBy { it.paymentMethodId }

        var totalSpend = BigDecimal.ZERO
        var totalRepay = BigDecimal.ZERO
        var settledSpend = BigDecimal.ZERO
        val cards = ArrayList<CycleCardDTO>(methods.size)

        for (method in methods) {
            // 缺账单日 / 还款规则 → cycleWindow 返回 null，该卡无法归期，跳过
            val window = Calculator.cycleWindow(today, method) ?: continue
            val cycleEnd = window.second

            var spend = BigDecimal.ZERO
            var repay = BigDecimal.ZERO
            var spendCount = 0
            var repayCount = 0

            for (txn in txnsByCard[method.id].orEmpty()) {
                // 归期判据：这笔消费的还款日 == 本还款日
                if (Calculator.cycleEndFor(txn.txnDate, method) != cycleEnd) continue
                if (txn.type == Calculator.TXN_REPAY) {
                    repay = repay.add(txn.amount)
                    repayCount++
                } else {
                    spend = spend.add(txn.amount)
                    spendCount++
                }
            }

            totalSpend = totalSpend.add(spend)
            totalRepay = totalRepay.add(repay)

            // 「已还清」是人工标记（不是流水）：该卡本期消费不再计入额度占用，
            // 仪表盘里这一段会被冻结成另一种颜色。
            val settled = method.settledUntil != null && !method.settledUntil.isBefore(today)
            if (settled) settledSpend = settledSpend.add(spend)

            cards.add(
                CycleCardDTO(
                    paymentMethodId = method.id,
                    paymentMethodName = method.name,
                    methodType = method.methodType,
                    basis = "cycle",
                    cycleStart = window.first.toString(),
                    cycleEnd = cycleEnd.toString(),
                    daysToDue = ChronoUnit.DAYS.between(today, cycleEnd).toInt(),
                    spendAmount = Calculator.formatAmount(spend) ?: "0.00",
                    repayAmount = Calculator.formatAmount(repay) ?: "0.00",
                    netAmount = Calculator.formatAmount(spend.subtract(repay)) ?: "0.00",
                    spendCount = spendCount,
                    repayCount = repayCount,
                    currentUsed = Calculator.formatAmount(method.currentUsed) ?: "0.00",
                    settled = settled,
                )
            )
        }

        return CycleSummaryDTO(
            asOf = today.toString(),
            totalSpend = Calculator.formatAmount(totalSpend) ?: "0.00",
            totalRepay = Calculator.formatAmount(totalRepay) ?: "0.00",
            totalNet = Calculator.formatAmount(totalSpend.subtract(totalRepay)) ?: "0.00",
            settledSpend = Calculator.formatAmount(settledSpend) ?: "0.00",
            cards = cards,
        )
    }

    /**
     * 标记 / 取消「已还清」。
     *
     * 这是**人工标记**，不写任何流水，也不改 `current_used`：
     * 只是告诉看板「这张卡本期这笔钱我已经还了」，于是
     * - 看板这一行显示「已还清」而不是金额；
     * - 仪表盘里它占的那一段被冻结成另一种颜色，不再计入额度占用。
     *
     * 标记有效期 = 当前账单周期的还款日：过了这天进入下一期，标记自动失效。
     */
    @Transactional
    fun setSettled(methodId: Long, settled: Boolean): MethodDTO {
        val entity = methodRepo.findById(methodId).orElse(null)
            ?: throw ApiException.notFound("支付方式 $methodId 不存在")

        if (settled) {
            val window = Calculator.cycleWindow(LocalDate.now(), entity.toDomain())
                ?: throw ApiException.badRequest("「${entity.name}」没有账单周期（只有信用卡能标记已还清）")
            entity.settledUntil = window.second
        } else {
            entity.settledUntil = null
        }
        return methodRepo.save(entity).toDto()
    }

    // -------------------- 消费流水（余额同事务原子维护） --------------------

    /**
     * 记一笔流水，并在同一事务内原子调整 current_used（消费 +、还款 −）。
     *
     * 校验顺序与原实现一致：备注长度 → 日期 → 金额 → 类型 → 卡存在 → 还款上限。
     */
    @Transactional
    fun createTransaction(input: TxnInput): TxnDTO {
        if (input.note.length > MAX_TXN_NOTE) {
            throw ApiException.badRequest("备注不能超过 $MAX_TXN_NOTE 字")
        }
        val txnDate = parseDate(input.txnDate, "交易日期")
        val amount = parseAmount(input.amount, "金额")
        if (amount <= BigDecimal.ZERO) {
            throw ApiException.badRequest("金额必须大于 0")
        }
        if (input.type !in Calculator.TXN_TYPES) {
            throw ApiException.literalError("type", "类型", input.type, Calculator.TXN_TYPES)
        }

        val card = methodRepo.lockById(input.paymentMethodId)
            ?: throw ApiException.notFound("支付方式 ${input.paymentMethodId} 不存在")

        val currentUsed = card.currentUsed
        if (input.type == Calculator.TXN_REPAY && amount > currentUsed) {
            throw ApiException.badRequest(
                "还款金额不能超过当前已用 ¥${Calculator.formatAmount(currentUsed)}"
            )
        }

        val delta = if (input.type == Calculator.TXN_CONSUME) amount else amount.negate()
        card.currentUsed = Calculator.quantize(currentUsed.add(delta))
        methodRepo.save(card)

        val saved = txnRepo.save(
            TransactionEntity(
                paymentMethodId = input.paymentMethodId,
                type = input.type,
                amount = Calculator.quantize(amount),
                txnDate = txnDate,
                note = input.note,
                createdAt = LocalDateTime.now(),
            )
        )
        return saved.toDto(card.name)
    }

    /** 流水分页（txn_date DESC, id DESC） */
    fun listTransactions(methodId: Long?, limit: Int, offset: Int): TxnsResponse {
        if (limit < 1 || limit > MAX_TXN_LIMIT) {
            throw ApiException.badRequest("limit 必须在 1-$MAX_TXN_LIMIT 之间")
        }
        if (offset < 0) {
            throw ApiException.badRequest("offset 不能小于 0")
        }
        // 先取 offset+limit 条再丢弃前 offset 条：数据量小，语义与 LIMIT/OFFSET 等价
        val pageable = org.springframework.data.domain.PageRequest.of(0, offset + limit)
        val fetched = if (methodId == null) txnRepo.pageAll(pageable) else txnRepo.pageByMethod(methodId, pageable)
        val rows = fetched.drop(offset)

        val nameMap = methodRepo.findAllById(rows.map { it.paymentMethodId }.distinct())
            .associate { (it.id ?: 0L) to it.name }
        return TxnsResponse(transactions = rows.map { it.toDto(nameMap[it.paymentMethodId] ?: "") })
    }

    /**
     * 删除流水，并在同一事务内回滚余额（消费删 → 减回；还款删 → 加回）。
     *
     * @throws ApiException 流水不存在 → 404
     */
    @Transactional
    fun deleteTransaction(txnId: Long): TxnDeleteResponse {
        val txn = txnRepo.lockById(txnId)
            ?: throw ApiException.notFound("流水 $txnId 不存在")

        val card = methodRepo.lockById(txn.paymentMethodId)
            ?: throw ApiException.notFound("流水 $txnId 不存在")

        val delta = if (txn.type == Calculator.TXN_CONSUME) txn.amount.negate() else txn.amount
        card.currentUsed = Calculator.quantize(card.currentUsed.add(delta))
        methodRepo.save(card)

        txnRepo.delete(txn)
        return TxnDeleteResponse(
            ok = true,
            cardCurrentUsed = Calculator.formatAmount(card.currentUsed) ?: "0.00",
        )
    }

    // -------------------- 实体 → DTO --------------------

    private fun PaymentMethodEntity.toDto(): MethodDTO {
        val limitText = Calculator.formatAmount(creditLimit)
        val usedText = Calculator.formatAmount(currentUsed) ?: "0.00"
        return MethodDTO(
            id = id ?: 0L,
            name = name,
            methodType = methodType,
            creditLimit = limitText,
            currentUsed = usedText,
            availableLimit = Calculator.formatAmount(Calculator.availableLimit(creditLimit, currentUsed)),
            statementDay = statementDay,
            dueDay = dueDay,
            dueDaysAfterStatement = dueDaysAfterStatement,
            statementDayInclusive = statementDayInclusive,
            active = active,
            settled = isSettledNow(),
        )
    }

    /**
     * 「已还清」标记是否当前生效。
     *
     * 标记带有效期（= 标记时所在账单周期的还款日），过了就自动失效，
     * 下一期恢复正常占用，不需要人工清理。
     */
    private fun PaymentMethodEntity.isSettledNow(): Boolean {
        val until = settledUntil ?: return false
        return !until.isBefore(LocalDate.now())
    }

    private fun TransactionEntity.toDto(methodName: String): TxnDTO = TxnDTO(
        id = id ?: 0L,
        paymentMethodId = paymentMethodId,
        paymentMethodName = methodName,
        type = type,
        amount = Calculator.formatAmount(amount) ?: "0.00",
        txnDate = txnDate.toString(),
        note = note,
        createdAt = createdAt.format(DATETIME_FMT),
    )
}
