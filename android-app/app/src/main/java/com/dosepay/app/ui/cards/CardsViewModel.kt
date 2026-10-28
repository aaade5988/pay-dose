package com.dosepay.app.ui.cards

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosepay.app.core.ChipStyle
import com.dosepay.app.core.Methods
import com.dosepay.app.data.ApiError
import com.dosepay.app.data.PlannerRepository
import com.dosepay.app.data.UiEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 卡片列表行模型（v3 收敛为「只配置规则」） */
data class CardRow(
    val id: Long,
    val name: String,
    val methodType: String,
    val initial: String,
    val color: Color,
    val chipStyle: ChipStyle,
    val ruleText: String,
)

/** 卡片列表页状态 */
data class CardsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String = "",
    val rows: List<CardRow> = emptyList(),
) {
    val count: Int get() = rows.size
}

/**
 * 卡片列表页（Tab2）ViewModel：只配置规则，不展示余额。
 */
class CardsViewModel(
    private val repository: PlannerRepository,
    private val events: UiEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.rows.isEmpty(), refreshing = true, errorMessage = "") }
        viewModelScope.launch {
            try {
                val list = repository.listMethods()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "",
                        rows = list.map { m ->
                            CardRow(
                                id = m.id,
                                name = m.name,
                                methodType = m.methodType,
                                initial = Methods.nameInitial(m.name),
                                color = Methods.avatarColor(m.name),
                                chipStyle = Methods.typeChipStyle(m.methodType),
                                ruleText = Methods.ruleLine(m),
                            )
                        },
                    )
                }
            } catch (e: ApiError) {
                _state.update { it.copy(loading = false, refreshing = false, errorMessage = e.message, rows = emptyList()) }
            }
        }
    }

    /** 删除卡片（该卡有流水时后端返回 409 中文 detail） */
    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteMethod(id)
                events.show("已删除")
                load()
            } catch (e: ApiError) {
                events.show(e.message)
            }
        }
    }
}
