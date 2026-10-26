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

/** 删除流水响应：删除后该卡已用金额 */
data class TxnDeleteResponse(
    val ok: Boolean = true,
    @JsonProperty("card_current_used")
    val cardCurrentUsed: String,
)

/* ---------------- 错误响应 ---------------- */

/** 业务错误：`{"detail": "中文错误信息"}` */
data class ErrorResponse(val detail: String)
