package com.dosepay.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosepay.app.core.BillRow
import com.dosepay.app.core.Money
import com.dosepay.app.ui.components.EmptyStateBox
import com.dosepay.app.ui.components.ErrorStateBox
import com.dosepay.app.ui.components.HintText
import com.dosepay.app.ui.components.PayPullToRefresh
import com.dosepay.app.ui.theme.PayColors

/**
 * 账单页（Tab4，原「我的」位）。
 *
 * 结构：顶部总览卡 → 卡筛选条 → 按月倒序的月卡（点标题可折叠）→ 月内明细行。
 * 明细行展示：卡名（字头色圆）+ 备注 + 类型 chip / 金额 / 时间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: BillsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<BillRow?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    PayPullToRefresh(
        isRefreshing = state.refreshing,
        onRefresh = { vm.load() },
    ) {
        Column(
            // 撑满一屏：内容不满时下拉手势也得有地方落，否则摸到空白处等于没反应
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
                .padding(top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            /* 标题栏 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "账单",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PayColors.Text,
                    modifier = Modifier.weight(1f),
                )
                if (state.months.isNotEmpty()) {
                    val allCollapsed = state.months.all { it.monthKey in state.collapsed }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(PayColors.Secondary)
                            .clickable { if (allCollapsed) vm.expandAll() else vm.collapseAll() }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (allCollapsed) "展开全部" else "收起全部",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PayColors.Primary,
                        )
                    }
                }
            }

            when {
                state.loading -> {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("正在汇总账单…", fontSize = 13.sp, color = PayColors.Hint)
                            HintText(
                                "首次进入需要拉全部流水再按月聚合",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                state.errorMessage.isNotEmpty() -> {
                    ErrorStateBox(title = "⚠️ 未能加载账单", description = state.errorMessage)
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

                else -> {
                    /* ① 总览卡 */
                    OverviewCard(
                        spendText = state.overview.totalSpendText,
                        filtered = state.selectedId.isNotEmpty(),
                    )

                    /* ② 卡筛选 */
                    if (state.filters.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilterDot(
                                label = "全部",
                                initial = "全",
                                color = if (state.selectedId.isEmpty()) PayColors.Primary else PayColors.ChipGrayBg,
                                active = state.selectedId.isEmpty(),
                                onClick = { vm.selectFilter("") },
                            )
                            state.filters.forEach { f ->
                                FilterDot(
                                    label = f.short,
                                    initial = f.initial,
                                    color = f.color,
                                    active = state.selectedId == f.idStr,
                                    onClick = { vm.selectFilter(f.idStr) },
                                )
                            }
                        }
                    }

                    /* ③ 按月倒序的月卡 */
                    if (state.months.isEmpty()) {
                        EmptyStateBox(
                            title = if (state.selectedId.isNotEmpty()) "该卡暂无消费记录" else "还没有消费记录",
                            description = if (state.selectedId.isNotEmpty()) {
                                "换个卡看看，或切换到「全部」"
                            } else {
                                "在「计算」页点「记一笔」，流水会按月自动汇总到这里"
                            },
                        )
                    } else {
                        state.months.forEach { month ->
                            MonthCard(
                                month = month,
                                collapsed = month.monthKey in state.collapsed,
                                onToggle = { vm.toggleMonth(month.monthKey) },
                                onDelete = { pendingDelete = it },
                            )
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
            title = { Text("删除流水", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "删除后 " + target.paymentMethodName + " 余额将回滚 " +
                        Money.money(target.amount) + "，确定删除这笔「" + target.type + "」吗？",
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
