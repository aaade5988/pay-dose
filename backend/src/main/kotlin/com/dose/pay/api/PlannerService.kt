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
        )
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
