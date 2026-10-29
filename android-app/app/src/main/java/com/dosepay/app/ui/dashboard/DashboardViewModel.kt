package com.dosepay.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosepay.app.core.AggUsage
import com.dosepay.app.core.CycleAgg
import com.dosepay.app.core.Cycles
import com.dosepay.app.core.DashRow
import com.dosepay.app.core.Methods
import com.dosepay.app.data.ApiError
import com.dosepay.app.data.CycleSummaryDto
import com.dosepay.app.data.MethodDto
import com.dosepay.app.data.PlannerRepository
import com.dosepay.app.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 看板页状态 */
data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String = "",
    val rows: List<DashRow> = emptyList(),
    /** 顶部环：本账单周期消费合计 ÷ Σ已设额度（与下方逐卡「本期消费」同源） */
    val gauge: AggUsage = Methods.cycleGauge(emptyList(), 0L),
    /** 本账单周期聚合（上一还款日 → 本还款日） */
    val cycle: CycleAgg = Cycles.aggregate(CycleSummaryDto()),
    /** 周期接口单独失败时的提示；额度数据仍可正常展示 */
    val cycleError: String = "",
    /** 聚焦的单卡（点下方周期卡 → 上方环切到这张卡）；null = 看全部 */
    val focusedId: Long? = null,
    /** 聚焦卡的环数据；null = 用聚合环 */
    val focusedGauge: AggUsage? = null,
)

/**
 * 额度看板页（Tab3）ViewModel：本期聚合仪表盘 + 本账单周期。
 *
 * 全页统一走「本期」口径：
 * - 顶部环（[DashboardUiState.gauge]）= 本期消费合计 ÷ Σ已设额度；
 * - 下方逐卡行（[DashboardUiState.cycle]）= 只统计「上一还款日 → 本还款日」。
 *
 * 两者同源，本期一笔没刷时环就是 0%。跨周期累积的 `current_used`
 * 只出现在「余额详情」弹层里（它不等于本期消费，2026-10-28 起不再上环）。
 */
class DashboardViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var rawMethods: List<MethodDto> = emptyList()

    /** 「已还清」请求进行中（防重复点击） */
    private var settling = false

    /** 是否已经拿到过一次结果（成功或失败都算）：之后刷新就别再闪加载态 */
    private var hasLoaded = false

    /** 取看板数据。suspend：下拉刷新容器要等它真正结束再收指示器 */
    suspend fun load() {
        val first = !hasLoaded
        _state.update { it.copy(loading = first, refreshing = !first, errorMessage = "") }
        try {
            val list = repository.listMethods()
            rawMethods = list
            _state.update { st ->
                st.copy(
                    loading = false,
                    refreshing = false,
                    errorMessage = "",
                    rows = list.map(Methods::toDashRow),
                    gauge = Methods.cycleGauge(
                        list,
                        st.cycle.totalNetCents,
                        st.cycle.settledNetCents,
                        st.cycle.totalRepayCents,
                    ),
                )
            }
        } catch (e: ApiError) {
            _state.update { it.copy(loading = false, refreshing = false, errorMessage = e.message) }
            return
        } finally {
            hasLoaded = true
        }
        // 周期数据独立加载：失败只降级提示，不影响卡片数据
        try {
            val cycle = Cycles.aggregate(repository.cycleSummary())
            _state.update {
                it.copy(
                    cycle = cycle,
                    gauge = Methods.cycleGauge(
                        rawMethods,
                        cycle.totalNetCents,
                        cycle.settledNetCents,
                        cycle.totalRepayCents,
                    ),
                    focusedGauge = focusGauge(it.focusedId, cycle),
                    cycleError = "",
                )
            }
        } catch (e: ApiError) {
            _state.update { it.copy(cycleError = e.message) }
        }
    }

    /**
     * 点下方周期卡 → 上方环切到这张卡单独的数据；再点同一张取消，回到全部。
     *
     * 纯本地切换，不重新请求接口；刷新后仍保持聚焦。
     */
    fun toggleFocus(id: Long) {
        _state.update { st ->
            val next = if (st.focusedId == id) null else id
            st.copy(focusedId = next, focusedGauge = focusGauge(next, st.cycle))
        }
    }

    /**
     * 单卡环：分母 = 该卡自己的额度，分子 = 该卡本期待还。
     *
     * 口径和聚合环完全一致（[Methods.cycleGauge]），只是把范围收窄到一张卡，
     * 所以「点开某张卡看到的百分比」和「全部卡的百分比」是同一把尺子。
     */
    private fun focusGauge(id: Long?, cycle: CycleAgg): AggUsage? {
        if (id == null) return null
        val row = cycle.rows.firstOrNull { it.id == id } ?: return null
        val method = rawOf(id) ?: return null
        return Methods.cycleGauge(
            methods = listOf(method),
            netCents = row.netCents,
            settledCents = if (row.settled) row.netCents else 0L,
            repaidCents = row.repayCents,
        )
    }

    private fun rawOf(id: Long): MethodDto? = rawMethods.firstOrNull { it.id == id }

    /**
     * 看板周期卡左滑「已还清 / 取消标记」。
     *
     * 这是**人工标记**（后端 `PUT /api/methods/{id}/settle`）：
     * - 不写任何流水、不动已用余额；
     * - 标记后该卡本期消费在看板显示「已还清」，仪表盘里那一段冻结成另一种颜色；
     * - 标记有效期 = 本期还款日，过了自动失效。
     */
    fun setSettled(methodId: Long, settled: Boolean) {
        if (settling) return
        val raw = rawOf(methodId) ?: return
        settling = true
        viewModelScope.launch {
            try {
                repository.setMethodSettled(methodId, settled)
                events.show(if (settled) "已标记已还清 · " + raw.name else "已取消标记 · " + raw.name)
                load()
            } catch (e: ApiError) {
                events.show(e.message)
            } finally {
                settling = false
            }
        }
    }
}
