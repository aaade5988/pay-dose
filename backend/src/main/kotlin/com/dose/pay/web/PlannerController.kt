package com.dose.pay.web

import com.dose.pay.api.ApiException
import com.dose.pay.api.ErrorResponse
import com.dose.pay.api.MethodInput
import com.dose.pay.api.OkResponse
import com.dose.pay.api.PlannerService
import com.dose.pay.api.RecommendRequest
import com.dose.pay.api.TxnInput
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MethodArgumentNotValidException

/**
 * 全部 REST 路由。
 *
 * | 方法 | 路径 | 说明 |
 * | GET    | /api/health | {"status":"ok"} |
 * | GET    | /api/methods | {methods:[MethodDTO]} |
 * | POST   | /api/methods | 201 {method} |
 * | PUT    | /api/methods/{id} | {method} |
 * | DELETE | /api/methods/{id} | {ok:true} |
 * | POST   | /api/recommend | {results:[RecItem]} |
 * | POST   | /api/transactions | 201 {transaction} |
 * | GET    | /api/transactions | {transactions:[TxnDTO]} |
 * | DELETE | /api/transactions/{id} | {ok, card_current_used} |
 *
 * 单用户应用，无认证。
 */
@RestController
@RequestMapping("/api")
class PlannerController(private val plannerService: PlannerService) {

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "ok")

    // -------------------- 支付方式 --------------------

    @GetMapping("/methods")
    fun getMethods(): Any = mapOf("methods" to plannerService.listMethods())

    @PostMapping("/methods")
    fun createMethod(@Valid @RequestBody body: MethodInput): ResponseEntity<Any> {
        val created = plannerService.createMethod(body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("method" to created))
    }

    @PutMapping("/methods/{id}")
    fun updateMethod(@PathVariable id: Long, @Valid @RequestBody body: MethodInput): Any =
        mapOf("method" to plannerService.updateMethod(id, body))

    @DeleteMapping("/methods/{id}")
    fun deleteMethod(@PathVariable id: Long): OkResponse {
        plannerService.deleteMethod(id)
        return OkResponse(ok = true)
    }

    // -------------------- 推荐 --------------------

    @PostMapping("/recommend")
    fun recommend(@Valid @RequestBody body: RecommendRequest): Any = plannerService.recommend(body)

    // -------------------- 消费流水 --------------------

    @PostMapping("/transactions")
    fun createTransaction(@Valid @RequestBody body: TxnInput): ResponseEntity<Any> {
        val txn = plannerService.createTransaction(body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("transaction" to txn))
    }

    @GetMapping("/transactions")
    fun getTransactions(
        @RequestParam(value = "method_id", required = false) methodId: Long?,
        @RequestParam(value = "limit", required = false, defaultValue = "50") limit: Int,
        @RequestParam(value = "offset", required = false, defaultValue = "0") offset: Int,
    ): Any = plannerService.listTransactions(methodId, limit, offset)

    @DeleteMapping("/transactions/{id}")
    fun deleteTransaction(@PathVariable id: Long): Any = plannerService.deleteTransaction(id)
}

/**
 * 全局异常处理器：把各类异常统一映射成契约约定的错误响应。
 *
 * 注意必须是 @RestControllerAdvice——用 @RestController 只会注册路由，不会拦截异常。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** 业务异常：400 / 404 / 409 / 422 */
    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<Any> {
        val body: Any = if (ex.status == 422) {
            mapOf("detail" to ex.message, "errors" to ex.errors)
        } else {
            ErrorResponse(detail = ex.message)
        }
        return ResponseEntity.status(ex.status).body(body)
    }

    /** 字段越界/缺失（@Min/@Max/@Size 触发）→ 422，文案对齐 pydantic */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Any> {
        val fieldErrors = ex.bindingResult.fieldErrors
        val details = fieldErrors.map { field ->
            val name = toSnakeCase(field.field)
            val label = FIELD_LABELS[field.field] ?: FIELD_LABELS[name] ?: name
            "$label：${translateMessage(field.code, field.defaultMessage)}"
        }
        return ResponseEntity.status(422).body(
            mapOf(
                "detail" to ("请求参数格式错误：" + details.joinToString("；")),
                "errors" to fieldErrors.map { field ->
                    val name = toSnakeCase(field.field)
                    linkedMapOf<String, Any?>(
                        "input" to field.rejectedValue,
                        "loc" to listOf("body", name),
                        "msg" to (field.defaultMessage ?: "invalid"),
                        "type" to when (field.code) {
                            "Max" -> "less_than_equal"
                            "Min" -> "greater_than_equal"
                            "Size" -> "string_too_short"
                            "NotNull", "NotBlank", "NotEmpty" -> "missing"
                            else -> "value_error"
                        },
                    )
                },
            )
        )
    }

    /**
     * 请求体无法解析（如金额传成数字而非字符串）→ 422。
     *
     * 原 pydantic 对 `amount: str` 收到数字会报 string_type；
     * Jackson 默认却会把数字宽容转成字符串，故由 StrictStringDeserializer 显式拒绝，
     * 并把原始字面量编进消息，便于还原 errors[].input。
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<Any> {
        val message = ex.cause?.message ?: ex.message ?: ""
        val isStringMismatch = message.contains("Input should be a valid string")
        val path = extractFieldPath(ex.cause) ?: extractFieldPath(ex)

        if (path != null && isStringMismatch) {
            val label = FIELD_LABELS[path] ?: path
            return ResponseEntity.status(422).body(
                mapOf(
                    "detail" to "请求参数格式错误：$label：必须是字符串（金额请带引号，如 \"10000.00\"）",
                    "errors" to listOf(
                        linkedMapOf<String, Any?>(
                            "input" to extractRawInput(ex.cause),
                            "loc" to listOf("body", path),
                            "msg" to "Input should be a valid string",
                            "type" to "string_type",
                        )
                    ),
                )
            )
        }
        val detail = if (path != null) {
            val label = FIELD_LABELS[path] ?: path
            "请求参数格式错误：$label：格式不正确"
        } else {
            "请求参数格式错误：请求体格式不正确"
        }
        return ResponseEntity.status(422).body(mapOf("detail" to detail, "errors" to emptyList<Any>()))
    }

    /** 查询参数类型不符 → 422 */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Any> {
        val label = FIELD_LABELS[ex.name] ?: ex.name
        return ResponseEntity.status(422).body(
            mapOf(
                "detail" to "请求参数格式错误：$label：必须是整数",
                "errors" to emptyList<Any>(),
            )
        )
    }

    /** 缺少必填查询参数 → 422 */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<Any> {
        val label = FIELD_LABELS[ex.parameterName] ?: ex.parameterName
        return ResponseEntity.status(422).body(
            mapOf(
                "detail" to "请求参数格式错误：$label：缺少必填字段",
                "errors" to emptyList<Any>(),
            )
        )
    }

    /** camelCase → snake_case（topN → top_n） */
    private fun toSnakeCase(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    private fun translateMessage(code: String?, message: String?): String = when (code) {
        "NotNull", "NotBlank", "NotEmpty" -> "缺少必填字段"
        "Min", "Max" -> {
            val limit = Regex("""(\d+)\s*$""").find(message ?: "")?.groupValues?.get(1)
            if (limit != null) "取值超出允许范围（边界 $limit）" else "取值超出允许范围"
        }
        "Size" -> "长度超出允许范围"
        else -> message ?: "格式不正确"
    }

    /** 从 Jackson 异常的 pathReference 提取字段名 */
    private fun extractFieldPath(error: Throwable?): String? {
        var current: Throwable? = error
        while (current != null) {
            if (current is com.fasterxml.jackson.databind.exc.MismatchedInputException) {
                val field = current.path?.lastOrNull()?.fieldName
                if (!field.isNullOrBlank()) return field
            }
            if (current is com.fasterxml.jackson.databind.JsonMappingException) {
                val ref = current.pathReference ?: current.path?.lastOrNull()?.fieldName
                if (!ref.isNullOrBlank()) {
                    val match = Regex("""\["([^"]+)"\]""").find(ref)
                    return match?.groupValues?.get(1) ?: ref
                }
            }
            current = current.cause
        }
        return null
    }

    /** 取出被拒绝的原始输入值（还原 pydantic 的 errors[].input） */
    private fun extractRawInput(error: Throwable?): Any? {
        var current: Throwable? = error
        while (current != null) {
            val match = Regex("""RAW_INPUT=([^;]*);""").find(current.message ?: "")
            if (match != null) {
                val raw = match.groupValues[1].trim()
                if (raw.isEmpty() || raw == "null") return null
                raw.toLongOrNull()?.let { return it }
                raw.toDoubleOrNull()?.let { return it }
                return raw
            }
            current = current.cause
        }
        return null
    }

    companion object {
        /** 请求体字段中文标签 */
        val FIELD_LABELS: Map<String, String> = mapOf(
            "name" to "名称",
            "method_type" to "类型",
            "methodType" to "类型",
            "credit_limit" to "信用额度",
            "creditLimit" to "信用额度",
            "current_used" to "当前使用金额",
            "currentUsed" to "当前使用金额",
            "statement_day" to "账单日",
            "statementDay" to "账单日",
            "due_day" to "还款日",
            "dueDay" to "还款日",
            "due_days_after_statement" to "账单日后还款天数",
            "dueDaysAfterStatement" to "账单日后还款天数",
            "statement_day_inclusive" to "账单日当天计入本期",
            "payment_date" to "支付日期",
            "paymentDate" to "支付日期",
            "amount" to "支付金额",
            "top_n" to "返回条数",
            "topN" to "返回条数",
            "limit" to "limit",
            "offset" to "offset",
            "id" to "id",
            "payment_method_id" to "支付方式",
            "method_id" to "method_id",
            "type" to "类型",
            "txn_date" to "交易日期",
            "txnDate" to "交易日期",
            "note" to "备注",
        )
    }
}
