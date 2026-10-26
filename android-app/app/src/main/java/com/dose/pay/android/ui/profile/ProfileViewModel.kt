package com.dose.pay.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.PlannerRepository
import com.dose.pay.android.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 我的页状态 */
data class ProfileUiState(
    val baseUrl: String = "",
    val editing: Boolean = false,
    val testing: Boolean = false,
    val resultMessage: String = "",
    val resultOk: Boolean = false,
)

/**
 * 我的页 ViewModel：后端地址配置 + 连接测试。
 */
class ProfileViewModel(
    private val settings: SettingsStore,
    private val repository: PlannerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(baseUrl = settings.baseUrl))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun setBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value, resultMessage = "", resultOk = false) }
    }

    fun startEdit() {
        _state.update { it.copy(editing = true, baseUrl = settings.baseUrl, resultMessage = "") }
    }

    fun cancelEdit() {
        _state.update { it.copy(editing = false, baseUrl = settings.baseUrl, resultMessage = "") }
    }

    /** 保存后端地址并持久化 */
    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(resultMessage = "后端地址不能为空", resultOk = false) }
            return
        }
        settings.baseUrl = url
        _state.update {
            it.copy(editing = false, baseUrl = settings.baseUrl, resultMessage = "已保存", resultOk = true)
        }
    }

    /** 测试连接：GET /api/health */
    fun testConnection() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, resultMessage = "正在测试连接…", resultOk = false) }
        viewModelScope.launch {
            try {
                val status = repository.health()
                _state.update {
                    it.copy(testing = false, resultMessage = "连接正常 · " + status, resultOk = true)
                }
            } catch (e: ApiError) {
                _state.update { it.copy(testing = false, resultMessage = e.message, resultOk = false) }
            }
        }
    }
}
