package com.dose.pay.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dose.pay.android.ui.components.HintText
import com.dose.pay.android.ui.components.OutlineButton
import com.dose.pay.android.ui.components.PayCard
import com.dose.pay.android.ui.components.PrimaryButton
import com.dose.pay.android.ui.theme.PayColors

/** 功能说明条目 */
private data class FeatureItem(val icon: String, val name: String, val desc: String)

private val FEATURES = listOf(
    FeatureItem("☁️", "服务端存储", "卡片与流水保存在后端 SQLite，多端一致"),
    FeatureItem("📤", "导出备份", "卡片规则 + 消费流水 CSV / JSON 备份（暂未开放）"),
    FeatureItem("📄", "免责声明", "计算结果仅供参考 · 还款日节假日可能顺延"),
)

/**
 * 我的（Tab4）：后端地址配置 + 版本信息 + 功能说明 + 免责声明。
 */
@Composable
fun ProfileScreen(vm: ProfileViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        /* Hero */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(PayColors.PrimaryDeep, PayColors.Primary)))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("💳", fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("pay-dose", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(
                    "v1.0.0 · 信用卡支付规划器",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }

        /* 后端地址配置 */
        PayCard(contentPadding = 12.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("后端地址", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
                if (!state.editing) {
                    Text(
                        "编辑",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Primary,
                        modifier = Modifier.clickable { vm.startEdit() },
                    )
                }
            }

            if (state.editing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PayColors.Bg)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    BasicTextField(
                        value = state.baseUrl,
                        onValueChange = { vm.setBaseUrl(it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = PayColors.Text),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (state.baseUrl.isEmpty()) {
                                    Text("http://10.0.2.2:8000", fontSize = 13.sp, color = PayColors.Care)
                                }
                                inner()
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlineButton(text = "取消", modifier = Modifier.weight(1f)) { vm.cancelEdit() }
                    PrimaryButton(text = "保存", modifier = Modifier.weight(1f)) { vm.saveBaseUrl() }
                }
            } else {
                Text(
                    state.baseUrl,
                    fontSize = 13.sp,
                    color = PayColors.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineButton(
                    text = if (state.testing) "测试中…" else "测试连接",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.testConnection() },
                )
            }

            if (state.resultMessage.isNotEmpty()) {
                Text(
                    state.resultMessage,
                    fontSize = 12.sp,
                    color = if (state.resultOk) PayColors.RepayGreen else PayColors.AccentText,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            HintText("模拟器访问宿主机请使用 http://10.0.2.2:8000；真机请填写局域网 IP。")
        }

        /* 功能说明 */
        PayCard(contentPadding = 12.dp) {
            FEATURES.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(item.icon, fontSize = 20.sp, modifier = Modifier.size(width = 28.dp, height = 24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
                        HintText(item.desc)
                    }
                }
            }
        }

        /* 免责声明 */
        PayCard(contentPadding = 12.dp) {
            Text("免责声明", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
            HintText(
                "计算结果仅供参考 · 还款日节假日可能顺延。本应用不构成任何消费或理财建议，" +
                    "请以银行实际账单与还款日为准。",
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        HintText(
            "pay-dose · v1.0.0",
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFA09CAC),
        )
    }
}
