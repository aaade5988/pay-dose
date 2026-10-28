package com.dosepay.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.dosepay.app.core.ChipStyle
import com.dosepay.app.core.CycleRow
import com.dosepay.app.ui.components.EmptyStateBox
import com.dosepay.app.ui.components.ErrorStateBox
import com.dosepay.app.ui.components.PayCard
import com.dosepay.app.ui.components.PayChip
import com.dosepay.app.ui.components.PayPullToRefresh
import com.dosepay.app.ui.components.PrimaryButton
import com.dosepay.app.ui.components.RingGauge
import com.dosepay.app.ui.components.SectionDivider
import com.dosepay.app.ui.components.SwipeRevealRow
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.theme.Radii

/**
 * 额度看板页（Tab3）：本期聚合仪表盘 + 本账单周期逐卡 + 「余额详情」半屏。
 *
 * 全页统一「本期」口径：顶部环 = 本期消费合计 ÷ Σ已设额度，
 * 下方逐卡行 = 上一还款日 → 本还款日的消费，两者同源，数字必须能对上。
 * 跨周期累积的已用余额只在「余额详情」弹层里出现，不上环、不参与聚合。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onGoCards: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    /** 左滑「已还清」待确认的周期卡 */
    var settleTarget by remember { mutableStateOf<CycleRow?>(null) }

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
        when {
            state.loading && state.rows.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("正在加载额度数据…", fontSize = 13.sp, color = PayColors.Hint)
                }
            }

            state.errorMessage.isNotEmpty() -> {
                ErrorStateBox(title = "⚠️ 未能加载看板", description = state.errorMessage)
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
                    title = "看板还没有数据",
                    description = "先到「卡片」Tab 添加支付方式（额度/账单/还款规则）",
                )
                PrimaryButton(text = "去添加卡片", modifier = Modifier.padding(top = 8.dp)) { onGoCards() }
            }

            else -> {
                val gauge = state.gauge
                /* 周期接口没回来时不猜数：环走空轨、百分比显示 —，下面给出失败原因 */
                val gaugeReady = state.cycleError.isEmpty()

                /* ① 聚合仪表盘：本期消费合计 ÷ Σ已设额度（与下方逐卡本期消费同源） */
                PayCard(contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "本期额度占用 · 断开式仪表盘",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B6774),
                        )
                        PayChip(
                            text = gauge.limitedCount.toString() + "/" + gauge.count + " 设额度",
                            style = ChipStyle.GRAY,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RingGauge(
                            pct = if (gaugeReady) gauge.pct else null,
                            diameter = 148.dp,
                            strokeWidth = 14.dp,
                            frozenP = if (gaugeReady) gauge.frozenP else 0f,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (gaugeReady) gauge.pctText else "—",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PayColors.Text,
                                )
                                Text("本期待还占用率", fontSize = 11.sp, color = PayColors.Hint)
                                if (gaugeReady && gauge.hasLimited) {
                                    Text(
                                        gauge.usedText + " / " + gauge.limitText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PayColors.Primary,
                                    )
                                }
                                if (gaugeReady && gauge.hasSettled) {
                                    Text(
                                        "已还 " + gauge.settledText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PayColors.RepayGreen,
                                    )
                                }
                            }
                        }
                    }
                    if (gaugeReady && gauge.hasSettled) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LegendDot(color = PayColors.RepayGreen, text = "已还")
                            LegendDot(color = PayColors.Primary, text = "待还占用")
                        }
                    }
                    if (!gaugeReady) {
                        Text(
                            "⚠ 本期周期数据未取到：" + state.cycleError,
                            fontSize = 11.sp,
                            color = Color(0xFFD0342C),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                /* ② 本账单周期（上一还款日 → 本还款日） */
                SectionDivider(
                    title = "本账单周期",
                    sub = "按还款日归期，不是自然月",
                    modifier = Modifier.padding(top = 6.dp),
                )

                state.cycle.rows.forEach { cyc ->
                    /* 左滑 → 标记 / 取消「已还清」（人工标记，不写流水） */
                    SwipeRevealRow(
                        modifier = Modifier.fillMaxWidth(),
                        actionWidthDp = 90,
                        actionLabel = if (cyc.settled) "取消标记" else "已还清",
                        actionColor = if (cyc.settled) PayColors.ChipGrayText else PayColors.RepayGreen,
                        onAction = { settleTarget = cyc },
                    ) {
                        PayCard(contentPadding = 0.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp)
                                        .clip(RoundedCornerShape(Radii.Chip))
                                        .background(cyc.color),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        cyc.initial,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            cyc.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        PayChip(text = cyc.rangeText, style = ChipStyle.GRAY)
                                    }
                                    Text(
                                        if (cyc.emptyText.isNotEmpty()) {
                                            cyc.emptyText
                                        } else {
                                            "本期消费 " + cyc.spendCount + " 笔" +
                                                if (cyc.repayCount > 0) " · 已还 " + cyc.repayText else ""
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFF5C5865),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (cyc.settled) {
                                        // 已还清：不再显示金额，只显示状态
                                        PayChip(text = "已还清", style = ChipStyle.GREEN)
                                    } else {
                                        Text(
                                            cyc.netText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (cyc.netCents == 0L) PayColors.TextMuted else Color(0xFF3A3742),
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    PayChip(text = cyc.dueText, style = cyc.dueChip)
                                }
                            }
                        }
                    }
                }

            
            }
        }
    }
    }

    /* 左滑「已还清」确认弹窗 */
    /* 左滑「已还清」确认弹窗 */
    val settle = settleTarget
    if (settle != null) {
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = {
                Text(
                    if (settle.settled) "取消已还清 · " + settle.name else "标记已还清 · " + settle.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    if (settle.settled) {
                        "取消后「" + settle.name + "」本期消费（" + settle.spendText +
                            "）重新计入额度占用，仪表盘恢复正常颜色。"
                    } else {
                        "标记后「" + settle.name + "」本期这一行显示「已还清」而不是金额，" +
                            "仪表盘里它占的那一段（" + settle.spendText + "）会冻结成绿色、不再计入额度占用。" +
                            "\n\n这只是人工标记：不写还款流水、不动已用余额；标记在本期还款日后自动失效。"
                    },
                    fontSize = 14.sp,
                    color = PayColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settleTarget = null
                    vm.setSettled(settle.id, !settle.settled)
                }) {
                    Text(
                        if (settle.settled) "取消标记" else "确认标记",
                        color = if (settle.settled) PayColors.Text else PayColors.RepayGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { settleTarget = null }) { Text("取消", color = PayColors.TextMuted) }
            },
        )
    }
}

/** 仪表盘图例：色点 + 说明 */
@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color),
        )
        Text(text, fontSize = 11.sp, color = PayColors.TextMuted)
    }
}
