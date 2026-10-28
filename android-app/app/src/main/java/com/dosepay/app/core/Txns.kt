package com.dosepay.app.core

import androidx.compose.ui.graphics.Color
import com.dosepay.app.data.TxnDto

/** 流水行展示模型 */
data class TxnRow(
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
    val amountText: String,
    val subText: String,
    val dayShort: String,
    val createdShort: String,
)

/** 按天分组 */
data class DayGroup(val date: String, val dayText: String, val weekday: String, val rows: MutableList<TxnRow>)

/**
 * 消费流水领域工具（移植自原小程序 utils/txn.js）。金额聚合全部走「分」整数通道。
 */
object Txns {

    const val TYPE_SPEND = "消费"
    const val TYPE_REPAY = "还款"
    val TXN_TYPES = listOf(TYPE_SPEND, TYPE_REPAY)

    /** 流水行 -> 展示模型 */
    fun toRowView(t: TxnDto): TxnRow {
        val isRepay = t.type == TYPE_REPAY
        val name = t.paymentMethodName
        return TxnRow(
            id = t.id,
            paymentMethodId = t.paymentMethodId,
            paymentMethodName = name,
            type = t.type,
            amount = t.amount,
            txnDate = t.txnDate,
            note = t.note,
            initial = Methods.nameInitial(name),
            color = Methods.avatarColor(name),
            isRepay = isRepay,
            amountText = (if (isRepay) "+" else "−") + Money.moneyCompact(t.amount),
            subText = if (t.note.isNotEmpty()) t.note else "记一笔",
            dayShort = Dates.shortDate(t.txnDate),
            createdShort = if (t.createdAt.length >= 10) t.createdAt.substring(5, 10) else "",
        )
    }

    /** 按 txn_date 分组（保持输入顺序） */
    fun groupByDay(txns: List<TxnDto>): List<DayGroup> {
        val groups = ArrayList<DayGroup>()
        val indexOf = HashMap<String, Int>()
        for (t in txns) {
            val day = t.txnDate.take(10)
            val idx = indexOf[day]
            if (idx == null) {
                indexOf[day] = groups.size
                val date = Dates.parse(day)
                groups.add(
                    DayGroup(
                        date = day,
                        dayText = Dates.shortDate(day),
                        weekday = date?.let { Dates.weekdayLabel(it) } ?: "",
                        rows = mutableListOf(),
                    )
                )
            }
            groups[indexOf[day]!!].rows.add(toRowView(t))
        }
        return groups
    }

    /** 卡名短标（字头横滑用）：取名称前 2 个字符 */
    fun shortName(name: String?): String {
        val chars = (name ?: "").trim()
        if (chars.isEmpty()) return "?"
        val cps = chars.codePoints().toArray()
        if (cps.size <= 2) return String(cps, 0, cps.size)
        return String(cps, 0, 2)
    }
}
