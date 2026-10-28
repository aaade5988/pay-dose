package com.dose.pay.api

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * API 契约 DTO。
 *
 * 字段名与取值刻意沿用原 credit-payment-planner 的契约（snake_case、中文枚举、
 * 金额为字符串），这样原 miniprogram / h5 前端可以零改动直连本服务。
 *
 * 约定：
 * - 金额一律 JSON **字符串**（如 "10000.00"），不是数字；
 * - 日期一律 ISO 字符串 "YYYY-MM-DD"；
 * - 类型不符 → 422；业务规则错误 → 400 / 404 / 409。
 */

/** 支付方式请求体（新增 / 编辑共用） */
data class MethodInput(
    val name: String = "",
    @JsonProperty("method_type")
    val methodType: String = "",
    /** null 或空串 = 不限额 */
    @JsonProperty("credit_limit")
    val creditLimit: String? = null,
    @JsonProperty("current_used")
    val currentUsed: String = "0",
    @JsonProperty("statement_day")
    @get:Min(1) @get:Max(31)
    val statementDay: Int? = null,
    @JsonProperty("due_day")
    @get:Min(1) @get:Max(31)
    val dueDay: Int? = null,
    @JsonProperty("due_days_after_statement")
    @get:Min(1) @get:Max(60)
    val dueDaysAfterStatement: Int? = null,
    @JsonProperty("statement_day_inclusive")
    val statementDayInclusive: Boolean = false,
)

/** 支付方式响应体 = 请求体 + id + active + available_limit */
data class MethodDTO(
    val id: Long,
    val name: String,
    @JsonProperty("method_type")
    val methodType: String,
    @JsonProperty("credit_limit")
    val creditLimit: String?,
    @JsonProperty("current_used")
    val currentUsed: String,
    /** 可用额度 = credit_limit − current_used（两位小数，可为负）；不限额 → null */
    @JsonProperty("available_limit")
    val availableLimit: String?,
    @JsonProperty("statement_day")
    val statementDay: Int?,
    @JsonProperty("due_day")
    val dueDay: Int?,
    @JsonProperty("due_days_after_statement")
    val dueDaysAfterStatement: Int?,
    @JsonProperty("statement_day_inclusive")
    val statementDayInclusive: Boolean,
    val active: Boolean,
    /** 「已还清」人工标记是否生效中（标记在本期还款日后自动失效） */
    val settled: Boolean,
)

/** 「标记已还清」请求体 */
data class SettleRequest(
    /** true = 标记本期已还清；false = 取消标记 */
    val settled: Boolean = true,
)

data class MethodsResponse(val methods: List<MethodDTO>)

data class MethodResponse(val method: MethodDTO)

data class OkResponse(val ok: Boolean = true)

/* ---------------- 推荐 ---------------- */

data class RecommendRequest(
    @JsonProperty("payment_date")
    val paymentDate: String = "",
    val amount: String = "",
    @JsonProperty("top_n")
    @get:Min(1) @get:Max(100)
    val topN: Int? = null,
)

data class RecItem(
    @JsonProperty("payment_method_id")
    val paymentMethodId: Long,
    @JsonProperty("payment_method")
    val paymentMethod: String,
    @JsonProperty("current_used")
    val currentUsed: String,
    @JsonProperty("after_used")
    val afterUsed: String,
    @JsonProperty("credit_limit")
    val creditLimit: String?,
    @JsonProperty("statement_day")
    val statementDay: Int?,
    @JsonProperty("due_date")
    val dueDate: String,
    @JsonProperty("occupied_days")
    val occupiedDays: Int,
    val reason: String,
)

data class RecommendResponse(val results: List<RecItem>)

/* ---------------- 消费流水 ---------------- */

data class TxnInput(
    @JsonProperty("payment_method_id")
    val paymentMethodId: Long = 0,
    val type: String = "",
    val amount: String = "",
    @JsonProperty("txn_date")
    val txnDate: String = "",
    val note: String = "",
)

data class TxnDTO(
    val id: Long,
    @JsonProperty("payment_method_id")
    val paymentMethodId: Long,
    @JsonProperty("payment_method_name")
    val paymentMethodName: String,
    val type: String,
    val amount: String,
    @JsonProperty("txn_date")
    val txnDate: String,
    val note: String,
    @JsonProperty("created_at")
    val createdAt: String,
)

data class TxnCreateResponse(val transaction: TxnDTO)

data class TxnsResponse(val transactions: List<TxnDTO>)

/* ---------------- 看板：账单周期聚合 ---------------- */

/**
 * 单卡当前账单周期聚合。
 *
 * 周期口径 = (上一还款日, 本还款日]，不是自然月。
 * 只包含信用卡：储蓄卡 / 电子钱包无账单周期，不进看板。
 */
data class CycleCardDTO(
    @JsonProperty("payment_method_id")
    val paymentMethodId: Long,
    @JsonProperty("payment_method_name")
    val paymentMethodName: String,
    @JsonProperty("method_type")
    val methodType: String,
    /** 口径标记，当前恒为 `cycle`；保留字段以便将来扩展 */
    val basis: String,
    /** 上一还款日（周期起点，不含） */
    @JsonProperty("cycle_start")
    val cycleStart: String,
    /** 本还款日（周期终点，含） */
    @JsonProperty("cycle_end")
    val cycleEnd: String,
    /** 距本还款日还剩几天（今天到期 = 0） */
    @JsonProperty("days_to_due")
    val daysToDue: Int,
    @JsonProperty("spend_amount")
    val spendAmount: String,
    @JsonProperty("repay_amount")
    val repayAmount: String,
    /** 本期净占用 = 消费 − 还款 */
    @JsonProperty("net_amount")
    val netAmount: String,
    @JsonProperty("spend_count")
    val spendCount: Int,
    @JsonProperty("repay_count")
    val repayCount: Int,
    @JsonProperty("current_used")
    val currentUsed: String,
    /** 该卡本期是否被人工标记为「已还清」（标记不计占用，仪表盘冻结成另一种颜色） */
    val settled: Boolean,
)

/** 看板周期汇总：各卡本期消费之和（各卡周期不同，但都是"当前这一期"） */
data class CycleSummaryDTO(
    @JsonProperty("as_of")
    val asOf: String,
    @JsonProperty("total_spend")
    val totalSpend: String,
    @JsonProperty("total_repay")
    val totalRepay: String,
    @JsonProperty("total_net")
    val totalNet: String,
    /** 已被标记「已还清」的卡本期消费合计（这部分在仪表盘里冻结，不计入占用） */
    @JsonProperty("settled_spend")
    val settledSpend: String,
    val cards: List<CycleCardDTO>,
)

/** 删除流水响应：删除后该卡已用金额 */
data class TxnDeleteResponse(
    val ok: Boolean = true,
    @JsonProperty("card_current_used")
    val cardCurrentUsed: String,
)

/* ---------------- 错误响应 ---------------- */

/** 业务错误：`{"detail": "中文错误信息"}` */
data class ErrorResponse(val detail: String)
