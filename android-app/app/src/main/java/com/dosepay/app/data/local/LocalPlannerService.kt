package com.dosepay.app.data.local

import androidx.room.withTransaction
import com.dosepay.app.core.Calculator
import com.dosepay.app.core.PaymentMethod
import com.dosepay.app.data.ApiError
import com.dosepay.app.data.CycleCardDto
import com.dosepay.app.data.CycleSummaryDto
import com.dosepay.app.data.MethodDto
import com.dosepay.app.data.MethodInput
import com.dosepay.app.data.RecItem
import com.dosepay.app.data.RecommendRequest
import com.dosepay.app.data.TxnDto
import com.dosepay.app.data.TxnInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 本地业务服务：把 backend 的 PlannerService 行为完整搬到 App 内。
 *
 * 校验文案、校验顺序、错误码全部沿用后端，保证界面提示零变化：
 * - 400 参数/业务错误、404 不存在、409 冲突、422 取值非法。
 *
 * 与后端的唯一差别：不再有 HTTP 层，所有方法直接返回 DTO。
 */
class LocalPlannerService(private val db: PayDoseDatabase) {

    private val methodDao = db.paymentMethodDao()
    private val txnDao = db.transactionDao()

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
    private fun parseAmount(value: String?, label: String): BigDecimal {
        if (value == null || value.isBlank()) {
            throw ApiError(400, "${label}请输入有效金额")
        }
        val amount = try {
            BigDecimal(value.trim())
        } catch (e: NumberFormatException) {
            throw ApiError(400, "${label}请输入有效金额")
        }
        if (amount < BigDecimal.ZERO) {
            throw ApiError(400, "${label}不能小于 0")
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN)
    }

    /** ISO 日期字符串 → LocalDate；非法 → 400 */
    private fun parseDate(value: String?, label: String): LocalDate {
        if (value == null || value.isBlank()) {
            throw ApiError(400, "${label}不能为空，格式 YYYY-MM-DD")
        }
        return try {
            LocalDate.parse(value.trim())
        } catch (e: Exception) {
            throw ApiError(400, "${label}格式不正确，请使用 YYYY-MM-DD")
        }
    }

    /** 构造 pydantic `literal_error` 形状的 422 */
    private fun literalError(field: String, label: String, input: String, allowed: List<String>): ApiError {
        val allowedText = allowed.joinToString(" / ")
        return ApiError(422, "请求参数格式错误：$label：取值必须是：$allowedText")
    }

    // -------------------- 支付方式 CRUD --------------------

    suspend fun listMethods(): List<MethodDto> =
        methodDao.findAllActive().map { it.toDto() }

    suspend fun createMethod(input: MethodInput): MethodDto = db.withTransaction {
        val entity = buildMethodEntity(input)
        methodDao.findByName(entity.name)?.let {
            throw ApiError(409, "支付方式名称「${entity.name}」已存在")
        }
        val id = methodDao.insert(entity)
        methodDao.findById(id)!!.toDto()
    }

    suspend fun updateMethod(methodId: Long, input: MethodInput): MethodDto = db.withTransaction {
        val existing = methodDao.findById(methodId)
            ?: throw ApiError(404, "支付方式 $methodId 不存在")

        val payload = buildMethodEntity(input)
        // 与他行重名 → 409（改成自己同名允许）
        methodDao.findByName(payload.name)?.let { found ->
            if (found.id != methodId) {
                throw ApiError(409, "支付方式名称「${payload.name}」已被其他记录使用")
            }
        }

        // active / created_at / settled_until 保持不变
        methodDao.update(
            existing.copy(
                name = payload.name,
                methodType = payload.methodType,
                creditLimit = payload.creditLimit,
                currentUsed = payload.currentUsed,
                statementDay = payload.statementDay,
                dueDay = payload.dueDay,
                dueDaysAfterStatement = payload.dueDaysAfterStatement,
                statementDayInclusive = payload.statementDayInclusive,
            )
        )
        methodDao.findById(methodId)!!.toDto()
    }

    suspend fun deleteMethod(methodId: Long) = db.withTransaction {
        methodDao.findById(methodId)
            ?: throw ApiError(404, "支付方式 $methodId 不存在")

        val txnCount = txnDao.countByMethodId(methodId)
        if (txnCount > 0) {
            throw ApiError(409, "该卡还有 $txnCount 笔流水，请先删除流水")
        }
        methodDao.deleteById(methodId)
    }

    /** 请求体 → 实体，同时执行业务校验（失败 → 400 / 422） */
    private fun buildMethodEntity(input: MethodInput): PaymentMethodEntity {
        val name = input.name.trim()
        if (name.isEmpty()) {
            throw ApiError(400, "名称不能为空")
        }
        if (input.methodType !in Calculator.METHOD_TYPES) {
            throw literalError("method_type", "类型", input.methodType, Calculator.METHOD_TYPES)
        }

        val currentUsed = parseAmount(input.currentUsed, "当前使用金额")
        val limitText = input.creditLimit?.trim()
        val creditLimit = if (limitText.isNullOrEmpty()) null else parseAmount(limitText, "信用额度")

        if (input.methodType == Calculator.CREDIT_CARD) {
            if (creditLimit == null) {
                throw ApiError(400, "信用卡必须填写信用额度")
            }
            if (input.statementDay == null) {
                throw ApiError(400, "信用卡必须填写账单日")
            }
            if (input.dueDay == null && input.dueDaysAfterStatement == null) {
                throw ApiError(400, "信用卡必须填写还款日或账单日后还款天数")
            }
        }

        return PaymentMethodEntity(
            name = name,
            methodType = input.methodType,
            creditLimit = Calculator.formatAmount(creditLimit),
            currentUsed = Calculator.formatAmount(currentUsed) ?: "0.00",
            statementDay = input.statementDay,
            dueDay = input.dueDay,
            dueDaysAfterStatement = input.dueDaysAfterStatement,
            statementDayInclusive = input.statementDayInclusive,
            active = true,
            createdAt = LocalDateTime.now().toString(),
        )
    }

    // -------------------- 推荐（只读，不落库） --------------------

    suspend fun recommend(request: RecommendRequest): List<RecItem> {
        val paymentDate = parseDate(request.paymentDate, "支付日期")
        val amount = parseAmount(request.amount, "支付金额")
        if (amount <= BigDecimal.ZERO) {
            throw ApiError(400, "支付金额必须大于 0")
        }
        val topN = request.topN ?: DEFAULT_TOP_N

        val methods = methodDao.findAllActive().map { it.toDomain() }
        val results = try {
            Calculator.recommend(methods, amount, paymentDate, topN)
        } catch (e: IllegalArgumentException) {
            throw ApiError(400, e.message ?: "计算失败")
        }

        return results.map { item ->
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
    }

    // -------------------- 看板：按账单周期聚合（只读） --------------------

    /**
     * 看板口径：**当前账单周期**的消费合计，不是自然月。
     *
     * 一条消费落在哪一期，由它的还款日决定（[Calculator.calculateDueDate]），
     * 因此 (上一还款日, 本还款日] 正好覆盖本期的全部消费。
     *
     * **只统计信用卡**：储蓄卡 / 电子钱包没有账单周期，不进看板、不进合计。
     */
    suspend fun cycleSummary(): CycleSummaryDto {
        val today = LocalDate.now()
        val methods = methodDao.findAllActive()
            .map { it.toDomain() }
            .filter { it.methodType == Calculator.CREDIT_CARD }
        val txnsByCard: Map<Long, List<TransactionEntity>> =
            if (methods.isEmpty()) emptyMap() else txnDao.findAll().groupBy { it.paymentMethodId }

        var totalSpend = BigDecimal.ZERO
        var totalRepay = BigDecimal.ZERO
        var settledSpend = BigDecimal.ZERO
        val cards = ArrayList<CycleCardDto>(methods.size)

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
                if (Calculator.cycleEndFor(txn.txnLocalDate, method) != cycleEnd) continue
                val amount = txn.amountDecimal
                if (txn.type == Calculator.TXN_REPAY) {
                    repay = repay.add(amount)
                    repayCount++
                } else {
                    spend = spend.add(amount)
                    spendCount++
                }
            }

            totalSpend = totalSpend.add(spend)
            totalRepay = totalRepay.add(repay)

            // 「已还清」是人工标记（不是流水）：该卡本期消费不再计入额度占用
            val settled = method.settledUntil != null && !method.settledUntil.isBefore(today)
            if (settled) settledSpend = settledSpend.add(spend)

            cards.add(
                CycleCardDto(
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

        return CycleSummaryDto(
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
     * 这是**人工标记**，不写任何流水，也不改 current_used。
     * 标记有效期 = 当前账单周期的还款日：过了这天进入下一期，标记自动失效。
     */
    suspend fun setSettled(methodId: Long, settled: Boolean): MethodDto = db.withTransaction {
        val entity = methodDao.findById(methodId)
            ?: throw ApiError(404, "支付方式 $methodId 不存在")

        val updated = if (settled) {
            val window = Calculator.cycleWindow(LocalDate.now(), entity.toDomain())
                ?: throw ApiError(400, "「${entity.name}」没有账单周期（只有信用卡能标记已还清）")
            entity.copy(settledUntil = window.second.toString())
        } else {
            entity.copy(settledUntil = null)
        }
        methodDao.update(updated)
        methodDao.findById(methodId)!!.toDto()
    }

    // -------------------- 消费流水（余额同事务原子维护） --------------------

    /**
     * 记一笔流水，并在同一事务内原子调整 current_used（消费 +、还款 −）。
     *
     * 校验顺序与后端一致：备注长度 → 日期 → 金额 → 类型 → 卡存在 → 还款上限。
     */
    suspend fun createTransaction(input: TxnInput): TxnDto = db.withTransaction {
        if (input.note.length > MAX_TXN_NOTE) {
            throw ApiError(400, "备注不能超过 $MAX_TXN_NOTE 字")
        }
        val txnDate = parseDate(input.txnDate, "交易日期")
        val amount = parseAmount(input.amount, "金额")
        if (amount <= BigDecimal.ZERO) {
            throw ApiError(400, "金额必须大于 0")
        }
        if (input.type !in Calculator.TXN_TYPES) {
            throw literalError("type", "类型", input.type, Calculator.TXN_TYPES)
        }

        val card = methodDao.findById(input.paymentMethodId)
            ?: throw ApiError(404, "支付方式 ${input.paymentMethodId} 不存在")

        val currentUsed = BigDecimal(card.currentUsed)
        if (input.type == Calculator.TXN_REPAY && amount > currentUsed) {
            throw ApiError(
                400,
                "还款金额不能超过当前已用 ¥${Calculator.formatAmount(currentUsed)}"
            )
        }

        val delta = if (input.type == Calculator.TXN_CONSUME) amount else amount.negate()
        val newUsed = Calculator.quantize(currentUsed.add(delta))
        methodDao.update(card.copy(currentUsed = Calculator.formatAmount(newUsed) ?: "0.00"))

        val txnId = txnDao.insert(
            TransactionEntity(
                paymentMethodId = input.paymentMethodId,
                type = input.type,
                amount = Calculator.formatAmount(Calculator.quantize(amount)) ?: "0.00",
                txnDate = txnDate.toString(),
                note = input.note,
                createdAt = LocalDateTime.now().toString(),
            )
        )
        txnDao.findById(txnId)!!.toDto(card.name)
    }

    /** 流水分页（txn_date DESC, id DESC） */
    suspend fun listTransactions(methodId: Long?, limit: Int, offset: Int): List<TxnDto> {
        if (limit < 1 || limit > MAX_TXN_LIMIT) {
            throw ApiError(400, "limit 必须在 1-$MAX_TXN_LIMIT 之间")
        }
        if (offset < 0) {
            throw ApiError(400, "offset 不能小于 0")
        }
        val rows = if (methodId == null) {
            txnDao.pageAll(limit, offset)
        } else {
            txnDao.pageByMethod(methodId, limit, offset)
        }

        val nameMap = methodDao.findAllActive().associate { it.id to it.name }
        return rows.map { it.toDto(nameMap[it.paymentMethodId] ?: "") }
    }

    /**
     * 删除流水，并在同一事务内回滚余额（消费删 → 减回；还款删 → 加回）。
     *
     * @return 删除后该卡已用金额字符串
     */
    suspend fun deleteTransaction(txnId: Long): String = db.withTransaction {
        val txn = txnDao.findById(txnId)
            ?: throw ApiError(404, "流水 $txnId 不存在")

        val card = methodDao.findById(txn.paymentMethodId)
            ?: throw ApiError(404, "流水 $txnId 不存在")

        val amount = txn.amountDecimal
        val delta = if (txn.type == Calculator.TXN_CONSUME) amount.negate() else amount
        val newUsed = Calculator.quantize(BigDecimal(card.currentUsed).add(delta))
        methodDao.update(card.copy(currentUsed = Calculator.formatAmount(newUsed) ?: "0.00"))

        txnDao.delete(txn)
        Calculator.formatAmount(newUsed) ?: "0.00"
    }

    // -------------------- 实体 → DTO --------------------

    private fun PaymentMethodEntity.toDto(): MethodDto {
        val limit = creditLimit?.let { BigDecimal(it) }
        val used = BigDecimal(currentUsed)
        return MethodDto(
            id = id,
            name = name,
            methodType = methodType,
            creditLimit = Calculator.formatAmount(limit),
            currentUsed = Calculator.formatAmount(used) ?: "0.00",
            availableLimit = Calculator.formatAmount(Calculator.availableLimit(limit, used)),
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
     * 标记带有效期（= 标记时所在账单周期的还款日），过了就自动失效。
     */
    private fun PaymentMethodEntity.isSettledNow(): Boolean {
        val until = settledUntil?.let { LocalDate.parse(it) } ?: return false
        return !until.isBefore(LocalDate.now())
    }

    private fun TransactionEntity.toDto(methodName: String): TxnDto = TxnDto(
        id = id,
        paymentMethodId = paymentMethodId,
        paymentMethodName = methodName,
        type = type,
        amount = Calculator.formatAmount(BigDecimal(amount)) ?: "0.00",
        txnDate = txnDate,
        note = note,
        createdAt = try {
            LocalDateTime.parse(createdAt).format(DATETIME_FMT)
        } catch (e: Exception) {
            createdAt
        },
    )
}
