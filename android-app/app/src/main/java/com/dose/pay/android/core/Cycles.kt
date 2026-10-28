package com.dose.pay.android.core

import androidx.compose.ui.graphics.Color
import com.dose.pay.android.data.CycleCardDto
import com.dose.pay.android.data.CycleSummaryDto

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
    val spendText: String,
    val spendCount: Int,
    val repayText: String,
    val repayCount: Int,
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
    /** 本期消费合计（分），顶部聚合环的分子 */
    val totalSpendCents: Long = 0L,
    /** 已标记「已还清」的卡本期消费合计（分），仪表盘里冻结成另一种颜色 */
    val settledSpendCents: Long = 0L,
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
        return CycleRow(
            id = card.paymentMethodId,
            name = card.paymentMethodName,
            methodType = card.methodType,
            initial = Methods.nameInitial(card.paymentMethodName),
            color = Methods.avatarColor(card.paymentMethodName),
            rangeText = Dates.shortDate(card.cycleStart) + " → " + Dates.shortDate(card.cycleEnd),
            spendText = Money.moneyCompact(card.spendAmount),
            spendCount = spendCount,
            repayText = Money.moneyCompact(card.repayAmount),
            repayCount = card.repayCount,
            dueText = dueText(card.daysToDue),
            daysToDue = card.daysToDue,
            dueChip = dueChip(card.daysToDue),
            emptyText = if (spendCount == 0) "本期还没刷" else "",
            currentUsed = card.currentUsed,
            settled = card.settled,
        )
    }

    /**
     * 汇总：合计是「各卡本期之和」。
     *
     * 各卡还款日不同，周期区间天然不一致，所以这里不能理解成
     * 某个统一日期区间内的消费总额——界面上必须说明这点。
     * [CycleAgg.totalSpendCents] 供顶部聚合环当分子，金额走分整数通道。
     */
    fun aggregate(summary: CycleSummaryDto): CycleAgg {
        val rows = summary.cards.map(::toRow)
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
            totalSpendCents = Money.toCents(summary.totalSpend) ?: 0L,
            settledSpendCents = Money.toCents(summary.settledSpend) ?: 0L,
        )
    }
}
