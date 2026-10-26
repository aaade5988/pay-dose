package com.dose.pay.android.ui.calc

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dose.pay.android.core.Dates
import com.dose.pay.android.core.Methods
import com.dose.pay.android.core.Money
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.MethodDto
import com.dose.pay.android.data.PlannerRepository
import com.dose.pay.android.data.RecItem
import com.dose.pay.android.data.RecommendRequest
import com.dose.pay.android.data.TxnDto
import com.dose.pay.android.data.TxnInput
import com.dose.pay.android.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 结果状态机 */
enum class ResultStatus { EMPTY, LOADING, READY, ERROR }

/** 快捷日期 chip 的键 */
object QuickKey {
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val WEEKEND = "weekend"
    const val BEFORE_STATEMENT = "before-statement"
    const val CUSTOM = "custom"
}

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
    val ringNone: Boolean,
    val ringP: Float,
    val ringColor: Color,
    val ringPctText: String,
    val reason: String,
)

/** 计算页 UI 状态 */
data class CalcUiState(
    val amountInput: String = "",
    val amountFocused: Boolean = false,
    val amountDisplay: String = "0.00",
    val paymentDate: LocalDate = Dates.today(),
    val quickKey: String = QuickKey.TODAY,
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
    val rest: List<RecView> get() = results.drop(1)
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
        setDate(Dates.today(), QuickKey.TODAY)
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

    fun onClearAmount() {
        _state.update { it.copy(amountInput = "", amountDisplay = "0.00", amountFocused = true) }
    }

    /* ────────────── 日期 ────────────── */

    /** 统一入口：同步日期、星期与命中的快捷 chip */
    fun setDate(date: LocalDate, key: String) {
        val resolved = if (key != QuickKey.CUSTOM) key else quickKeyOfDate(date)
        _state.update { it.copy(paymentDate = date, quickKey = resolved) }
    }

    fun onDatePicked(date: LocalDate) {
        setDate(date, QuickKey.CUSTOM)
    }

    private fun quickKeyOfDate(date: LocalDate): String {
        val today = Dates.today()
        val map = mapOf(
            QuickKey.TODAY to Dates.toStr(today),
            QuickKey.TOMORROW to Dates.toStr(Dates.addDays(today, 1)),
            QuickKey.WEEKEND to Dates.toStr(Dates.weekendFrom(today)),
        )
        for ((key, value) in map) {
            if (value == Dates.toStr(date)) return key
        }
        return QuickKey.CUSTOM
    }

    /** 快捷 chip 点击 */
    fun onQuickTap(key: String) {
        val today = Dates.today()
        if (key == QuickKey.BEFORE_STATEMENT) {
            val nearest = Methods.dayBeforeNearestStatement(methods, today)
            if (nearest == null) {
                events.show("请先在「卡片」页添加信用卡")
                return
            }
            setDate(nearest.first, QuickKey.BEFORE_STATEMENT)
            events.show("最近账单日前 · " + nearest.second)
            return
        }
        val date = when (key) {
            QuickKey.TOMORROW -> Dates.addDays(today, 1)
            QuickKey.WEEKEND -> Dates.weekendFrom(today)
            else -> today
        }
        setDate(date, key)
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
            val afterPct = if (hasLimit) Money.percentOf(item.afterUsed, item.creditLimit) else null
            val ringStatus = Methods.usageStatus(afterPct)

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
                ringNone = afterPct == null,
                ringP = Methods.ringArc(afterPct),
                ringColor = ringStatus.color,
                ringPctText = if (afterPct == null) "—" else Math.round(afterPct).toInt().toString() + "%",
                reason = item.reason,
            )
        }
        _state.update {
            it.copy(
                status = ResultStatus.READY,
                errorMessage = "",
                results = views,
                resultCountText = "本次支付 " + Money.money(lastAmount) + " · 共 " + views.size + " 种可用方式",
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
