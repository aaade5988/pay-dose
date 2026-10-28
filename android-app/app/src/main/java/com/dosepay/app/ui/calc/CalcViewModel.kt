package com.dosepay.app.ui.calc

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosepay.app.core.Dates
import com.dosepay.app.core.Methods
import com.dosepay.app.core.Money
import com.dosepay.app.data.ApiError
import com.dosepay.app.data.MethodDto
import com.dosepay.app.data.PlannerRepository
import com.dosepay.app.data.RecItem
import com.dosepay.app.data.RecommendRequest
import com.dosepay.app.data.TxnDto
import com.dosepay.app.data.TxnInput
import com.dosepay.app.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 结果状态机 */
enum class ResultStatus { EMPTY, LOADING, READY, ERROR }

/** 单条推荐结果的展示模型 */
data class RecView(
    val rank: Int,
    val id: Long,
    val name: String,
    val initial: String,
    val color: Color,
    val ruleText: String,
    val occupiedDays: Int,
    val dueDateText: String,
    val dueShort: String,
    val currentUsedText: String,
    val afterUsedText: String,
    val limitShow: String,
    val remainingAfterText: String,
    val reason: String,
)

/** 计算页 UI 状态 */
data class CalcUiState(
    val amountInput: String = "",
    val amountFocused: Boolean = false,
    val amountDisplay: String = "0.00",
    val paymentDate: LocalDate = Dates.today(),
    val status: ResultStatus = ResultStatus.EMPTY,
    val errorMessage: String = "",
    val results: List<RecView> = emptyList(),
    val methodsCount: Int = 0,
    val methodList: List<MethodDto> = emptyList(),
    val methodsError: String = "",
    val resultCountText: String = "",
    val filteredNote: String = "",
) {
    val topOne: RecView? get() = results.firstOrNull()
    val paymentDateText: String get() = Dates.toStr(paymentDate)
    val paymentWeekday: String get() = Dates.weekdayLabel(paymentDate)
}

/**
 * 计算页（Tab1）ViewModel：金额 + 支付日期 -> POST /api/recommend -> 推荐结果。
 */
class CalcViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CalcUiState())
    val state: StateFlow<CalcUiState> = _state.asStateFlow()

    /** 后端原始卡片列表（用于合并规则文案与「下账单日前」推算） */
    private var methods: List<MethodDto> = emptyList()
    private var methodMap: Map<String, MethodDto> = emptyMap()
    private var lastResults: List<RecItem> = emptyList()
    private var lastAmount: String = ""

    init {
        loadMethods()
    }

    /* ────────────── 数据加载 ────────────── */

    fun loadMethods() {
        viewModelScope.launch {
            try {
                val list = repository.listMethods()
                methods = list
                methodMap = list.associateBy { it.id.toString() }
                _state.update { it.copy(methodsCount = list.size, methodList = list, methodsError = "") }
                // 已有结果时按最新的卡片信息重建展示模型
                if (_state.value.status == ResultStatus.READY && lastResults.isNotEmpty()) {
                    buildResults(lastResults)
                }
            } catch (e: ApiError) {
                methods = emptyList()
                methodMap = emptyMap()
                _state.update { it.copy(methodsCount = 0, methodList = emptyList(), methodsError = e.message) }
            }
        }
    }

    /* ────────────── 金额输入 ────────────── */

    /** 逐键清洗：非法字符 / 多余小数位静默剔除 */
    fun onAmountInput(raw: String) {
        val fixed = Money.sanitizeAmountInput(raw)
        _state.update {
            it.copy(amountInput = fixed, amountDisplay = Money.formatAmount(if (fixed.isEmpty()) "0" else fixed))
        }
    }

    /** 失焦：定稿补全两位小数 */
    fun onAmountBlur() {
        val current = _state.value.amountInput
        val finalized = Money.finalizeAmountInput(current)
        _state.update {
            it.copy(
                amountFocused = false,
                amountInput = finalized,
                amountDisplay = Money.formatAmount(if (finalized.isEmpty()) "0" else finalized),
            )
        }
    }

    fun onAmountFocus() {
        _state.update { it.copy(amountFocused = true) }
    }

    /** 清空金额：同时收起推荐结果，避免「金额已空、结果还停在上一次的金额上」 */
    fun onClearAmount() {
        lastResults = emptyList()
        lastAmount = ""
        _state.update {
            it.copy(
                amountInput = "",
                amountDisplay = "0.00",
                amountFocused = true,
                status = ResultStatus.EMPTY,
                errorMessage = "",
                results = emptyList(),
                resultCountText = "",
                filteredNote = "",
            )
        }
    }

    /* ────────────── 日期 ────────────── */

    /** 日期选择器回填 */
    fun onDatePicked(date: LocalDate) {
        _state.update { it.copy(paymentDate = date) }
    }

    /* ────────────── 生成推荐 ────────────── */

    fun generate() {
        if (_state.value.status == ResultStatus.LOADING) return
        val finalized = Money.finalizeAmountInput(_state.value.amountInput)
        if (finalized.isEmpty() || (Money.toCents(finalized) ?: 0L) <= 0L) {
            events.show("请输入大于 0 的支付金额")
            return
        }
        val amountStr = if (finalized.contains('.')) finalized else finalized + ".00"
        _state.update {
            it.copy(
                amountInput = amountStr,
                amountDisplay = Money.formatAmount(amountStr),
                status = ResultStatus.LOADING,
                errorMessage = "",
            )
        }
        lastAmount = amountStr
        runRecommend(amountStr)
    }

    private fun runRecommend(amountStr: String) {
        val date = _state.value.paymentDate
        viewModelScope.launch {
            try {
                val items = repository.recommend(
                    RecommendRequest(paymentDate = Dates.toStr(date), amount = amountStr, topN = 3)
                )
                lastResults = items
                if (items.isEmpty()) {
                    _state.update {
                        it.copy(
                            status = ResultStatus.ERROR,
                            errorMessage = "没有可用的支付方式：所有方式的「已用 + 本次」都超出额度，请调低金额或到「卡片」页提升额度。",
                            results = emptyList(),
                            resultCountText = "",
                        )
                    }
                } else {
                    buildResults(items)
                }
            } catch (e: ApiError) {
                lastResults = emptyList()
                _state.update {
                    it.copy(status = ResultStatus.ERROR, errorMessage = e.message, results = emptyList())
                }
            }
        }
    }

    /** 把后端 results 与本地 methods 信息合并成展示模型 */
    private fun buildResults(items: List<RecItem>) {
        val amountCents = Money.toCents(lastAmount) ?: 0L
        val methodsCount = _state.value.methodsCount
        val views = items.mapIndexed { index, item ->
            val card = methodMap[item.paymentMethodId.toString()]
            val hasLimit = !item.creditLimit.isNullOrBlank()

            val remainingAfter = when {
                card != null && !card.availableLimit.isNullOrBlank() ->
                    Money.money(Money.subAmount(card.availableLimit, Money.fromCents(amountCents)))
                hasLimit -> Money.money(Money.subAmount(item.creditLimit, item.afterUsed))
                else -> "不限额"
            }

            RecView(
                rank = index + 1,
                id = item.paymentMethodId,
                name = item.paymentMethod,
                initial = Methods.nameInitial(card?.name ?: item.paymentMethod),
                color = Methods.avatarColor(card?.name ?: item.paymentMethod),
                ruleText = card?.let { Methods.ruleSummary(it) } ?: fallbackRule(item),
                occupiedDays = item.occupiedDays,
                dueDateText = item.dueDate.take(10),
                dueShort = Dates.shortDate(item.dueDate),
                currentUsedText = Money.money(item.currentUsed),
                afterUsedText = Money.money(item.afterUsed),
                limitShow = if (hasLimit) Money.money(item.creditLimit) else "不限额",
                remainingAfterText = remainingAfter,
                reason = item.reason,
            )
        }
        _state.update {
            it.copy(
                status = ResultStatus.READY,
                errorMessage = "",
                results = views,
                resultCountText = "本次支付 " + Money.money(lastAmount) + " · " +
                    Dates.toStr(_state.value.paymentDate) + " · 共 " + views.size + " 种可用方式",
                filteredNote = if (methodsCount > views.size) {
                    "额度不足的 " + (methodsCount - views.size) + " 种方式已自动过滤"
                } else {
                    "同天数时优先推荐剩余额度更多的方式"
                },
            )
        }
    }

    /** 后端结果缺少本地卡片时的兜底规则文案 */
    private fun fallbackRule(item: RecItem): String {
        if (item.statementDay != null) {
            val due = item.dueDate.take(10)
            val dueShort = if (due.length > 5) due.substring(5) else due
            return "账单日 " + item.statementDay + " 日 · 预计 " + dueShort + " 还款"
        }
        return "即时扣款 · 占用 0 天"
    }

    /** Top2/3 行点击：置顶展开（原地交换，减少跳转） */
    fun promote(rank: Int) {
        if (rank <= 1) return
        val current = _state.value.results
        if (rank > current.size) return
        val picked = current[rank - 1]
        val reordered = listOf(picked.copy(rank = 1)) + current.filterIndexed { index, _ -> index != rank - 1 }
        _state.update { it.copy(results = reordered.mapIndexed { index, item -> item.copy(rank = index + 1) }) }
    }

    /** 提交一笔流水（记账弹层调用） */
    suspend fun createTransaction(body: TxnInput): TxnDto = repository.createTransaction(body)

    /** 记一笔成功提示 */
    fun onTxnCreated(methodName: String) {
        events.show("已记入 " + methodName)
    }

    /** 记一笔成功后刷新卡片余额 */
    fun onTxnSubmitted() {
        loadMethods()
    }
}
