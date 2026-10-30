package com.dosepay.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 账单分期计算。
 *
 * 两种计价口径，对应两类完全不同的产品：
 *
 * - [InstallmentRateMode.ANNUAL_RATE]（消费贷 / 分期通）：给的是**年化利率**，
 *   月利率 = 年利率 / 12，每期还款额固定、利息按剩余本金逐月递减（真等额本息）。
 * - [InstallmentRateMode.FEE_RATE]（信用卡账单分期）：给的是**总手续费率**，
 *   银行按原始全额收手续费（本金在还、手续费不减），每期还固定金额
 *   （等额本金 + 固定手续费），这里的每期金额与银行账单逐分对齐。
 *
 * 两种口径在**同一费率下每期还款总额完全一致**（IRR 正是让两者等价的那个利率），
 * 差别只在本金/利息怎么拆——这决定了额度释放节奏和提前结清的成本。
 *
 * 金额全程 BigDecimal，舍入统一 HALF_EVEN（与 [Calculator.quantize] 一致）。
 */
object Installments {

    /** 分期期数上限（信用卡常见 3~24 期） */
    const val MAX_PERIODS = 24

    /** 计价口径 */
    object RateMode {
        /** 年化利率，如 0.0516 = 5.16% */
        const val ANNUAL_RATE = "年利率"

        /** 总手续费率，如 0.072 = 7.2%（一次性按全额收取） */
        const val FEE_RATE = "总费率"

        /**
         * 总利息金额，如 36.79。
         *
         * 账单上直接印着这个数，填它最省事：每期还款额由此均分得出，
         * 再反解利率生成等额本息表，算出来的数字与账单逐分对齐。
         */
        const val TOTAL_INTEREST = "总利息"

        val ALL = listOf(ANNUAL_RATE, FEE_RATE, TOTAL_INTEREST)
    }

    /** 反解月利率时的搜索区间 */
    private val MIN_MONTH_RATE = BigDecimal("0.000001")
    private val MAX_MONTH_RATE = BigDecimal("0.05")

    /** 计算中间精度：期初本金 × 月利率 要留够位，最后再落到分 */
    private val PRECISION = 12

    /** 单期 */
    data class Period(
        /** 期号，1 起 */
        val index: Int,
        /** 本期还款日 */
        val dueDate: LocalDate,
        /** 本期还的本金 */
        val principalPart: BigDecimal,
        /** 本期的利息 / 手续费 */
        val interestPart: BigDecimal,
        /** 期初剩余本金 */
        val beginBalance: BigDecimal,
        /** 期末剩余本金 */
        val endBalance: BigDecimal,
    ) {
        /** 本期应还 = 本金 + 利息 */
        val total: BigDecimal get() = principalPart.add(interestPart)
    }

    /** 完整分期方案 */
    data class Schedule(
        val principal: BigDecimal,
        val periods: Int,
        val mode: String,
        val rate: BigDecimal,
        val items: List<Period>,
        /** 总利息 / 总手续费 */
        val totalInterest: BigDecimal,
        /** 总还款 */
        val totalPayment: BigDecimal,
        /** 折合年化利率（单利口径，IRR × 12） */
        val effectiveAnnualRate: BigDecimal,
    ) {
        /** 首期应还 */
        val firstPayment: BigDecimal get() = items.first().total
    }

    /**
     * 生成分期方案。
     *
     * @param principal 分期本金（正数）
     * @param periods 期数，1..[MAX_PERIODS]
     * @param mode [RateMode] 里的口径
     * @param rate 年化利率或总手续费率
     * @param firstDueDate 首期还款日（= 分期那一期的账单还款日）
     * @return 方案；参数非法时返回 null
     */
    fun build(
        principal: BigDecimal,
        periods: Int,
        mode: String,
        rate: BigDecimal,
        firstDueDate: LocalDate,
    ): Schedule? {
        if (principal <= BigDecimal.ZERO) return null
        if (periods !in 1..MAX_PERIODS) return null
        if (rate < BigDecimal.ZERO) return null

        val items = when (mode) {
            RateMode.ANNUAL_RATE -> annuitySchedule(principal, periods, rate, firstDueDate)
            RateMode.FEE_RATE -> feeSchedule(principal, periods, rate, firstDueDate)
            RateMode.TOTAL_INTEREST -> interestSchedule(principal, periods, rate, firstDueDate)
            else -> return null
        }

        val totalInterest = items.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.interestPart) }
        val totalPayment = items.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.total) }
        val effective = effectiveAnnualRate(principal, items, periods, mode, rate)

        return Schedule(
            principal = principal,
            periods = periods,
            mode = mode,
            rate = rate,
            items = items,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            effectiveAnnualRate = effective,
        )
    }

    /**
     * 等额本息（年利率口径）。
     *
     * 每期还款额固定 `R = P·i·(1+i)^n / ((1+i)^n − 1)`，
     * 每期利息 = 期初剩余本金 × i，逐月递减，本金逐月递增。
     * 最后一期按实际剩余本金结清，吸收逐期取整的尾差。
     */
    private fun annuitySchedule(
        principal: BigDecimal,
        periods: Int,
        annualRate: BigDecimal,
        firstDueDate: LocalDate,
    ): List<Period> {
        val i = annualRate.divide(BigDecimal(12), PRECISION, RoundingMode.HALF_EVEN)
        return annuityScheduleAt(principal, periods, i, annuityPayment(principal, i, periods), firstDueDate)
    }

    /**
     * 总利息口径：账单上直接给了「总利息」，每期还款额由它均分得出。
     *
     * 先 `R = (本金 + 总利息) / n`，再从这个 R 反解月利率生成等额本息表。
     * 不重算 R，是为了让 Σ每期 = 本金 + 总利息**严格成立**——
     * 这正是「和账单逐分对齐」的关键（用利率重算会差几分）。
     */
    private fun interestSchedule(
        principal: BigDecimal,
        periods: Int,
        totalInterest: BigDecimal,
        firstDueDate: LocalDate,
    ): List<Period> {
        val payment = principal.add(totalInterest)
            .divide(BigDecimal(periods), PRECISION, RoundingMode.HALF_EVEN)
        val i = solveMonthlyRate(principal, payment, periods) ?: BigDecimal.ZERO
        val raw = annuityScheduleAt(principal, periods, i, payment, firstDueDate)

        // 逐期取整会让 Σ利息 偏离输入的总利息（1305.97/12 期就差 4 分），
        // 末期按差额补回来——银行也是这么收的，否则总额对不上账单。
        val collected = raw.dropLast(1).fold(BigDecimal.ZERO) { acc, p -> acc.add(p.interestPart) }
        val last = raw.last()
        val fixedLast = last.copy(
            interestPart = Calculator.quantize(totalInterest.subtract(collected)),
            principalPart = last.beginBalance,
            endBalance = BigDecimal.ZERO.setScale(2),
        )
        return raw.dropLast(1) + fixedLast
    }

    /** 等额本息递推：还款额固定为 [payment]，利息按 [monthlyRate] 逐月递减 */
    private fun annuityScheduleAt(
        principal: BigDecimal,
        periods: Int,
        monthlyRate: BigDecimal,
        payment: BigDecimal,
        firstDueDate: LocalDate,
    ): List<Period> {
        val i = monthlyRate
        val out = ArrayList<Period>(periods)
        var remain = principal
        for (k in 1..periods) {
            val last = k == periods
            val interest = Calculator.quantize(remain.multiply(i))
            val principalPart = if (last) {
                // 末期结清：本金 = 剩余全部，利息 = 还款额 − 本金
                Calculator.quantize(remain)
            } else {
                Calculator.quantize(payment.subtract(interest))
            }
            val total = if (last) {
                // 末期还款额仍为 R（尾差由末期利息吸收）
                Calculator.quantize(payment)
            } else {
                Calculator.quantize(principalPart.add(interest))
            }
            val end = Calculator.quantize(remain.subtract(principalPart))
            out.add(
                Period(
                    index = k,
                    dueDate = dueDateOf(firstDueDate, k),
                    principalPart = principalPart,
                    interestPart = Calculator.quantize(total.subtract(principalPart)),
                    beginBalance = Calculator.quantize(remain),
                    endBalance = end,
                )
            )
            remain = end
        }
        return out
    }

    /**
     * 等额本金 + 固定手续费（信用卡账单分期口径）。
     *
     * 每期本金固定 `P / n`，每期手续费固定 `P × 总费率 / n`，
     * 分摊到分时末位补差，保证 Σ本金 = 本金、Σ手续费 = 总手续费。
     */
    private fun feeSchedule(
        principal: BigDecimal,
        periods: Int,
        feeRate: BigDecimal,
        firstDueDate: LocalDate,
    ): List<Period> {
        val totalFee = Calculator.quantize(principal.multiply(feeRate))
        val feeParts = splitEvenly(totalFee, periods)
        val principalParts = splitEvenly(principal, periods)

        val out = ArrayList<Period>(periods)
        var remain = principal
        for (k in 1..periods) {
            val principalPart = principalParts[k - 1]
            val end = Calculator.quantize(remain.subtract(principalPart))
            out.add(
                Period(
                    index = k,
                    dueDate = dueDateOf(firstDueDate, k),
                    principalPart = principalPart,
                    interestPart = feeParts[k - 1],
                    beginBalance = Calculator.quantize(remain),
                    endBalance = end,
                )
            )
            remain = end
        }
        return out
    }

    /**
     * 等额本息每期还款额。
     *
     * i = 0（零利率）时退化为本金均摊。
     */
    fun annuityPayment(principal: BigDecimal, monthlyRate: BigDecimal, periods: Int): BigDecimal {
        if (periods <= 0) return BigDecimal.ZERO
        if (monthlyRate <= BigDecimal.ZERO) {
            return principal.divide(BigDecimal(periods), PRECISION, RoundingMode.HALF_EVEN)
        }
        val growth = BigDecimal.ONE.add(monthlyRate).pow(periods)
        return principal
            .multiply(monthlyRate)
            .multiply(growth)
            .divide(growth.subtract(BigDecimal.ONE), PRECISION, RoundingMode.HALF_EVEN)
    }

    /**
     * 由「本金 + 每期还款额 + 期数」反解月利率（IRR）。
     *
     * 只有拿到**每期还款额**（而不是利率）时用这个：
     * f(i) = 每期还款 × 年金现值系数(i) − 本金，随 i 单调递减，二分求解。
     */
    fun solveMonthlyRate(
        principal: BigDecimal,
        perPeriod: BigDecimal,
        periods: Int,
    ): BigDecimal? {
        if (principal <= BigDecimal.ZERO || perPeriod <= BigDecimal.ZERO || periods <= 0) return null
        var lo = MIN_MONTH_RATE
        var hi = MAX_MONTH_RATE
        repeat(80) {
            val mid = lo.add(hi).divide(BigDecimal(2), PRECISION, RoundingMode.HALF_EVEN)
            val pv = perPeriod.multiply(annuityFactor(mid, periods))
            if (pv > principal) lo = mid else hi = mid
        }
        return lo.add(hi).divide(BigDecimal(2), PRECISION, RoundingMode.HALF_EVEN)
    }

    /** 年金现值系数 (1 − (1+i)^(−n)) / i */
    private fun annuityFactor(rate: BigDecimal, periods: Int): BigDecimal {
        if (rate <= BigDecimal.ZERO) return BigDecimal(periods)
        val growth = BigDecimal.ONE.add(rate).pow(periods)
        val discount = BigDecimal.ONE.divide(growth, PRECISION, RoundingMode.HALF_EVEN)
        return BigDecimal.ONE.subtract(discount).divide(rate, PRECISION, RoundingMode.HALF_EVEN)
    }

    /**
     * 折合年化（单利口径 = 月利率 × 12）。
     *
     * 年利率口径直接给原值；费率口径用每期还款额反解 IRR 再乘 12。
     */
    private fun effectiveAnnualRate(
        principal: BigDecimal,
        items: List<Period>,
        periods: Int,
        mode: String,
        rate: BigDecimal,
    ): BigDecimal {
        if (mode == RateMode.ANNUAL_RATE) return rate
        val perPeriod = items.map { it.total }.fold(BigDecimal.ZERO) { a, b -> a.add(b) }
            .divide(BigDecimal(periods), PRECISION, RoundingMode.HALF_EVEN)
        val monthly = solveMonthlyRate(principal, perPeriod, periods) ?: BigDecimal.ZERO
        return monthly.multiply(BigDecimal(12))
    }

    /**
     * 把总额均分成 n 份，末位补差。
     *
     * 保证 Σ各份 = 总额，误差不超过一分。
     */
    fun splitEvenly(amount: BigDecimal, parts: Int): List<BigDecimal> {
        if (parts <= 0) return emptyList()
        val per = amount.divide(BigDecimal(parts), 2, RoundingMode.HALF_EVEN)
        val out = ArrayList<BigDecimal>(parts)
        for (i in 0 until parts) out.add(per)
        val diff = amount.subtract(per.multiply(BigDecimal(parts)))
        if (diff != BigDecimal.ZERO) {
            out[parts - 1] = out[parts - 1].add(diff)
        }
        return out
    }

    /**
     * 第 k 期还款日 = 首期还款日 + (k−1) 个月，月末钳制。
     *
     * 首期是 1-31 时，后续月份没有 31 号就落到当月最后一天（2 月 28/29）。
     */
    fun dueDateOf(firstDueDate: LocalDate, index: Int): LocalDate {
        val ym = YearMonth.from(firstDueDate).plusMonths((index - 1).toLong())
        return ym.atDay(minOf(firstDueDate.dayOfMonth, ym.lengthOfMonth()))
    }
}
