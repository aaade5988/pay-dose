package com.dosepay.app.ui.installment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.core.Calculator
import com.dosepay.app.core.InstallmentRow
import com.dosepay.app.core.Installments
import com.dosepay.app.core.Money
import com.dosepay.app.ui.components.PayCard
import com.dosepay.app.ui.components.PrimaryButton
import com.dosepay.app.ui.components.SegControl
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.txnsheet.SheetField
import com.dosepay.app.ui.txnsheet.SheetInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** 常用分期期数 */
private val PERIOD_PRESETS = listOf(3, 6, 12, 24)

/**
 * 账单分期弹层（看板周期卡左滑「分期」进入）。
 *
 * 交互只做三件事：
 * 1. 填分期金额（默认 = 本期待还，也可以只分一部分）；
 * 2. 选期数 + 计价口径 + 费率；
 * 3. 看清楚「每期还多少 / 总利息多少 / 折合年化多少」。
 *
 * **费率口径**决定金额怎么算：
 * - 「总费率」是信用卡账单分期的报价方式，每期本金与手续费都固定，与银行账单逐分对齐；
 * - 「年利率」是消费贷 / 分期通的报价方式，每期还款额固定、利息按剩余本金递减。
 *
 * 输入的是百分数（7.2 表示 7.2%），落库转成小数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentSheet(
    visible: Boolean,
    cardName: String,
    /** 本期待还（分）：分期金额的上限 */
    dueCents: Long,
    /** 该期已有的分期（重新编辑时回填）；没有则 null */
    initial: InstallmentRow?,
    /** 本期还款日 ISO：分期首期就落在这一天 */
    cycleEnd: String,
    onDismiss: () -> Unit,
    onSubmit: (principal: String, periods: Int, rateMode: String, rate: String) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var principal by remember(visible) {
        mutableStateOf(Money.fromCents(if (dueCents > 0L) dueCents else 0L))
    }
    var periodsIndex by remember(visible) { mutableStateOf(2) }
    // 默认「年利率」= 等额本息（每期还款固定、利息逐月递减），这是最常见的账单分期形态
    var modeIndex by remember(visible) { mutableStateOf(1) }
    // 三种口径各记各的输入：5.16 当总利息就是 5 块 1 毛 6，切过去必须换一套
    var rateTexts by remember(visible) {
        mutableStateOf(listOf("7.2", initial?.rateText?.removeSuffix("%") ?: "5.16", ""))
    }
    var errorMessage by remember { mutableStateOf("") }

    val periods = PERIOD_PRESETS[periodsIndex]
    val mode = when (modeIndex) {
        0 -> Installments.RateMode.FEE_RATE
        1 -> Installments.RateMode.ANNUAL_RATE
        else -> Installments.RateMode.TOTAL_INTEREST
    }
    val rateText = rateTexts[modeIndex]

    val principalDecimal = principal.toBigDecimalOrNull() ?: BigDecimal.ZERO
    // 「总利息」口径下 rate 直接是金额，不除以 100
    val rateDecimal = if (modeIndex == 2) {
        rateText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    } else {
        (rateText.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            .divide(BigDecimal(100), 8, RoundingMode.HALF_EVEN)
    }
    val firstDue = runCatching { LocalDate.parse(cycleEnd) }.getOrNull() ?: LocalDate.now()
    val schedule = Installments.build(principalDecimal, periods, mode, rateDecimal, firstDue)

    val overLimit = principalDecimal > BigDecimal(dueCents).divide(BigDecimal(100))
    val canSubmit = schedule != null && principalDecimal > BigDecimal.ZERO && !overLimit

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "账单分期 · " + cardName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PayColors.Text,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PayColors.Bg)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = PayColors.Care, fontSize = 14.sp) }
            }

            Text(
                "本期待还 " + Money.moneyCompact(Money.fromCents(dueCents)) +
                    " · 分期金额不能超过它",
                fontSize = 11.sp,
                color = PayColors.Hint,
            )

            // 分期金额
            SheetField(label = "分期金额", onClick = { }) {
                Text(
                    "¥",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayColors.TextMuted,
                )
                SheetInput(
                    value = principal,
                    onValueChange = {
                        principal = Money.sanitizeAmountInput(it)
                        errorMessage = ""
                    },
                    placeholder = "0.00",
                    focusRequester = remember { FocusRequester() },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onFocusChange = { },
                    modifier = Modifier.weight(1f),
                )
            }
            // 期数
            SheetField(label = "期数", onClick = { }) {
                SegControl(
                    items = PERIOD_PRESETS.map { it.toString() + " 期" },
                    selectedIndex = periodsIndex,
                    onSelect = { periodsIndex = it },
                )
            }

            // 计价口径
            SheetField(label = "计价口径", onClick = { }) {
                SegControl(
                    items = listOf("总费率", "年利率", "总利息"),
                    selectedIndex = modeIndex,
                    onSelect = {
                        modeIndex = it
                        errorMessage = ""
                    },
                )
            }

            // 费率 / 总利息
            SheetField(
                label = when (modeIndex) {
                    0 -> "总手续费率"
                    1 -> "年利率"
                    else -> "总利息"
                },
                onClick = { },
            ) {
                if (modeIndex == 2) {
                    Text(
                        "¥",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.TextMuted,
                    )
                }
                SheetInput(
                    value = rateText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { c -> c.isDigit() || c == '.' }.take(8)
                        rateTexts = rateTexts.toMutableList().also { it[modeIndex] = filtered }
                        errorMessage = ""
                    },
                    placeholder = when (modeIndex) {
                        0 -> "7.2"
                        1 -> "5.16"
                        else -> "36.79"
                    },
                    focusRequester = remember { FocusRequester() },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onFocusChange = { },
                    modifier = Modifier.weight(1f),
                )
                if (modeIndex != 2) {
                    Text(
                        "%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayColors.TextMuted,
                    )
                }
            }

            Text(
                when (modeIndex) {
                    0 -> "总费率按原始本金一次性收，每期本金与手续费都固定，和银行账单一致"
                    1 -> "年利率按剩余本金逐月计息，每期还款额固定"
                    else -> "填账单上的总利息，每期还款由「本金 + 总利息」均分，和账单逐分对齐"
                },
                fontSize = 10.sp,
                color = PayColors.Hint,
            )
            // 预览
            PayCard {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (schedule == null) {
                        Text(
                            "填入金额和费率后这里显示每期怎么还",
                            fontSize = 12.sp,
                            color = PayColors.Hint,
                        )
                    } else {
                        PreviewLine(
                            "每期还款",
                            Money.moneyCompact(Calculator.formatAmount(schedule.firstPayment)),
                            bold = true,
                        )
                        // 利息要显示「首期 → 末期」，否则等额本息看起来像每期固定
                        val firstInterest = schedule.items.first().interestPart
                        val lastInterest = schedule.items.last().interestPart
                        PreviewLine(
                            if (firstInterest.compareTo(lastInterest) == 0) "每期利息" else "利息首末",
                            if (firstInterest.compareTo(lastInterest) == 0) {
                                Money.moneyCompact(Calculator.formatAmount(firstInterest)) + "（固定）"
                            } else {
                                Money.moneyCompact(Calculator.formatAmount(firstInterest)) +
                                    " → " + Money.moneyCompact(Calculator.formatAmount(lastInterest))
                            },
                        )
                        PreviewLine(
                            "总利息",
                            Money.moneyCompact(Calculator.formatAmount(schedule.totalInterest)),
                        )
                        PreviewLine(
                            "折合年化",
                            (schedule.effectiveAnnualRate.multiply(BigDecimal(100))
                                .setScale(2, RoundingMode.HALF_EVEN).toPlainString()) + "%",
                            bold = true,
                        )
                        PreviewLine(
                            "冻结额度",
                            Money.moneyCompact(
                                Calculator.formatAmount(
                                    schedule.principal.subtract(schedule.items.first().principalPart),
                                ),
                            ),
                        )
                        Text(
                            "首期 " + schedule.items.first().dueDate +
                                " · 末期 " + schedule.items.last().dueDate,
                            fontSize = 10.sp,
                            color = PayColors.Hint,
                        )
                    }
                }
            }

            // 逐期明细：期数 / 利息 / 本金 / 剩余
            // 汇总数字看不出「利息在递减、本金在递增」，摊开成表最直观
            if (schedule != null) {
                PayCard {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "每期明细",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PayColors.Text,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TableCell("期", 0.6f, header = true, atStart = true)
                            TableCell("利息", 1f, header = true)
                            TableCell("还本金", 1f, header = true)
                            TableCell("剩余本金", 1.2f, header = true)
                        }
                        schedule.items.forEachIndexed { index, period ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TableCell("${index + 1}", 0.6f, atStart = true)
                                // 明细表统一保留两位小数：1,199.69 和 110 混排不齐
                                TableCell(
                                    Money.money(Calculator.formatAmount(period.interestPart)),
                                    1f,
                                )
                                TableCell(
                                    Money.money(Calculator.formatAmount(period.principalPart)),
                                    1f,
                                )
                                TableCell(
                                    Money.money(Calculator.formatAmount(period.endBalance)),
                                    1.2f,
                                )
                            }
                        }
                    }
                }
            }

            if (overLimit) {
                Text(
                    "分期金额不能超过本期待还 " +
                        Money.moneyCompact(Money.fromCents(dueCents)),
                    fontSize = 12.sp,
                    color = Color(0xFFD0342C),
                )
            } else if (errorMessage.isNotEmpty()) {
                Text(errorMessage, fontSize = 12.sp, color = Color(0xFFD0342C))
            }

            PrimaryButton(
                text = if (initial != null) "更新分期" else "确认分期",
                enabled = canSubmit,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                onSubmit(principal, periods, mode, rateDecimal.toPlainString())
            }

            Text(
                "分期不写流水、不动已用额度：钱还欠着，只是把本期待还摊到未来各期",
                fontSize = 10.sp,
                color = PayColors.Hint,
            )
            // __PART2__
        }
    }
}
/** 明细表的一格：按权重分列，默认右对齐（金额），期数那列左对齐 */
@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    atStart: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        fontSize = 11.sp,
        textAlign = if (atStart) TextAlign.Start else TextAlign.End,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = if (header) PayColors.Hint else PayColors.Text,
    )
}

/** 预览区的一行：左边标签、右边数值 */
@Composable
private fun PreviewLine(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = PayColors.Hint)
        Text(
            value,
            fontSize = if (bold) 14.sp else 12.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            color = PayColors.Text,
        )
    }
}
