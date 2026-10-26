package com.dose.pay.android.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dose.pay.android.core.AggUsage
import com.dose.pay.android.core.DashRow
import com.dose.pay.android.core.Dates
import com.dose.pay.android.core.Methods
import com.dose.pay.android.core.Money
import com.dose.pay.android.core.Txns
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.MethodDto
import com.dose.pay.android.data.PlannerRepository
import com.dose.pay.android.data.TxnDto
import com.dose.pay.android.data.TxnInput
import com.dose.pay.android.data.UiEventBus
import com.dose.pay.android.ui.theme.PayColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 「更新已用」半屏弹层状态 */
data class UpdateSheetState(
    val methodId: Long,
    val name: String,
    val methodType: String,
    val initial: String,
    val color: Color,
    val hasLimit: Boolean,
    val limitText: String,
    val usedText: String,
    val availText: String,
    val currentUsed: String,
    val input: String = "",
    val pct: Double? = null,
    val arc: Float = 0f,
    val ringColor: Color = PayColors.RingTrack,
    val pctText: String = "—",
    val overText: String = "",
    val error: String = "",
    val saving: Boolean = false,
)

/** 看板页状态 */
data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String = "",
    val rows: List<DashRow> = emptyList(),
    val agg: AggUsage = Methods.aggregateUsage(emptyList()),
    val methodList: List<MethodDto> = emptyList(),
    val sheet: UpdateSheetState? = null,
)

/**
 * 额度看板页（Tab3）ViewModel：聚合仪表盘 + 单卡缺口环 + 「更新已用」半屏。
 */
class DashboardViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var rawMethods: List<MethodDto> = emptyList()

    fun load() {
        _state.update { it.copy(loading = it.rows.isEmpty(), refreshing = true, errorMessage = "") }
        viewModelScope.launch {
            try {
                val list = repository.listMethods()
                rawMethods = list
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "",
                        rows = list.map(Methods::toDashRow),
                        agg = Methods.aggregateUsage(list),
                        methodList = list,
                    )
                }
            } catch (e: ApiError) {
                _state.update { it.copy(loading = false, refreshing = false, errorMessage = e.message) }
            }
        }
    }

    private fun rawOf(id: Long): MethodDto? = rawMethods.firstOrNull { it.id == id }

    /* ────────────── 更新已用半屏 ────────────── */

    fun openSheet(id: Long) {
        val raw = rawOf(id) ?: return
        val row = Methods.toDashRow(raw)
        _state.update {
            it.copy(
                sheet = UpdateSheetState(
                    methodId = raw.id,
                    name = raw.name,
                    methodType = raw.methodType,
                    initial = row.initial,
                    color = row.color,
                    hasLimit = row.hasLimit,
                    limitText = row.limitText,
                    usedText = row.usedText,
                    availText = row.availText,
                    currentUsed = row.currentUsed,
                    input = if (row.currentUsed == "0") "0" else row.currentUsed,
                )
            )
        }
        recomputeSheet()
    }

    fun closeSheet() {
        _state.update { it.copy(sheet = null) }
    }

    /** 逐键清洗金额 */
    fun onSheetInput(raw: String) {
        val fixed = Money.sanitizeAmountInput(raw)
        _state.update { st ->
            val sheet = st.sheet ?: return@update st
            st.copy(sheet = sheet.copy(input = fixed, error = ""))
        }
        recomputeSheet()
    }

    /** 输入值 -> 迷你环实时预览 + 超限告警（分整数运算） */
    private fun recomputeSheet() {
        val sheet = _state.value.sheet ?: return
        val raw = rawOf(sheet.methodId) ?: return
        val limitC = if (Methods.limitSet(raw)) Money.toCents(raw.creditLimit) else null
        val inputC = Money.toCents(sheet.input) ?: 0L
        if (limitC == null || limitC <= 0L) {
            _state.update {
                it.copy(
                    sheet = sheet.copy(
                        pct = null,
                        arc = 0f,
                        ringColor = PayColors.RingTrack,
                        pctText = "—",
                        overText = "",
                    )
                )
            }
            return
        }
        val pct = Math.round(inputC * 1000.0 / limitC) / 10.0
        val status = Methods.usageStatus(pct)
        _state.update {
            it.copy(
                sheet = sheet.copy(
                    pct = pct,
                    arc = Methods.ringArc(pct),
                    ringColor = status.color,
                    pctText = Math.round(pct).toInt().toString() + "%",
                    overText = if (inputC > limitC) {
                        "超额度 " + Money.moneyCompact(Money.fromCents(inputC - limitC))
                    } else {
                        ""
                    },
                )
            )
        }
    }

    /** 保存：PUT 全量回传原字段 + 新 current_used（超限允许） */
    fun saveSheet() {
        val sheet = _state.value.sheet ?: return
        if (sheet.saving) return
        val raw = rawOf(sheet.methodId)
        if (raw == null) {
            _state.update { it.copy(sheet = sheet.copy(error = "卡片已被删除，请下拉刷新")) }
            return
        }
        var used = Money.finalizeAmountInput(sheet.input)
        if (used.isEmpty()) used = "0"
        if (used == raw.currentUsed || Money.toCents(used) == Money.toCents(raw.currentUsed)) {
            events.show("数值未变化")
            closeSheet()
            return
        }
        val payload = com.dose.pay.android.data.MethodInput(
            name = raw.name,
            methodType = raw.methodType,
            creditLimit = raw.creditLimit,
            currentUsed = used,
            statementDay = raw.statementDay,
            dueDay = raw.dueDay,
            dueDaysAfterStatement = raw.dueDaysAfterStatement,
            statementDayInclusive = raw.statementDayInclusive,
        )
        _state.update { it.copy(sheet = sheet.copy(saving = true, error = "")) }
        viewModelScope.launch {
            try {
                repository.updateMethod(raw.id, payload)
                _state.update { it.copy(sheet = sheet.copy(saving = false)) }
                closeSheet()
                events.show("已更新 · 环形重算")
                load()
            } catch (e: ApiError) {
                _state.update { it.copy(sheet = sheet.copy(saving = false, error = e.message)) }
            }
        }
    }

    /** 「按账单清零 / 记为已还清」：POST /api/transactions 还款，金额 = 当前已用 */
    fun repayAll(note: String) {
        val sheet = _state.value.sheet ?: return
        if (sheet.saving) return
        val raw = rawOf(sheet.methodId) ?: return
        val usedC = Money.toCents(raw.currentUsed) ?: 0L
        if (usedC <= 0L) {
            events.show("当前无占用 · 无需还款")
            return
        }
        _state.update { it.copy(sheet = sheet.copy(saving = true, error = "")) }
        viewModelScope.launch {
            try {
                repository.createTransaction(
                    TxnInput(
                        paymentMethodId = raw.id,
                        type = Txns.TYPE_REPAY,
                        amount = Money.fromCents(usedC),
                        txnDate = Dates.toStr(Dates.today()),
                        note = note,
                    )
                )
                _state.update { it.copy(sheet = sheet.copy(saving = false)) }
                closeSheet()
                events.show("已记还款 · 余额归零")
                load()
            } catch (e: ApiError) {
                _state.update { it.copy(sheet = sheet.copy(saving = false, error = e.message)) }
            }
        }
    }

    /** 看板内「记一笔」：提交流水 */
    suspend fun createTransaction(body: TxnInput): TxnDto = repository.createTransaction(body)

    fun onTxnCreated(name: String) {
        events.show("已记入 " + name)
    }
}
