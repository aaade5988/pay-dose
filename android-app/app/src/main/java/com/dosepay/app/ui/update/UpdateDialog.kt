package com.dosepay.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.ui.theme.PayColors

/**
 * 发现新版本时的提示弹窗。
 *
 * 只展示版本号、更新说明与「去下载」按钮。
 * 不做应用内下载与安装——那需要「安装未知来源应用」权限，
 * 而且各家 ROM 的安装器行为差异很大，稳定性不如交给浏览器。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "发现新版本  ${state.latestVersion}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row {
                    Text(
                        "当前版本 ${state.currentVersion}" +
                            if (state.apkSizeText.isNotBlank()) " · ${state.apkSizeText}" else "",
                        fontSize = 12.sp,
                        color = PayColors.TextMuted,
                    )
                }

                if (state.releaseNotes.isNotBlank()) {
                    Text(
                        state.releaseNotes.trim(),
                        fontSize = 13.sp,
                        color = PayColors.TextMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "包含问题修复与体验优化。",
                        fontSize = 13.sp,
                        color = PayColors.TextMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("去下载", color = PayColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("以后再说", color = PayColors.TextMuted)
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
