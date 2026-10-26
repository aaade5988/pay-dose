package com.dose.pay.android.ui.cardedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dose.pay.android.core.Methods
import com.dose.pay.android.core.Money
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.MethodInput
import com.dose.pay.android.data.PlannerRepository
import com.dose.pay.android.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 还款规则模式 */
object DueMode {
    const val FIXED = "fixed"
    const val AFTER = "after"
}

/** 表单模型 */
data class CardEditForm(
    val name: String = "",
    val typeIndex: Int = 0,
    val creditLimit: String = "",
    /** 编辑模式下为后端原值（只读，提交时原样回传，避免余额被清零） */
    val currentUsed: String = "0",
    val statementDay: Int = 1,
    val dueMode: String = DueMode.FIXED,
    val dueDay: Int = 1,
    val dueDaysAfterStatement: Int = 1,
    val statementDayInclusive: Boolean = false,
)

/** 编辑页状态 */
data class CardEditUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val methodId: Long = -1L,
    val form: CardEditForm = CardEditForm(),
    val errors: Map<String, String> = emptyMap(),
    val formError: String = "",
    val saving: Boolean = false,
) {
    val isCredit: Boolean get() = Methods.METHOD_TYPES[form.typeIndex] == Methods.CREDIT_CARD
    val methodType: String get() = Methods.METHOD_TYPES[form.typeIndex]
}

/**
 * 新增 / 编辑支付方式共用表单。
 *
 * 本页不采集「当前已用金额」：编辑时把后端原值缓存下来，PUT 时原样回传。
 */
class CardEditViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CardEditUiState())
    val state: StateFlow<CardEditUiState> = _state.asStateFlow()

    private var loaded = false

    /** 载入：methodId < 0 表示新增 */
    fun load(methodId: Long) {
        if (loaded) return
        loaded = true
        if (methodId <= 0L) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val list = repository.listMethods()
                val found = list.firstOrNull { it.id == methodId }
                if (found == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            isEdit = false,
                            methodId = -1L,
                            formError = "未找到该支付方式，已切换为新增",
                        )
                    }
                    events.show("未找到该卡片，按新增处理")
                    return@launch
                }
                val isAfter = found.dueDaysAfterStatement != null
                val typeIndex = Methods.METHOD_TYPES.indexOf(found.methodType).coerceAtLeast(0)
                val form = CardEditForm(
                    name = found.name,
                    typeIndex = typeIndex,
                    creditLimit = found.creditLimit ?: "",
                    currentUsed = found.currentUsed.ifBlank { "0" },
                    statementDay = found.statementDay ?: 1,
                    dueMode = if (isAfter) DueMode.AFTER else DueMode.FIXED,
                    dueDay = found.dueDay ?: 1,
                    dueDaysAfterStatement = found.dueDaysAfterStatement ?: 1,
                    statementDayInclusive = found.statementDayInclusive,
                )
                _state.update {
                    it.copy(loading = false, isEdit = true, methodId = found.id, form = form, formError = "")
                }
            } catch (e: ApiError) {
                _state.update { it.copy(loading = false, formError = e.message) }
            }
        }
    }

    /* ────────────── 表单交互 ────────────── */

    fun setName(value: String) {
        if (value.length > 30) return
        updateForm({ it.copy(name = value) })
        clearError("name")
    }

    fun setType(index: Int) {
        updateForm({ it.copy(typeIndex = index) })
        clearError("method_type")
        _state.update { it.copy(formError = "") }
    }

    /** 额度输入：非法格式（超过两位小数等）直接回退上一次的值 */
    fun setCreditLimit(value: String) {
        val normalized = Money.normalizeAmountInput(value)
        if (normalized == null) {
            events.show("额度最多两位小数")
            return
        }
        updateForm({ it.copy(creditLimit = normalized) })
        clearError("credit_limit")
    }

    fun setStatementDay(day: Int) {
        updateForm { it.copy(statementDay = day) }
        clearError("statement_day")
    }

    fun setDueMode(mode: String) {
        updateForm { it.copy(dueMode = mode) }
        clearError("due_day")
        clearError("due_days_after_statement")
        _state.update { it.copy(formError = "") }
    }

    fun setDueDay(day: Int) {
        updateForm { it.copy(dueDay = day) }
        clearError("due_day")
    }

    fun setDueDaysAfterStatement(days: Int) {
        updateForm { it.copy(dueDaysAfterStatement = days) }
        clearError("due_days_after_statement")
    }

    fun setStatementDayInclusive(value: Boolean) {
        updateForm { it.copy(statementDayInclusive = value) }
    }

    private fun updateForm(block: (CardEditForm) -> CardEditForm) {
        _state.update { it.copy(form = block(it.form)) }
    }

    private fun clearError(field: String) {
        _state.update { st ->
            if (!st.errors.containsKey(field)) st else st.copy(errors = st.errors - field)
        }
    }

    /* ────────────── 校验与提交 ────────────── */

    private fun validate(): Boolean {
        val form = _state.value.form
        val errors = HashMap<String, String>()

        val name = form.name.trim()
        if (name.isEmpty()) errors["name"] = "名称不能为空"
        else if (name.length > 30) errors["name"] = "名称不超过 30 个字符"

        val limitText = form.creditLimit.trim()
        if (limitText.isNotEmpty()) {
            val cents = Money.toCents(limitText)
            if (cents == null || cents < 0L) {
                errors["credit_limit"] = "额度需为不小于 0 的金额"
            } else if (cents > 99999999L * 100L) {
                errors["credit_limit"] = "额度超出合理范围（≤ 99,999,999）"
            }
        }

        if (form.typeIndex == Methods.METHOD_TYPES.indexOf(Methods.CREDIT_CARD)) {
            if (form.statementDay < 1 || form.statementDay > 31) {
                errors["statement_day"] = "账单日需在 1–31 之间"
            }
            if (form.dueMode == DueMode.FIXED) {
                if (form.dueDay < 1 || form.dueDay > 31) errors["due_day"] = "还款日需在 1–31 之间"
            } else {
                if (form.dueDaysAfterStatement < 1 || form.dueDaysAfterStatement > 60) {
                    errors["due_days_after_statement"] = "天数需在 0–60 之间"
                }
            }
        }

        _state.update { it.copy(errors = errors) }
        return errors.isEmpty()
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.saving) return
        if (!validate()) {
            events.show("请先修正表单错误")
            return
        }
        val form = _state.value.form
        val isCredit = form.typeIndex == Methods.METHOD_TYPES.indexOf(Methods.CREDIT_CARD)
        val payload = MethodInput(
            name = form.name.trim(),
            methodType = Methods.METHOD_TYPES[form.typeIndex],
            creditLimit = form.creditLimit.trim().ifEmpty { null },
            currentUsed = form.currentUsed.trim().ifEmpty { "0" },
            statementDay = if (isCredit) form.statementDay else null,
            dueDay = if (isCredit && form.dueMode == DueMode.FIXED) form.dueDay else null,
            dueDaysAfterStatement = if (isCredit && form.dueMode == DueMode.AFTER) form.dueDaysAfterStatement else null,
            statementDayInclusive = if (isCredit) form.statementDayInclusive else false,
        )
        val isEdit = _state.value.isEdit
        val methodId = _state.value.methodId

        _state.update { it.copy(saving = true, formError = "") }
        viewModelScope.launch {
            try {
                if (isEdit && methodId > 0L) repository.updateMethod(methodId, payload)
                else repository.createMethod(payload)
                _state.update { it.copy(saving = false) }
                events.show("已保存")
                onDone()
            } catch (e: ApiError) {
                val field = mapDetailToField(e.message)
                _state.update {
                    it.copy(
                        saving = false,
                        formError = e.message,
                        errors = if (field != null) it.errors + (field to e.message) else it.errors,
                    )
                }
            }
        }
    }

    /** 后端 detail 里若带字段名，映射到对应表单项 */
    private fun mapDetailToField(detail: String?): String? {
        val text = detail ?: ""
        val map = listOf(
            "name" to "name",
            "名称" to "name",
            "credit_limit" to "credit_limit",
            "额度" to "credit_limit",
            "statement_day" to "statement_day",
            "账单日" to "statement_day",
            "due_day" to "due_day",
            "还款日" to "due_day",
            "due_days_after_statement" to "due_days_after_statement",
            "method_type" to "method_type",
            "类型" to "method_type",
        )
        for ((keyword, field) in map) {
            if (text.contains(keyword)) return field
        }
        return null
    }
}
