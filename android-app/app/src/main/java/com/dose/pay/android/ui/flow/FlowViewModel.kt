package com.dose.pay.android.ui.flow

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dose.pay.android.core.DayGroup
import com.dose.pay.android.core.Methods
import com.dose.pay.android.core.MonthSummary
import com.dose.pay.android.core.Txns
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.MethodDto
import com.dose.pay.android.data.PlannerRepository
import com.dose.pay.android.data.TxnDto
import com.dose.pay.android.data.TxnInput
import com.dose.pay.android.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 筛选条上的一张卡 */
data class FilterItem(
    val id: Long,
    val idStr: String,
    val name: String,
    val short: String,
    val initial: String,
    val color: Color,
)

/** 流水页状态 */
data class FlowUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String = "",
    val metrics: MonthSummary = Txns.summarizeMonth(emptyList()),
    val filters: List<FilterItem> = emptyList(),
    val selectedId: String = "",
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val methodList: List<MethodDto> = emptyList(),
)

/**
 * 消费流水页 ViewModel：从看板进入的二级页。
 */
class FlowViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    companion object {
        /** 每页条数（与原小程序一致） */
        const val PAGE_LIMIT = 200
    }

    private val _state = MutableStateFlow(FlowUiState())
    val state: StateFlow<FlowUiState> = _state.asStateFlow()

    private var allTxns: List<TxnDto> = emptyList()
    private var rawMethods: List<MethodDto> = emptyList()

    fun load() {
        _state.update { it.copy(loading = it.groups.isEmpty(), refreshing = true, errorMessage = "") }
        viewModelScope.launch {
            try {
                val methods = repository.listMethods()
                val txns = repository.listTransactions(null, PAGE_LIMIT, 0)
                rawMethods = methods
                allTxns = txns
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "",
                        methodList = methods,
                        hasMore = txns.size >= PAGE_LIMIT,
                        filters = methods.map { m ->
                            FilterItem(
                                id = m.id,
                                idStr = m.id.toString(),
                                name = m.name,
                                short = Txns.shortName(m.name),
                                initial = Methods.nameInitial(m.name),
                                color = Methods.avatarColor(m.name),
                            )
                        },
                    )
                }
                rebuild()
            } catch (e: ApiError) {
                allTxns = emptyList()
                rawMethods = emptyList()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = e.message,
                        groups = emptyList(),
                        filters = emptyList(),
                        methodList = emptyList(),
                    )
                }
            }
        }
    }

    /** 触底加载更多 */
    fun loadMore() {
        val st = _state.value
        if (st.loading || st.loadingMore || !st.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val more = repository.listTransactions(null, PAGE_LIMIT, allTxns.size)
                allTxns = allTxns + more
                _state.update { it.copy(loadingMore = false, hasMore = more.size >= PAGE_LIMIT) }
                rebuild()
            } catch (e: ApiError) {
                _state.update { it.copy(loadingMore = false) }
                events.show(e.message)
            }
        }
    }

    /** 切换筛选（"" = 全部） */
    fun selectFilter(id: String) {
        if (id == _state.value.selectedId) return
        _state.update { it.copy(selectedId = id) }
        rebuild()
    }

    /** 筛选变化后重建：三月指标条 + 按天分组 */
    private fun rebuild() {
        val selected = _state.value.selectedId
        val list = if (selected.isEmpty()) {
            allTxns
        } else {
            allTxns.filter { it.paymentMethodId.toString() == selected }
        }
        _state.update {
            it.copy(
                metrics = Txns.summarizeMonth(list),
                groups = Txns.groupByDay(list),
                count = list.size,
            )
        }
    }

    /** 删除流水（后端同事务回滚余额） */
    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                events.show("已删除，余额已回滚")
                load()
            } catch (e: ApiError) {
                events.show(e.message)
            }
        }
    }

    /** 提交一笔流水 */
    suspend fun createTransaction(body: TxnInput): TxnDto = repository.createTransaction(body)

    fun onTxnCreated(name: String) {
        events.show("已记入 " + name)
    }
}
