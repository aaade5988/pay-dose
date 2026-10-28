package com.dosepay.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosepay.app.data.update.UpdateChecker
import com.dosepay.app.data.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 更新提示弹窗状态 */
data class UpdateUiState(
    /** 是否显示弹窗 */
    val visible: Boolean = false,
    /** 是否正在检查（手动触发时显示 loading） */
    val checking: Boolean = false,
    /** 新版本号，如 "v1.1.0" */
    val latestVersion: String = "",
    /** 当前版本号 */
    val currentVersion: String = "",
    /** 更新说明 */
    val releaseNotes: String = "",
    /** 下载地址（APK 直链或 Release 网页） */
    val downloadUrl: String = "",
    /** APK 大小（字节），0 表示未知 */
    val apkSizeBytes: Long = 0L,
    /** 检查失败的提示，空表示无错误 */
    val errorMessage: String = "",
) {
    /** 格式化文件大小，如 "10.9 MB" */
    val apkSizeText: String
        get() = if (apkSizeBytes <= 0) "" else "%.1f MB".format(apkSizeBytes / 1024.0 / 1024.0)
}

/**
 * 更新检查 ViewModel。
 *
 * 两种触发方式：
 * - [checkSilently]：启动时静默检查，只有发现新版本才弹窗，失败不打扰用户
 * - [checkManually]：用户主动点「检查更新」，无论结果如何都给反馈
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val currentVersionName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState(currentVersion = currentVersionName))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 启动时静默检查：失败不提示，避免每次开 App 都弹网络错误 */
    fun checkSilently() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            when (val result = checker.check(currentVersionName)) {
                is UpdateResult.Available -> _state.update {
                    it.copy(
                        checking = false,
                        visible = true,
                        latestVersion = result.latestVersion,
                        releaseNotes = result.releaseNotes,
                        downloadUrl = result.apkDownloadUrl.orEmpty(),
                        apkSizeBytes = result.apkSizeBytes,
                        errorMessage = "",
                    )
                }
                // 静默模式：已是最新 / 失败，都不打扰
                is UpdateResult.UpToDate, is UpdateResult.Failed -> _state.update {
                    it.copy(checking = false)
                }
                UpdateResult.Checking -> Unit
            }
        }
    }

    /** 手动检查：明确告知用户结果 */
    fun checkManually() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            when (val result = checker.check(currentVersionName)) {
                is UpdateResult.Available -> _state.update {
                    it.copy(
                        checking = false,
                        visible = true,
                        latestVersion = result.latestVersion,
                        releaseNotes = result.releaseNotes,
                        downloadUrl = result.apkDownloadUrl.orEmpty(),
                        apkSizeBytes = result.apkSizeBytes,
                        errorMessage = "",
                    )
                }
                is UpdateResult.UpToDate -> _state.update {
                    it.copy(checking = false, errorMessage = "已是最新版本（$currentVersionName）")
                }
                is UpdateResult.Failed -> _state.update {
                    it.copy(checking = false, errorMessage = result.message)
                }
                UpdateResult.Checking -> Unit
            }
        }
    }

    /** 点「去下载」：交给浏览器 */
    fun openDownload() {
        val url = _state.value.downloadUrl
        if (url.isNotBlank()) checker.openDownload(url)
        dismiss()
    }

    /** 关闭弹窗 */
    fun dismiss() {
        _state.update { it.copy(visible = false) }
    }

    /** 清掉手动检查的错误提示 */
    fun clearMessage() {
        _state.update { it.copy(errorMessage = "") }
    }
}
