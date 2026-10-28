package com.dosepay.app.ui.cards

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosepay.app.ui.components.Avatar
import com.dosepay.app.ui.components.EmptyStateBox
import com.dosepay.app.ui.components.ErrorStateBox
import com.dosepay.app.ui.components.HintText
import com.dosepay.app.ui.components.PayChip
import com.dosepay.app.ui.components.PayPullToRefresh
import com.dosepay.app.ui.components.PrimaryButton
import com.dosepay.app.ui.components.SwipeRevealRow
import com.dosepay.app.ui.theme.PayColors

/**
 * 卡片列表页（Tab2）：只展示规则，不展示余额。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    vm: CardsViewModel,
    onOpenEditor: (Long?) -> Unit,
    onGoDashboard: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<CardRow?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    PayPullToRefresh(
        isRefreshing = state.refreshing,
        onRefresh = { vm.load() },
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        /* 顶部操作条 */
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("我的支付方式", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(PayColors.Secondary)
                    .clickable { onOpenEditor(null) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("＋", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Primary)
                Text("新增", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PayColors.Primary)
            }
        }

        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("正在加载支付方式…", fontSize = 13.sp, color = PayColors.Hint)
                }
            }

            state.errorMessage.isNotEmpty() -> {
                ErrorStateBox(title = "⚠️ 未能加载列表", description = state.errorMessage)
                Text(
                    "重新加载",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayColors.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.load() }
                        .padding(vertical = 6.dp),
                )
            }

            state.rows.isEmpty() -> {
                EmptyStateBox(
                    title = "还没有任何支付方式",
                    description = "点击右上角「＋ 新增」录入信用卡 / 储蓄卡 / 电子钱包",
                )
                PrimaryButton(text = "新增支付方式", modifier = Modifier.padding(top = 8.dp)) {
                    onOpenEditor(null)
                }
                Text(
                    "先去看板看看额度 ›",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayColors.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGoDashboard() }
                        .padding(top = 10.dp),
                )
            }

            else -> {
                state.rows.forEach { row ->
                    SwipeRevealRow(
                        modifier = Modifier.fillMaxWidth(),
                        actionLabel = "删除",
                        onAction = { pendingDelete = row },
                        onContentClick = { onOpenEditor(row.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Avatar(row.initial, row.color, size = 32.dp, fontSize = 15)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        row.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    PayChip(text = row.methodType, style = row.chipStyle)
                                }
                                HintText(row.ruleText)
                            }
                            Text("›", fontSize = 16.sp, color = PayColors.Care)
                        }
                    }
                }

               
            }
        }
    }
    }

    /* 删除确认 */
    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除支付方式", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确定删除「" + target.name + "」吗？删除后无法恢复。",
                    fontSize = 14.sp,
                    color = PayColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.delete(target.id)
                }) { Text("删除", color = PayColors.Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = PayColors.TextMuted)
                }
            },
        )
    }
}
