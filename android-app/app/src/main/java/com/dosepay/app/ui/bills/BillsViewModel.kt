package com.dosepay.app.ui.bills

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosepay.app.core.BillMonth
import com.dosepay.app.core.Bills
import com.dosepay.app.core.Methods
import com.dosepay.app.core.Txns
import com.dosepay.app.data.ApiError
import com.dosepay.app.data.PlannerRepository
import com.dosepay.app.data.TxnDto
import com.dosepay.app.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 筛选条上的一张卡 */
data class BillFilter(
    val idStr: String,
    val short: String,
    val initial: String,
    val color: Color,
)

/**
 * 账单页状态。
 *
 * 账单页是「全量按月回顾」，因此首次进入会把流水全部翻页拉回本地再聚合，
 * 拉取完成前显示加载态，避免出现「只聚合了前 200 笔」的错误月汇总。
 */
data class BillsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String = "",
    val overview: Bills.BillOverview = Bills.overview(emptyList()),
    val months: List<BillMonth> = emptyList(),
    val filters: List<BillFilter> = emptyList(),
    val selectedId: String = "",
    /** 折叠的月份 key 集合 */
    val collapsed: Set<String> = emptySet(),
    /** 已加载到的条数（用于估算总笔数文案） */
    val loadedCount: Int = 0,
    val hasMore: Boolean = false,
)

/**
 * 账单页 ViewModel：按自然月倒序聚合当月全部流水。
 */
class BillsViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    companion object {
        /** 后端 limit 上限 200 */
        const val PAGE_LIMIT = 200

        /** 全量拉取的翻页上限，防御性止损（200 × 50 = 10000 笔） */
        const val MAX_PAGES = 50
    }

    private val _state = MutableStateFlow(BillsUiState())
    val state: StateFlow<BillsUiState> = _state.asStateFlow()

    /** 全量流水缓存（已按 txn_date DESC, id DESC） */
    private var allTxns: List<TxnDto> = emptyList()

    /** 首次进入 / 下拉刷新：重建全量缓存 */
    fun load() {
        val first = allTxns.isEmpty()
        _state.update {
            it.copy(
                loading = first,
                refreshing = !first,
                loadingMore = false,
                errorMessage = "",
            )
        }
        viewModelScope.launch {
            try {
                val methods = repository.listMethods()
                val fetched = ArrayList<TxnDto>(PAGE_LIMIT * 2)
                var offset = 0
                var page = 0
                while (page < MAX_PAGES) {
                    val chunk = repository.listTransactions(null, PAGE_LIMIT, offset)
                    fetched.addAll(chunk)
                    if (chunk.size < PAGE_LIMIT) break
                    offset += chunk.size
                    page++
                }
                allTxns = fetched
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "",
                        filters = methods.map { m ->
                            BillFilter(
                                idStr = m.id.toString(),
                                short = Txns.shortName(m.name),
                                initial = Methods.nameInitial(m.name),
                                color = Methods.avatarColor(m.name),
                            )
                        },
                        loadedCount = fetched.size,
                        hasMore = page >= MAX_PAGES,
                    )
                }
                rebuild()
            } catch (e: ApiError) {
                // 首屏失败才清空；下拉刷新失败保留旧数据，只提示
                if (allTxns.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            errorMessage = e.message,
                            months = emptyList(),
                            filters = emptyList(),
                            overview = Bills.overview(emptyList()),
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false, refreshing = false) }
                    events.show(e.message)
                }
            }
        }
    }

    /** 切换卡筛选（"" = 全部） */
    fun selectFilter(id: String) {
        if (id == _state.value.selectedId) return
        _state.update { it.copy(selectedId = id) }
        rebuild()
    }

    /** 折叠 / 展开某个月 */
    fun toggleMonth(monthKey: String) {
        _state.update {
            val next = it.collapsed.toMutableSet()
            if (!next.add(monthKey)) next.remove(monthKey)
            it.copy(collapsed = next)
        }
    }

    /** 全部展开 */
    fun expandAll() {
        _state.update { it.copy(collapsed = emptySet()) }
    }

    /** 全部折叠 */
    fun collapseAll() {
        _state.update { st -> st.copy(collapsed = st.months.map { it.monthKey }.toSet()) }
    }

    /**
     * 筛选变化后重建：顶部总览 + 按月聚合。
     *
     * 注意筛选按「卡」过滤，月汇总随之变为该卡口径。
     */
    private fun rebuild() {
        val selected = _state.value.selectedId
        val list = if (selected.isEmpty()) {
            allTxns
        } else {
            allTxns.filter { it.paymentMethodId.toString() == selected }
        }
        val months = Bills.groupByMonth(list)
        _state.update {
            it.copy(
                months = months,
                overview = Bills.overview(list),
                // 切筛选后清掉折叠态，避免用户看不到新数据
                collapsed = it.collapsed.intersect(months.map { m -> m.monthKey }.toSet()),
            )
        }
    }

    /** 删除流水（同事务回滚余额）：列表行会立刻消失，不再额外弹 toast */
    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                load()
            } catch (e: ApiError) {
                events.show(e.message)
            }
        }
    }
}
