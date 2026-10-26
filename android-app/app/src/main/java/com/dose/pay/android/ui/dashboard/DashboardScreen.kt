package com.dose.pay.android.ui.dashboard

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dose.pay.android.core.ChipStyle
import com.dose.pay.android.core.Dates
import com.dose.pay.android.core.Money
import com.dose.pay.android.core.Txns
import com.dose.pay.android.ui.components.EmptyStateBox
import com.dose.pay.android.ui.components.ErrorStateBox
import com.dose.pay.android.ui.components.GapRing
import com.dose.pay.android.ui.components.HintText
import com.dose.pay.android.ui.components.OutlineButton
import com.dose.pay.android.ui.components.PayCard
import com.dose.pay.android.ui.components.PayChip
import com.dose.pay.android.ui.components.PrimaryButton
import com.dose.pay.android.ui.components.Radii
import com.dose.pay.android.ui.components.RingGauge
import com.dose.pay.android.ui.components.SectionDivider
import com.dose.pay.android.ui.theme.PayColors
import com.dose.pay.android.ui.txnsheet.TxnSheet
import com.dose.pay.android.ui.txnsheet.TxnSheetDefaults
import kotlinx.coroutines.launch

/**
 * 额度看板页（Tab3）：聚合仪表盘 + 单卡缺口环 + 「更新已用」半屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onGoFlow: () -> Unit,
    onGoCards: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var repayNote by remember { mutableStateOf<String?>(null) }

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
                /* ① 聚合仪表盘 */
                PayCard(contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "总额度占用 · 断开式仪表盘",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B6774),
                        )
                        PayChip(
                            text = state.agg.limitedCount.toString() + "/" + state.agg.count + " 设额度",
                            style = ChipStyle.GRAY,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RingGauge(pct = state.agg.pct, diameter = 148.dp, strokeWidth = 14.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    state.agg.pctText,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PayColors.Text,
                                )
                                Text("总占用率", fontSize = 11.sp, color = PayColors.Hint)
                                if (state.agg.hasLimited) {
                                    Text(
                                        state.agg.usedText + " / " + state.agg.limitText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PayColors.Primary,
                                    )
                                }
                            }
                        }
                    }
                    HintText(state.agg.subText, modifier = Modifier.fillMaxWidth())
                }

                /* ② 单卡行 */
                SectionDivider(
                    title = "单卡占用 · 缺口环形进度环",
                    sub = "点卡片更新已用",
                    modifier = Modifier.padding(top = 6.dp),
                )

                state.rows.forEach { row ->
                    PayCard(
                        modifier = Modifier.clickable { vm.openSheet(row.id) },
                        contentPadding = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GapRing(
                                pct = row.pct,
                                arc = row.ringP,
                                color = row.ringColor,
                                none = row.ringNone,
                                diameter = 54.dp,
                                strokeWidth = 7.dp,
                            ) {
                                Text(
                                    row.pctText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (row.ringNone) PayColors.Care else Color(0xFF3A3742),
                                )
                            }
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
                                    PayChip(
                                        text = row.methodType,
                                        style = ChipStyle.PRIMARY,
                                    )
                                }
                                Text(row.moneyLine, fontSize = 12.sp, color = Color(0xFF5C5865), modifier = Modifier.padding(top = 2.dp))
                                if (row.overText.isNotEmpty()) {
                                    Text(
                                        "⚠ " + row.overText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PayColors.RingBad,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            PayChip(text = row.statusLabel, style = row.statusChip)
                        }
                    }
                }

                HintText(
                    "环形按阈值变色：<50% 安全 · 50–79% 偏高 · ≥80% 吃紧 · 下拉刷新 GET /api/methods",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = Color(0xFFA09CAC),
                )
                Text(
                    "查看消费流水 ›",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayColors.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGoFlow() }
                        .padding(top = 2.dp),
                )
            }
        }
    }
    }

    /* ③ 更新已用 · 半屏弹层 */
    val sheet = state.sheet
    if (sheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.closeSheet() },
            sheetState = sheetState,
            containerColor = PayColors.Surface,
            dragHandle = { Spacer(Modifier.height(4.dp)) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "更新已用 · " + sheet.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PayColors.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "✕",
                        fontSize = 15.sp,
                        color = PayColors.Care,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.closeSheet() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // 当前余额 + 迷你环
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFAF9F5))
                        .padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    GapRing(
                        pct = sheet.pct,
                        arc = sheet.arc,
                        color = sheet.ringColor,
                        none = sheet.pct == null,
                        diameter = 40.dp,
                        strokeWidth = 5.dp,
                    ) {
                        Text(
                            sheet.pctText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (sheet.pct == null) PayColors.Care else Color(0xFF3A3742),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "当前 已用 " + sheet.usedText +
                                (if (sheet.hasLimit) " · 剩 " + sheet.availText + " / 额度 " + sheet.limitText else ""),
                            fontSize = 11.sp,
                            color = PayColors.Hint,
                            lineHeight = 17.sp,
                        )
                        HintText("改后即时重算聚合环 · 手改=对账修正（不产生流水）", color = Color(0xFFA09CAC))
                    }
                }

                // 新金额（可编辑）
                EditAmountRow(
                    label = "新的已用金额",
                    value = sheet.input,
                    onValueChange = { vm.onSheetInput(it) },
                )

                if (sheet.overText.isNotEmpty()) {
                    Text(
                        "⚠ " + sheet.overText + " · 超限允许，环形置满红",
                        fontSize = 11.sp,
                        color = Color(0xFFB3271C),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF6F4))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                if (sheet.error.isNotEmpty()) {
                    Text(sheet.error, fontSize = 12.sp, color = Color(0xFFD0342C))
                }

                // 快捷按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PayChip(text = "记一笔 →", onClick = { showSheet = true })
                    PayChip(
                        text = "按账单清零",
                        style = ChipStyle.GRAY,
                        onClick = { repayNote = "按账单清零" },
                    )
                    PayChip(
                        text = "记为已还清",
                        style = ChipStyle.GRAY,
                        onClick = { repayNote = "记为已还清" },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineButton(text = "取消", modifier = Modifier.weight(1f)) { vm.closeSheet() }
                    PrimaryButton(
                        text = if (sheet.saving) "保存中…" else "保存 · PUT /api/methods",
                        enabled = !sheet.saving,
                        modifier = Modifier.weight(2f),
                    ) { vm.saveSheet() }
                }

                HintText("「清零/还清」走还款流水（可追溯、可回滚）· 手改直接覆盖余额快照 · 超过额度不拦截")
            }
        }
    }

    /* 还款确认弹窗 */
    val note = repayNote
    if (note != null && sheet != null) {
        AlertDialog(
            onDismissRequest = { repayNote = null },
            title = { Text(note, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "将记一笔还款 " + Money.money(Money.fromCents(Money.toCents(sheet.currentUsed) ?: 0L)) +
                        "（" + note + "），「" + sheet.name + "」余额归零。还款会写入消费流水，可在流水页追溯/回滚。",
                    fontSize = 14.sp,
                    color = PayColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repayNote = null
                    vm.repayAll(note)
                }) { Text("确认还款", color = PayColors.Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { repayNote = null }) { Text("取消", color = PayColors.TextMuted) }
            },
        )
    }

    /* 看板内「记一笔」弹层（锁定本卡） */
    TxnSheet(
        visible = showSheet,
        methods = state.methodList,
        defaults = TxnSheetDefaults(
            methodId = sheet?.methodId,
            type = Txns.TYPE_SPEND,
            amount = "",
            date = Dates.today(),
            lockMethod = true,
        ),
        onDismiss = { showSheet = false },
        onSubmit = { body -> vm.createTransaction(body) },
        onSubmitted = { tx ->
            vm.onTxnCreated(tx.paymentMethodName)
            vm.closeSheet()
            vm.load()
        },
    )
}

/** 「更新已用」弹层里的可编辑金额行 */
@Composable
private fun EditAmountRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PayColors.Bg)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 11.sp, color = PayColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("¥", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PayColors.Primary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PayColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "0.00",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PayColors.Care,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}
