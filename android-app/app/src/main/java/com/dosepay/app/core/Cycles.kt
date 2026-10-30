package com.dosepay.app.core

import androidx.compose.ui.graphics.Color
import com.dosepay.app.data.CycleCardDto
import com.dosepay.app.data.CycleSummaryDto
import com.dosepay.app.data.InstallmentDto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 账单分期的展示模型。
 *
 * 只带界面要用的文案，算法结果（每期拆分明细）在 [Installments] 里算好了。
 */
data class InstallmentRow(
    /** 期数 */
    val periods: Int,
    /** 分期本金 */
    val principalText: String,
    /** 每期还款额 */
    val perPeriodText: String,
    /** 每期利息 / 手续费 */
    val interestPerPeriodText: String,
    /** 总利息 / 总手续费 */
    val totalInterestText: String,
    /** 计价口径：年利率 / 总费率 */
    val modeText: String,
    /** 原始费率，如 5.16% */
    val rateText: String,
    /** 折合年化，如 13.06%——把「每期 0.6%」翻译成真实利率 */
    val annualRateText: String,
    /** 首期还款日 */
    val firstDueText: String,
    /** 末期还款日 */
    val lastDueText: String,
    /** 挪到未来各期的本金（已从本期待还里冻结） */
    val frozenText: String,
)

/**
 * 单卡账单周期展示模型。
 *
 * 口径由后端给出：区间 = (上一还款日, 本还款日]，一笔消费算哪一期看它的还款日。
 * 客户端只做展示格式化，不重算归期逻辑，避免和后端算法漂移。
 */
data class CycleRow(
    val id: Long,
    val name: String,
    val methodType: String = "",
    val initial: String,
    val color: Color,
    /** 周期区间文案：10-21 → 11-21 */
    val rangeText: String,
    /** 本期还款日（ISO "YYYY-MM-DD"）：分期计划靠它定位是哪一期账单 */
    val cycleEnd: String = "",
    val spendText: String,
    val spendCount: Int,
    val repayText: String,
    val repayCount: Int,
    /** 本期还款流水合计（分）：单卡仪表盘绿色段（已还）的来源 */
    val repayCents: Long = 0L,
    /**
     * 本期待还（**分期后**口径），列表右侧展示的就是它。
     *
     * 没有分期时 = 本期消费 − 本期还款；有分期时扣掉冻结到未来各期的本金，
     * 再加上分期首期的手续费（分期新增的成本，这个月确实要付）。
     */
    val netText: String,
    /** 原始净额（分）：消费 − 还款，**不做分期调整**，仪表盘按这把尺子算额度占用 */
    val netCents: Long,
    /** 本期待还（分）：分期后的口径，等于 [netText] 对应的金额 */
    val dueCents: Long = 0L,
    /** 距还款日文案 */
    val dueText: String,
    /** 还剩天数，用于紧急度着色 */
    val daysToDue: Int,
    val dueChip: ChipStyle,
    /** 本期没消费时的提示 */
    val emptyText: String,
    /** 当前已用余额（全周期累积，仅「更新已用」弹层用） */
    val currentUsed: String = "0.00",
    /**
     * 本期是否被人工标记为「已还清」。
     *
     * 标记 = 这笔本期消费已经还了：看板这一行显示「已还清」而不是金额，
     * 它在仪表盘里占的那一段被冻结成另一种颜色，不再计入额度占用。
     */
    val settled: Boolean = false,
    /** 本期的账单分期（没有则 null） */
    val installment: InstallmentRow? = null,
    /** 本期被分期冻结走的本金（分）：仪表盘里也冻结成绿色 */
    val frozenCents: Long = 0L,
)

/** 看板周期汇总（含多卡合计） */
data class CycleAgg(
    val rows: List<CycleRow>,
    val totalSpendText: String,
    val totalRepayText: String,
    val totalSpendCount: Int,
    val asOfText: String,
    /** 口径说明，界面上必须显式标注 */
    val basisText: String,
    /** 本期待还合计 = 消费 − 还款（分），顶部聚合环紫色段的分子 */
    val totalNetCents: Long = 0L,
    /** 已标记「已还清」的卡本期待还合计（分），仪表盘里冻结成绿色 */
    val settledNetCents: Long = 0L,
    /**
     * 被分期冻结走的本金合计（分）。
     *
     * 分期把钱挪到了未来各期，本期不再是要还的压力，所以也冻结成绿色——
     * 但钱**仍然欠着**（额度照占），所以它和「已还清」在语义上不是一回事。
     */
    val installmentFrozenCents: Long = 0L,
    /** 本期实际还款流水合计（分），与已还清标记一起构成仪表盘绿色段 */
    val totalRepayCents: Long = 0L,
)

/**
 * 账单周期展示工具。
 *
 * 金额聚合走「分」整数通道，与项目其他部分保持一致。
 */
object Cycles {

    /** 距还款日 ≤ 3 天视为吃紧 */
    private const val URGENT_DAYS = 3

    /** 距还款日 ≤ 7 天视为偏高 */
    private const val WARN_DAYS = 7

    fun dueText(days: Int): String = when {
        days < 0 -> "已过 $(-days) 天"
        days == 0 -> "今天到期"
        days == 1 -> "明天到期"
        else -> "还剩 $days 天"
    }

    fun dueChip(days: Int): ChipStyle = when {
        days <= URGENT_DAYS -> ChipStyle.RED
        days <= WARN_DAYS -> ChipStyle.ACCENT
        else -> ChipStyle.GRAY
    }

    fun toRow(card: CycleCardDto): CycleRow {
        val spendCount = card.spendCount
        // 原始净额 = 本期消费 − 本期还款；还款超过本期消费时按 0 显示，避免出现负的「待还」
        val netCents = (Money.toCents(card.netAmount) ?: 0L).coerceAtLeast(0L)
        // 本期待还：分期后口径（扣掉冻结到未来的本金，补回首期手续费）
        val dueCents = (Money.toCents(card.dueAmount) ?: netCents).coerceAtLeast(0L)
        val installment = card.installment?.let { toInstallmentRow(it) }
        val frozenCents = (Money.toCents(card.installment?.frozenAmount) ?: 0L).coerceAtLeast(0L)
        return CycleRow(
            id = card.paymentMethodId,
            name = card.paymentMethodName,
            methodType = card.methodType,
            initial = Methods.nameInitial(card.paymentMethodName),
            color = Methods.avatarColor(card.paymentMethodName),
            rangeText = Dates.shortDate(card.cycleStart) + " → " + Dates.shortDate(card.cycleEnd),
            cycleEnd = card.cycleEnd,
            spendText = Money.moneyCompact(card.spendAmount),
            spendCount = spendCount,
            repayText = Money.moneyCompact(card.repayAmount),
            repayCount = card.repayCount,
            repayCents = Money.toCents(card.repayAmount) ?: 0L,
            netText = Money.moneyCompact(Money.fromCents(dueCents)),
            netCents = netCents,
            dueCents = dueCents,
            dueText = dueText(card.daysToDue),
            daysToDue = card.daysToDue,
            dueChip = dueChip(card.daysToDue),
            emptyText = if (spendCount == 0) "本期还没刷" else "",
            currentUsed = card.currentUsed,
            settled = card.settled,
            installment = installment,
            frozenCents = frozenCents,
        )
    }

    /** 分期 DTO → 展示模型 */
    private fun toInstallmentRow(d: InstallmentDto): InstallmentRow = InstallmentRow(
        periods = d.periods,
        principalText = Money.moneyCompact(d.principal),
        perPeriodText = Money.moneyCompact(d.perPeriod),
        interestPerPeriodText = Money.moneyCompact(d.interestPerPeriod),
        totalInterestText = Money.moneyCompact(d.totalInterest),
        modeText = d.rateMode,
        rateText = percentText(d.rate),
        annualRateText = percentText(d.effectiveAnnualRate),
        firstDueText = Dates.shortDate(d.firstDueDate),
        lastDueText = Dates.shortDate(d.lastDueDate),
        frozenText = Money.moneyCompact(d.frozenAmount),
    )

    /** 小数比率 → 百分比文案：0.0516 → "5.16%" */
    private fun percentText(value: String?): String {
        val percent = (value?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_EVEN)
        return percent.toPlainString() + "%"
    }

    /**
     * 汇总：合计是「各卡本期之和」。
     *
     * 各卡还款日不同，周期区间天然不一致，所以这里不能理解成
     * 某个统一日期区间内的消费总额——界面上必须说明这点。
     * [CycleAgg.totalNetCents]（消费 − 还款）供顶部聚合环当分子，金额走分整数通道。
     */
    fun aggregate(summary: CycleSummaryDto): CycleAgg {
        val rows = summary.cards.map(::toRow)
        val totalNetCents = (Money.toCents(summary.totalNet) ?: 0L).coerceAtLeast(0L)
        // 已还清卡的净额也要从占用里剔除，否则「已还清」只是标记、数字不动
        val settledNetCents = rows.filter { it.settled }.sumOf { it.netCents }
        return CycleAgg(
            rows = rows,
            totalSpendText = Money.moneyCompact(summary.totalSpend),
            totalRepayText = Money.moneyCompact(summary.totalRepay),
            totalSpendCount = summary.cards.sumOf { it.spendCount },
            asOfText = Dates.shortDate(summary.asOf),
            basisText = if (rows.isEmpty()) {
                "还没有信用卡 · 添加卡片后按还款日周期统计"
            } else {
                "各卡按自己的还款周期划分，合计为 " + rows.size + " 张卡本期之和"
            },
            totalNetCents = totalNetCents,
            settledNetCents = settledNetCents,
            installmentFrozenCents = rows.sumOf { it.frozenCents },
            totalRepayCents = (Money.toCents(summary.totalRepay) ?: 0L).coerceAtLeast(0L),
        )
    }
}
