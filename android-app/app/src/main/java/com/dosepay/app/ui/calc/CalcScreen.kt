package com.dosepay.app.ui.calc

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dosepay.app.core.Txns
import com.dosepay.app.ui.components.Avatar
import com.dosepay.app.ui.components.EmptyStateBox
import com.dosepay.app.ui.components.ErrorStateBox
import com.dosepay.app.ui.components.GradientAmountField
import com.dosepay.app.ui.components.HintText
import com.dosepay.app.ui.components.KeyValueRow
import com.dosepay.app.ui.components.PayCard
import com.dosepay.app.ui.components.PayChip
import com.dosepay.app.ui.components.PayDatePickerDialog
import com.dosepay.app.ui.components.PrimaryButton
import com.dosepay.app.ui.components.SectionDivider
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.txnsheet.TxnSheet
import com.dosepay.app.ui.txnsheet.TxnSheetDefaults
import com.dosepay.app.ui.theme.Radii

/**
 * 计算页（Tab1）：金额 + 支付日期 -> 推荐结果。
 */
@Composable
fun CalcScreen(
    vm: CalcViewModel,
    onGoCards: () -> Unit,
    onGoDashboard: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        /* ① 金额输入卡（紫色渐变） */
        GradientAmountField(
            label = "本次支付金额",
            displayText = state.amountDisplay,
            inputText = state.amountInput,
            focused = state.amountFocused,
            onRequestFocus = { vm.onAmountFocus() },
            onValueChange = { vm.onAmountInput(it) },
            onBlur = { vm.onAmountBlur() },
            onClear = { vm.onClearAmount() },
        )

        /* ② 支付日期 */
        PayCard(
            modifier = Modifier.clickable { showDatePicker = true },
            contentPadding = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("📅 支付日期", fontSize = 14.sp, color = PayColors.TextMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.paymentDateText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
                    PayChip(text = state.paymentWeekday, style = ChipStyle.PRIMARY)
                    Text("▾", fontSize = 12.sp, color = PayColors.Care)
                }
            }
        }

        /* ③ 主按钮 */
        PrimaryButton(
            text = if (state.status == ResultStatus.LOADING) "正在计算…" else "生成前三名推荐",
            enabled = state.status != ResultStatus.LOADING,
        ) { vm.generate() }

        /* ④ 记一笔：常驻入口，与推荐结果无关，随时可记 */
        PrimaryButton(
            text = "✏️ 记一笔",
            background = PayColors.Accent,
        ) { showSheet = true }

        /* 后端不可用提示 */
        if (state.methodsError.isNotEmpty() && state.status != ResultStatus.READY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFFFF6F1))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    state.methodsError,
                    fontSize = 12.sp,
                    color = PayColors.AccentText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "重试",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayColors.Primary,
                    modifier = Modifier.clickable { vm.loadMethods() },
                )
            }
        }

        /* ⑤ 结果区 */
        when (state.status) {
            ResultStatus.LOADING -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("正在按资金占用天数排序…", fontSize = 13.sp, color = PayColors.Hint)
                }
            }

            ResultStatus.EMPTY -> {
                EmptyStateBox(
                    title = "计算结果将展示在此处",
                    description = "输入金额与日期，找出占用资金最久的付款方式",
                )
            }

            ResultStatus.ERROR -> {
                ErrorStateBox(
                    title = "⚠️ 无法生成推荐",
                    description = state.errorMessage,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "去调整卡片额度",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Primary,
                        modifier = Modifier.clickable { onGoCards() },
                    )
                    Text(" · ", fontSize = 13.sp, color = PayColors.Care)
                    Text(
                        "重新计算",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Primary,
                        modifier = Modifier.clickable { vm.generate() },
                    )
                }
            }

            ResultStatus.READY -> {
                val top = state.topOne
                if (top != null) {
                    /* ① 详细卡：Top1 的完整信息，放在「推荐结果」列表上方 */
                    PayCard(
                        radius = Radii.Large,
                        background = Color(0xFFF5F2FF),
                        contentPadding = 12.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 头（头像 + 名称 + 第1名）
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Avatar(top.initial, top.color, size = 32.dp, fontSize = 15)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            top.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        PayChip(text = "第 1 名", style = ChipStyle.ACCENT)
                                    }
                                    HintText(top.ruleText)
                                }
                            }

                            // 大数字 + 预计还款
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        top.occupiedDays.toString(),
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PayColors.Text,
                                    )
                                    Text(
                                        " 天 资金占用",
                                        fontSize = 12.sp,
                                        color = PayColors.Hint,
                                        modifier = Modifier.padding(bottom = 5.dp),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("预计还款", fontSize = 12.sp, color = Color(0xFF6B6774))
                                    Text(
                                        top.dueDateText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF26232C),
                                    )
                                }
                            }

                            KeyValueRow("支付前已用", top.currentUsedText)
                            KeyValueRow("支付后已用", top.afterUsedText + " / " + top.limitShow)
                            KeyValueRow("支付后可用额度", top.remainingAfterText)

                            HintText(top.reason, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    /* ② 推荐结果：三条统一紧凑行（点任意一条把它顶到首位，上方详情卡同步） */
                    SectionDivider(title = "推荐结果", sub = "按占用天数降序", modifier = Modifier.padding(top = 4.dp))

                    state.results.forEach { item ->
                        PayCard(
                            modifier = Modifier.clickable { vm.promote(item.rank) },
                            contentPadding = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PayColors.Secondary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        item.rank.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PayColors.Primary,
                                    )
                                }
                                Avatar(item.initial, item.color, size = 22.dp, fontSize = 11)
                                Text(
                                    item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    item.dueShort + " 还款",
                                    fontSize = 11.sp,
                                    color = PayColors.Hint,
                                )
                                Text(
                                    item.occupiedDays.toString() + " 天",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PayColors.Primary,
                                )
                                Text("›", fontSize = 14.sp, color = PayColors.Care)
                            }
                        }
                    }

                    HintText(
                        state.resultCountText + " · " + state.filteredNote,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = Color(0xFFA09CAC),
                    )
                    Text(
                        "查看额度看板（已用/可用/超限）›",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.Primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGoDashboard() }
                            .padding(top = 4.dp),
                    )
                }
            }
        }

        HintText(
            "结果仅供参考 · 节假日还款日可能顺延",
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            color = Color(0xFFA09CAC),
        )
    }

    if (showDatePicker) {
        PayDatePickerDialog(
            initial = state.paymentDate,
            onDismiss = { showDatePicker = false },
            onPicked = { vm.onDatePicked(it) },
        )
    }

    TxnSheet(
        visible = showSheet,
        methods = state.methodList,
        defaults = TxnSheetDefaults(
            methodId = state.topOne?.id,
            type = Txns.TYPE_SPEND,
            amount = state.amountInput,
            date = state.paymentDate,
            lockMethod = false,
        ),
        onDismiss = { showSheet = false },
        onSubmit = { body -> vm.createTransaction(body) },
        onSubmitted = { tx ->
            vm.onTxnSubmitted()
            vm.onTxnCreated(tx.paymentMethodName)
        },
    )
}
