package com.dose.pay.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 支付方式领域模型。
 *
 * 复刻自原 credit-payment-planner 的 PaymentMethod：
 * 金额全程 BigDecimal（对应 Python Decimal），绝不使用 Double，避免浮点误差。
 */
data class PaymentMethod(
    val id: Long,
    val name: String,
    val methodType: String,
    /** 信用额度；null = 不限额 */
    val creditLimit: BigDecimal?,
    /** 当前已用金额，由消费流水原子维护 */
    val currentUsed: BigDecimal,
    /** 账单日 1–31；非信用卡为 null */
    val statementDay: Int?,
    /** 固定还款日 1–31；与 dueDaysAfterStatement 二选一 */
    val dueDay: Int?,
    /** 账单日后 N 天（1–60）；与 dueDay 二选一 */
    val dueDaysAfterStatement: Int?,
    /** 账单日当天是否计入本期（false 时当天消费算下一期，资金占用更久） */
    val statementDayInclusive: Boolean,
    val active: Boolean = true,
)

/**
 * 推荐结果项。
 */
data class Recommendation(
    val paymentMethodId: Long,
    val paymentMethod: String,
    val currentUsed: BigDecimal,
    val afterUsed: BigDecimal,
    val creditLimit: BigDecimal?,
    val statementDay: Int?,
    val dueDate: LocalDate,
    val occupiedDays: Int,
    val reason: String,
)

/**
 * 核心业务：还款日推算 + Top-N 推荐。
 *
 * 这是整个系统的唯一算法事实源，行为必须与原 Python 实现一致：
 * - 非信用卡 → 即时扣款，还款日 = 支付日，占用 0 天；
 * - 信用卡 → 找「支付日当天或之后的下一个账单日」，再按固定还款日或账单日后 N 天推算；
 * - 所有日期按月末钳制（31 日 → 2 月 28/29 日）；
 * - 过滤「已用 + 本次 > 额度」，按占用天数降序取前 N 名。
 */
object Calculator {

    const val CREDIT_CARD = "信用卡"
    const val DEBIT_CARD = "储蓄卡"
    const val E_WALLET = "电子钱包"
    const val OTHER = "其他"

    val METHOD_TYPES = listOf(CREDIT_CARD, DEBIT_CARD, E_WALLET, OTHER)

    const val TXN_CONSUME = "消费"
    const val TXN_REPAY = "还款"

    val TXN_TYPES = listOf(TXN_CONSUME, TXN_REPAY)

    /** 金额统一两位小数 */
    @JvmField
    val TWO_PLACES: BigDecimal = BigDecimal("0.01")

    /**
     * 安全构造日期：day 超过当月天数时取当月最后一天。
     *
     * 对应原 _safe_date：day = min(day, calendar.monthrange(year, month)[1])
     */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(minOf(day, ym.lengthOfMonth()))
    }

    /**
     * 月份加减，返回 (year, month)。
     *
     * 对应原 _add_months：value = year*12 + (month-1) + months; return value//12, value%12+1
     * 用 floorDiv/floorMod 保持 Python 整除与取模语义。
     */
    private fun addMonths(year: Int, month: Int, months: Int): Pair<Int, Int> {
        val value = year * 12 + (month - 1) + months
        return Pair(Math.floorDiv(value, 12), Math.floorMod(value, 12) + 1)
    }

    /**
     * 下一个账单日（>= paymentDate）。
     *
     * inclusive 为 true 时，账单日当天算本期；为 false 时算下一期。
     */
    fun nextStatementDate(paymentDate: LocalDate, statementDay: Int, inclusive: Boolean): LocalDate {
        val candidate = safeDate(paymentDate.year, paymentDate.monthValue, statementDay)
        if (paymentDate.isBefore(candidate) || (inclusive && paymentDate == candidate)) {
            return candidate
        }
        val (year, month) = addMonths(paymentDate.year, paymentDate.monthValue, 1)
        return safeDate(year, month, statementDay)
    }

    /**
     * 计算某笔交易的保守还款日。
     *
     * 1. 非信用卡 → 返回支付日（即时扣款）；
     * 2. 信用卡缺账单日 → 抛异常；
     * 3. 有 dueDaysAfterStatement → 账单日 + N 天；
     * 4. 否则用 dueDay：取账单日所在月的该日，若 <= 账单日则顺延一个月（同样月末钳制）。
     *
     * @throws IllegalArgumentException 信用卡缺账单日或还款规则时
     */
    fun calculateDueDate(paymentDate: LocalDate, method: PaymentMethod): LocalDate {
        if (method.methodType != CREDIT_CARD) {
            return paymentDate
        }
        val statementDay = method.statementDay
            ?: throw IllegalArgumentException("支付方式 ${method.name} 缺少账单日")

        val statementDate = nextStatementDate(paymentDate, statementDay, method.statementDayInclusive)

        method.dueDaysAfterStatement?.let { days ->
            return statementDate.plusDays(days.toLong())
        }

        val dueDay = method.dueDay
            ?: throw IllegalArgumentException("支付方式 ${method.name} 缺少还款日")

        var dueYear = statementDate.year
        var dueMonth = statementDate.monthValue
        var dueDate = safeDate(dueYear, dueMonth, dueDay)
        if (!dueDate.isAfter(statementDate)) {
            val (y, m) = addMonths(dueYear, dueMonth, 1)
            dueYear = y
            dueMonth = m
            dueDate = safeDate(dueYear, dueMonth, dueDay)
        }
        return dueDate
    }

    /**
     * 生成推荐：过滤超额方式，按占用天数降序取前 N 名。
     *
     * 注意：排序是**稳定**的，占用天数相同时保持输入顺序（即卡 id 升序）。
     * 原实现没有「剩余额度多者优先」的次级排序，此处刻意不加，否则会与契约快照产生偏差。
     *
     * @throws IllegalArgumentException amount <= 0 或某卡规则缺失时
     */
    fun recommend(
        methods: List<PaymentMethod>,
        amount: BigDecimal,
        paymentDate: LocalDate,
        topN: Int = 3,
    ): List<Recommendation> {
        require(amount > BigDecimal.ZERO) { "支付金额必须大于 0" }

        val results = ArrayList<Recommendation>(methods.size)
        for (method in methods) {
            val afterUsed = method.currentUsed.add(amount)
            val limit = method.creditLimit
            if (limit != null && afterUsed > limit) {
                continue
            }

            val dueDate = calculateDueDate(paymentDate, method)
            val occupiedDays = maxOf(
                java.time.temporal.ChronoUnit.DAYS.between(paymentDate, dueDate).toInt(),
                0,
            )
            val reason = if (occupiedDays == 0) {
                "即时扣款，占用天数为 0 天"
            } else {
                "预计 $dueDate 还款，占用 $occupiedDays 天"
            }

            results.add(
                Recommendation(
                    paymentMethodId = method.id,
                    paymentMethod = method.name,
                    currentUsed = method.currentUsed,
                    afterUsed = afterUsed,
                    creditLimit = method.creditLimit,
                    statementDay = method.statementDay,
                    dueDate = dueDate,
                    occupiedDays = occupiedDays,
                    reason = reason,
                )
            )
        }

        return results.sortedByDescending { it.occupiedDays }.take(topN)
    }

    // -------------------- 金额工具 --------------------

    /**
     * 金额归一为两位小数。
     *
     * Python Decimal.quantize 默认使用 ROUND_HALF_EVEN（银行家舍入），
     * 这里显式指定 HALF_EVEN 以保持逐分一致：10.125 → 10.12（不是 10.13）。
     */
    fun quantize(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_EVEN)

    /** 金额格式化为两位小数字符串 */
    fun formatAmount(value: BigDecimal?): String? =
        value?.setScale(2, RoundingMode.HALF_EVEN)?.toPlainString()

    /** 可用额度 = 额度 − 已用（两位小数，可为负）；不限额返回 null */
    fun availableLimit(creditLimit: BigDecimal?, currentUsed: BigDecimal): BigDecimal? {
        if (creditLimit == null) return null
        return quantize(creditLimit.subtract(currentUsed))
    }
}
