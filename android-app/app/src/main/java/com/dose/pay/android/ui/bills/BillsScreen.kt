package com.dose.pay.android.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dose.pay.android.core.BillMonth
import com.dose.pay.android.core.BillRow
import com.dose.pay.android.core.ChipStyle
import com.dose.pay.android.core.Money
import com.dose.pay.android.ui.components.Avatar
import com.dose.pay.android.ui.components.EmptyStateBox
import com.dose.pay.android.ui.components.ErrorStateBox
import com.dose.pay.android.ui.components.HintText
import com.dose.pay.android.ui.components.PayChip
import com.dose.pay.android.ui.components.Radii
import com.dose.pay.android.ui.components.SwipeRevealRow
import com.dose.pay.android.ui.theme.PayColors

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

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.load() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("账单", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
                    Text(
                        "按月倒序 · 每月全部消费明细",
                        fontSize = 12.sp,
                        color = PayColors.Hint,
                    )
                }
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
                        HintText(
                            "共 " + state.overview.monthCount + " 个月 · " + state.overview.txnCount +
                                " 笔流水 · 左滑单笔可删除（余额回滚）",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFA09CAC),
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

/** 顶部总览：只显示消费合计金额（月份 / 笔数 / 还款都不在这里统计） */
@Composable
private fun OverviewCard(
    spendText: String,
    filtered: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(Brush.linearGradient(listOf(PayColors.PrimaryDeep, PayColors.Primary)))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            if (filtered) "该卡消费合计" else "全部消费合计",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.72f),
        )
        Text(
            spendText,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 卡筛选圆点 */
@Composable
private fun FilterDot(
    label: String,
    initial: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
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

/**
 * 月卡：标题（月份 + 消费合计 · 笔数）可点击折叠，展开后是当月明细。
 */
@Composable
private fun MonthCard(
    month: BillMonth,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDelete: (BillRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(PayColors.Surface),
    ) {
        /* 月标题 */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PayColors.Primary),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    month.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PayColors.Text,
                )
                Text(
                    month.summaryText,
                    fontSize = 11.sp,
                    color = PayColors.Hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PayChip(
                text = if (collapsed) "展开" else "收起",
                style = ChipStyle.GRAY,
            )
        }

        if (!collapsed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PayColors.LineSoft),
            )
            month.rows.forEach { row ->
                SwipeRevealRow(
                    modifier = Modifier.fillMaxWidth(),
                    actionLabel = "删除",
                    onAction = { onDelete(row) },
                ) {
                    BillRowView(row)
                }
            }
        }
    }
}

/**
 * 明细行：字头色圆 + 卡名/备注 + 类型 chip + 金额 + 时间。
 *
 * 消费金额深色 `−¥`，还款绿色 `+¥`。
 */
@Composable
private fun BillRowView(row: BillRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Avatar(row.initial, row.color, size = 24.dp, fontSize = 11)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    row.paymentMethodName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PayChip(
                    text = row.type,
                    style = if (row.isRepay) ChipStyle.GREEN else ChipStyle.ACCENT,
                )
            }
            Text(
                row.noteText,
                fontSize = 12.sp,
                color = PayColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.amountText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.isRepay) PayColors.RepayGreen else PayColors.Text,
            )
            Text(
                row.dateText,
                fontSize = 11.sp,
                color = PayColors.Hint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
