package com.dose.pay.android.core

import androidx.compose.ui.graphics.Color
import com.dose.pay.android.data.TxnDto

/**
 * 账单按月聚合模型（账单 Tab 专用）。
 *
 * 与 [Txns.groupByDay] 的区别：这里按**自然月**分桶且整体倒序，
 * 便于「一月一张卡」地回顾当月全部消费。
 */

/** 账单明细行 */
data class BillRow(
    val id: Long,
    val paymentMethodId: Long,
    val paymentMethodName: String,
    val type: String,
    val amount: String,
    val txnDate: String,
    val note: String,
    val initial: String,
    val color: Color,
    val isRepay: Boolean,
    /** 消费 "−¥12.50" / 还款 "+¥12.50" */
    val amountText: String,
    /** 备注；为空时回落成类型文案 */
    val noteText: String,
    /** "08-03 周一" */
    val dateText: String,
)

/** 一个自然月的账单 */
data class BillMonth(
    /** "YYYY-MM" */
    val monthKey: String,
    /** "2026年08月" */
    val title: String,
    /** 该月消费合计（两位小数字符串） */
    val spend: String,
    /** "¥1,288" */
    val spendText: String,
    /** 该月消费笔数 */
    val spendCount: Int,
    /** 该月还款合计（两位小数字符串，仅作附带信息） */
    val repay: String,
    val repayCount: Int,
    /** 该月全部明细（消费 + 还款，日期倒序） */
    val rows: List<BillRow>,
) {
    val totalCount: Int get() = rows.size
    /** 月卡副标：「消费 ¥1,288 · 4 笔」 */
    val summaryText: String get() = "消费 " + spendText + " · " + spendCount + " 笔"
}

/**
 * 账单工具：按自然月倒序聚合 + 月内汇总。金额聚合全程走「分」整数通道。
 */
object Bills {

    /** "YYYY-MM-DD" -> "YYYY-MM" */
    fun monthKeyOf(dateStr: String?): String = (dateStr ?: "").take(7)

    /** "2026-08" -> "2026年08月"；非法原样返回 */
    fun monthTitle(monthKey: String): String {
        if (monthKey.length < 7) return monthKey
        val year = monthKey.substring(0, 4)
        val month = monthKey.substring(5, 7)
        return year + "年" + month + "月"
    }

    /** 流水 -> 明细行 */
    fun toRow(t: TxnDto): BillRow {
        val isRepay = t.type == Txns.TYPE_REPAY
        val name = t.paymentMethodName
        val date = Dates.parse(t.txnDate)
        val dateText = if (date != null) {
            Dates.shortDate(t.txnDate) + " " + Dates.weekdayLabel(date)
        } else {
            t.txnDate
        }
        return BillRow(
            id = t.id,
            paymentMethodId = t.paymentMethodId,
            paymentMethodName = if (name.isBlank()) "已删除的卡" else name,
            type = t.type,
            amount = t.amount,
            txnDate = t.txnDate,
            note = t.note,
            initial = Methods.nameInitial(name),
            color = Methods.avatarColor(name),
            isRepay = isRepay,
            amountText = (if (isRepay) "+" else "−") + Money.moneyCompact(t.amount),
            noteText = if (t.note.isNotBlank()) t.note else t.type.ifBlank { "未填写备注" },
            dateText = dateText,
        )
    }

    /**
     * 按月聚合：月 key 倒序（最新月在最上），月内按 txn_date DESC, id DESC。
     *
     * 输入不要求已排序，这里统一排一遍，避免依赖后端返回顺序。
     */
    fun groupByMonth(txns: List<TxnDto>): List<BillMonth> {
        val sorted = txns.sortedWith(
            compareByDescending<TxnDto> { it.txnDate }.thenByDescending { it.id }
        )
        val buckets = LinkedHashMap<String, MutableList<TxnDto>>()
        for (t in sorted) {
            val key = monthKeyOf(t.txnDate)
            buckets.getOrPut(key) { ArrayList() }.add(t)
        }

        val months = ArrayList<BillMonth>(buckets.size)
        for ((key, list) in buckets) {
            var spendC = 0L
            var repayC = 0L
            var spendCount = 0
            var repayCount = 0
            for (t in list) {
                val cents = Money.toCents(t.amount) ?: 0L
                if (t.type == Txns.TYPE_REPAY) {
                    repayC += cents
                    repayCount++
                } else {
                    spendC += cents
                    spendCount++
                }
            }
            val spend = Money.fromCents(spendC)
            months.add(
                BillMonth(
                    monthKey = key,
                    title = monthTitle(key),
                    spend = spend,
                    spendText = Money.moneyCompact(spend),
                    spendCount = spendCount,
                    repay = Money.fromCents(repayC),
                    repayCount = repayCount,
                    rows = list.map { toRow(it) },
                )
            )
        }
        return months
    }

    /** 顶部总览：全部月份的消费合计 / 还款合计 / 涉及月份数 / 流水笔数 */
    fun overview(txns: List<TxnDto>): BillOverview {
        var spendC = 0L
        var repayC = 0L
        val months = HashSet<String>(8)
        for (t in txns) {
            val cents = Money.toCents(t.amount) ?: 0L
            if (t.type == Txns.TYPE_REPAY) repayC += cents else spendC += cents
            months.add(monthKeyOf(t.txnDate))
        }
        return BillOverview(
            totalSpend = Money.fromCents(spendC),
            totalSpendText = Money.moneyCompact(Money.fromCents(spendC)),
            totalRepay = Money.fromCents(repayC),
            totalRepayText = Money.moneyCompact(Money.fromCents(repayC)),
            monthCount = months.size,
            txnCount = txns.size,
        )
    }

    /** 顶部总览模型 */
    data class BillOverview(
        val totalSpend: String,
        val totalSpendText: String,
        val totalRepay: String,
        val totalRepayText: String,
        val monthCount: Int,
        val txnCount: Int,
    )
}
