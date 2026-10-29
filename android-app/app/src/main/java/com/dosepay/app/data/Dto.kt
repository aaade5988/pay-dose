package com.dosepay.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 后端契约 DTO（字段名以 backend 的 Dtos.kt 为准）。
 *
 * 约定：金额一律 JSON 字符串（如 "10000.00"），日期一律 "YYYY-MM-DD"。
 */

/** 支付方式请求体（新增 / 编辑共用） */
@Serializable
data class MethodInput(
    val name: String = "",
    @SerialName("method_type") val methodType: String = "",
    /** null 或空串 = 不限额 */
    @SerialName("credit_limit") val creditLimit: String? = null,
    @SerialName("current_used") val currentUsed: String = "0",
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("due_days_after_statement") val dueDaysAfterStatement: Int? = null,
    @SerialName("statement_day_inclusive") val statementDayInclusive: Boolean = false,
)

/** 支付方式响应体 */
@Serializable
data class MethodDto(
    val id: Long,
    val name: String,
    @SerialName("method_type") val methodType: String,
    @SerialName("credit_limit") val creditLimit: String? = null,
    @SerialName("current_used") val currentUsed: String = "0.00",
    /** 可用额度 = 额度 − 已用（可为负）；不限额为 null */
    @SerialName("available_limit") val availableLimit: String? = null,
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("due_days_after_statement") val dueDaysAfterStatement: Int? = null,
    @SerialName("statement_day_inclusive") val statementDayInclusive: Boolean = false,
    val active: Boolean = true,
    /** 「已还清」人工标记是否生效中（后端在本期还款日后自动失效） */
    val settled: Boolean = false,
)

/** 推荐请求 */
@Serializable
data class RecommendRequest(
    @SerialName("payment_date") val paymentDate: String = "",
    val amount: String = "",
    @SerialName("top_n") val topN: Int? = null,
)

/** 推荐结果项 */
@Serializable
data class RecItem(
    @SerialName("payment_method_id") val paymentMethodId: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("current_used") val currentUsed: String = "0.00",
    @SerialName("after_used") val afterUsed: String = "0.00",
    @SerialName("credit_limit") val creditLimit: String? = null,
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_date") val dueDate: String = "",
    @SerialName("occupied_days") val occupiedDays: Int = 0,
    val reason: String = "",
)

/** 流水请求体 */
@Serializable
data class TxnInput(
    @SerialName("payment_method_id") val paymentMethodId: Long = 0,
    val type: String = "",
    val amount: String = "",
    @SerialName("txn_date") val txnDate: String = "",
    val note: String = "",
)

@Serializable
data class TxnDto(
    val id: Long,
    @SerialName("payment_method_id") val paymentMethodId: Long,
    @SerialName("payment_method_name") val paymentMethodName: String = "",
    val type: String = "",
    val amount: String = "0.00",
    @SerialName("txn_date") val txnDate: String = "",
    val note: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/* ---------------- 看板：账单周期聚合 ---------------- */

/**
 * 单卡当前账单周期（上一还款日 → 本还款日）。
 *
 * 口径由后端 Calculator 决定：一笔消费算哪一期，看它的还款日是哪天，不是自然月。
 */
@Serializable
data class CycleCardDto(
    @SerialName("payment_method_id") val paymentMethodId: Long,
    @SerialName("payment_method_name") val paymentMethodName: String = "",
    @SerialName("method_type") val methodType: String = "",
    val basis: String = "cycle",
    /** 上一还款日（周期起点，不含） */
    @SerialName("cycle_start") val cycleStart: String = "",
    /** 本还款日（周期终点，含） */
    @SerialName("cycle_end") val cycleEnd: String = "",
    /** 距本还款日还剩几天，今天到期 = 0 */
    @SerialName("days_to_due") val daysToDue: Int = 0,
    @SerialName("spend_amount") val spendAmount: String = "0.00",
    @SerialName("repay_amount") val repayAmount: String = "0.00",
    @SerialName("net_amount") val netAmount: String = "0.00",
    @SerialName("spend_count") val spendCount: Int = 0,
    @SerialName("repay_count") val repayCount: Int = 0,
    @SerialName("current_used") val currentUsed: String = "0.00",
    /** 该卡本期是否被人工标记为「已还清」（不计占用，仪表盘冻结成另一种颜色） */
    val settled: Boolean = false,
)

/** 看板周期汇总：各卡本期消费之和 */
@Serializable
data class CycleSummaryDto(
    @SerialName("as_of") val asOf: String = "",
    @SerialName("total_spend") val totalSpend: String = "0.00",
    @SerialName("total_repay") val totalRepay: String = "0.00",
    @SerialName("total_net") val totalNet: String = "0.00",
    /** 已被标记「已还清」的卡本期消费合计（仪表盘里冻结成另一种颜色） */
    @SerialName("settled_spend") val settledSpend: String = "0.00",
    val cards: List<CycleCardDto> = emptyList(),
)
