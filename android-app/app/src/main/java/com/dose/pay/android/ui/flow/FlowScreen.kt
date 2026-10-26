package com.dose.pay.android.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.dose.pay.android.core.Money
import com.dose.pay.android.core.Txns
import com.dose.pay.android.ui.components.Avatar
import com.dose.pay.android.ui.components.EmptyStateBox
import com.dose.pay.android.ui.components.ErrorStateBox
import com.dose.pay.android.ui.components.HintText
import com.dose.pay.android.ui.components.PayChip
import com.dose.pay.android.ui.components.PrimaryButton
import com.dose.pay.android.ui.components.SwipeRevealRow
import com.dose.pay.android.ui.theme.PayColors
import com.dose.pay.android.ui.txnsheet.TxnSheet
import com.dose.pay.android.ui.txnsheet.TxnSheetDefaults

/**
 * 消费流水页（从看板进入的二级页）：三月指标条 + 按卡筛选 + 按天分组 + 左滑删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowScreen(
    vm: FlowViewModel,
    onGoCards: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.dose.pay.android.core.TxnRow?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(
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
        /* 顶栏 */
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("消费流水", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(PayColors.Secondary)
                    .clickable { showSheet = true }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("＋", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Primary)
                Text("记一笔", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PayColors.Primary)
            }
        }

        /* ① 三月指标条 */
        if (!state.loading && state.errorMessage.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PayColors.Surface)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricCell("本月消费", state.metrics.spendText, PayColors.Accent, Modifier.weight(1f))
                MetricDivider()
                MetricCell("本月还款", state.metrics.repayText, PayColors.RepayGreen, Modifier.weight(1f))
                MetricDivider()
                MetricCell("净占用", state.metrics.netText, PayColors.Primary, Modifier.weight(1f))
            }
        }

        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("正在加载流水…", fontSize = 13.sp, color = PayColors.Hint)
                }
            }

            state.errorMessage.isNotEmpty() -> {
                ErrorStateBox(title = "⚠️ 未能加载流水", description = state.errorMessage)
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
                /* ② 筛选横滑 */
                if (state.filters.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterChip(
                            label = "全部",
                            initial = "全",
                            color = if (state.selectedId.isEmpty()) PayColors.Primary else PayColors.ChipGrayBg,
                            active = state.selectedId.isEmpty(),
                            onClick = { vm.selectFilter("") },
                        )
                        state.filters.forEach { f ->
                            FilterChip(
                                label = f.short,
                                initial = f.initial,
                                color = f.color,
                                active = state.selectedId == f.idStr,
                                onClick = { vm.selectFilter(f.idStr) },
                            )
                        }
                    }
                }

                if (state.groups.isNotEmpty()) {
                    state.groups.forEach { group ->
                        Text(
                            group.dayText + " " + group.weekday,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PayColors.TextMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                        group.rows.forEach { row ->
                            SwipeRevealRow(
                                modifier = Modifier.fillMaxWidth(),
                                onDelete = { pendingDelete = row },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    Avatar(row.initial, row.color, size = 22.dp, fontSize = 11)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Text(
                                                row.paymentMethodName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            PayChip(
                                                text = row.type,
                                                style = if (row.isRepay) {
                                                    com.dose.pay.android.core.ChipStyle.GREEN
                                                } else {
                                                    com.dose.pay.android.core.ChipStyle.ACCENT
                                                },
                                            )
                                        }
                                        Text(row.subText, fontSize = 11.sp, color = PayColors.Hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        row.amountText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.isRepay) PayColors.RepayGreen else PayColors.Text,
                                    )
                                }
                            }
                        }
                    }

                    if (state.loadingMore) {
                        HintText("正在加载更多…", modifier = Modifier.fillMaxWidth())
                    } else if (state.hasMore) {
                        Text(
                            "上拉或点此加载更多流水 ›",
                            fontSize = 11.sp,
                            color = PayColors.Primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.loadMore() }
                                .padding(top = 6.dp),
                        )
                    }
                    HintText(
                        "共 " + state.count + " 笔 · 左滑流水删除（余额回滚）· 汇总随筛选联动",
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFA09CAC),
                    )
                } else {
                    EmptyStateBox(
                        title = "还没有消费流水",
                        description = if (state.selectedId.isNotEmpty()) {
                            "该卡暂无流水"
                        } else {
                            "在「计算」页记一笔，或点右上角「＋ 记一笔」"
                        },
                    )
                    PrimaryButton(text = "＋ 记一笔", modifier = Modifier.padding(top = 8.dp)) { showSheet = true }
                    Text(
                        "去添加卡片 ›",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGoCards() }
                            .padding(top = 10.dp),
                    )
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
                    "删除后 " + target.paymentMethodName + " 余额将回滚 " + Money.money(target.amount) +
                        "，确定删除这笔「" + target.type + "」吗？",
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
                TextButton(onClick = { pendingDelete = null }) { Text("取消", color = PayColors.TextMuted) }
            },
        )
    }

    /* 记一笔弹层（不预选卡） */
    TxnSheet(
        visible = showSheet,
        methods = state.methodList,
        defaults = TxnSheetDefaults(
            methodId = null,
            type = Txns.TYPE_SPEND,
            amount = "",
            date = com.dose.pay.android.core.Dates.today(),
            lockMethod = false,
        ),
        onDismiss = { showSheet = false },
        onSubmit = { body -> vm.createTransaction(body) },
        onSubmitted = { tx ->
            vm.onTxnCreated(tx.paymentMethodName)
            vm.load()
        },
    )
}

/** 指标格 */
@Composable
private fun MetricCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = PayColors.Hint)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

/** 指标分隔线 */
@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 26.dp)
            .background(PayColors.LineSoft),
    )
}

/** 筛选 chip */
@Composable
private fun FilterChip(label: String, initial: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) color else PayColors.ChipGrayBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else PayColors.ChipGrayText,
            )
        }
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) PayColors.Primary else PayColors.Hint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
