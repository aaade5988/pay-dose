package com.dose.pay.android.ui.txnsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dose.pay.android.core.Dates
import com.dose.pay.android.core.Methods
import com.dose.pay.android.core.Money
import com.dose.pay.android.core.Txns
import com.dose.pay.android.data.ApiError
import com.dose.pay.android.data.MethodDto
import com.dose.pay.android.data.TxnDto
import com.dose.pay.android.data.TxnInput
import com.dose.pay.android.ui.components.Avatar
import com.dose.pay.android.ui.components.PayDatePickerDialog
import com.dose.pay.android.ui.components.PrimaryButton
import com.dose.pay.android.ui.components.SegControl
import com.dose.pay.android.ui.theme.PayColors
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 记账弹层的预填参数 */
data class TxnSheetDefaults(
    val methodId: Long? = null,
    val type: String = Txns.TYPE_SPEND,
    val amount: String = "",
    val date: LocalDate = Dates.today(),
    val lockMethod: Boolean = false,
)

/**
 * 记一笔半屏弹层（计算页 / 看板 / 流水页三入口共用）。
 *
 * 金额与占用% 全部走「分」整数运算；还款超过当前已用时前端直接拦截。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnSheet(
    visible: Boolean,
    methods: List<MethodDto>,
    defaults: TxnSheetDefaults,
    onDismiss: () -> Unit,
    onSubmit: suspend (TxnInput) -> TxnDto,
    onSubmitted: (TxnDto) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var type by remember(defaults) { mutableStateOf(defaults.type) }
    var methodId by remember(defaults) { mutableStateOf(defaults.methodId?.toString() ?: "") }
    var amount by remember(defaults) { mutableStateOf(Money.normalizeAmountInput(defaults.amount) ?: "") }
    var date by remember(defaults) { mutableStateOf(defaults.date) }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var amountFocused by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }

    // 默认卡片：优先取预选卡，否则取第一张
    LaunchedEffect(defaults, methods) {
        if (methodId.isEmpty() && methods.isNotEmpty()) methodId = methods.first().id.toString()
    }

    val selectedCard = methods.firstOrNull { it.id.toString() == methodId }
    val amountCents = Money.toCents(amount) ?: 0L
    val preview = buildPreview(selectedCard, type, amount)
    val overLimit = preview?.overLimit == true
    val canSubmit = selectedCard != null && amountCents > 0L && !overLimit && !submitting

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
                Text("记一笔", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PayColors.Bg)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = PayColors.Care, fontSize = 14.sp) }
            }

            // 消费 / 还款
            SegControl(
                items = Txns.TXN_TYPES,
                selectedIndex = if (type == Txns.TYPE_REPAY) 1 else 0,
                onSelect = { index ->
                    type = if (index == 1) Txns.TYPE_REPAY else Txns.TYPE_SPEND
                    errorMessage = ""
                },
            )

            // 卡选择（横向滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (methods.isEmpty()) {
                    Text(
                        "还没有支付方式，请先到「卡片」页添加",
                        fontSize = 12.sp,
                        color = PayColors.Hint,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                methods.forEach { card ->
                    val on = card.id.toString() == methodId
                    Column(
                        modifier = Modifier.clickable {
                            if (defaults.lockMethod) {
                                errorMessage = "已锁定本卡，如需换卡请先返回"
                            } else {
                                methodId = card.id.toString()
                                errorMessage = ""
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier = if (on) {
                                Modifier.size(38.dp).border(2.dp, PayColors.Primary, RoundedCornerShape(19.dp))
                            } else {
                                Modifier.size(38.dp)
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Avatar(
                                text = Methods.nameInitial(card.name),
                                color = Methods.avatarColor(card.name),
                                size = 30.dp,
                                fontSize = 13,
                            )
                        }
                        Text(
                            Txns.shortName(card.name) + if (on) " ✓" else "",
                            fontSize = 11.sp,
                            color = if (on) PayColors.Primary else PayColors.Hint,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (defaults.lockMethod && selectedCard != null) {
                Text("已锁定：" + selectedCard.name, fontSize = 11.sp, color = PayColors.Hint)
            }

            // 金额
            SheetField(
                label = "金额",
                onClick = { amountFocus.requestFocus() },
                focused = amountFocused,
            ) {
                Text(
                    "¥",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (amountFocused) PayColors.Primary else PayColors.TextMuted,
                )
                SheetInput(
                    value = amount,
                    onValueChange = {
                        amount = Money.sanitizeAmountInput(it)
                        errorMessage = ""
                    },
                    placeholder = "0.00",
                    focusRequester = amountFocus,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onFocusChange = { amountFocused = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // 日期
            SheetField(label = "日期", onClick = { showDatePicker = true }) {
                Text(Dates.toStr(date), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
                Text("▾", fontSize = 12.sp, color = PayColors.Care)
            }

            // 备注
            // 注意：这里刻意不设 imeAction / keyboardActions。中文输入法（搜狗/百度/Gboard 拼音）
            // 会把 Enter 或「完成」当作提交候选词，一旦被 clearFocus 抢走，组词直接作废——
            // 表现就是「拼音打得出来，字进不去」。保持最简单的文本输入行为最稳。
            SheetField(
                label = "备注",
                onClick = { noteFocus.requestFocus() },
                focused = noteFocused,
            ) {
                SheetInput(
                    value = note,
                    onValueChange = { if (it.length <= 50) note = it },
                    placeholder = "如：数码产品（可选）",
                    focusRequester = noteFocus,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    onFocusChange = { noteFocused = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // 余额预览
            if (preview != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (overLimit) Color(0xFFFFF6F4) else Color(0xFFFAF9F5))
                        .border(
                            1.dp,
                            if (overLimit) Color(0xFFF3B4AC) else Color(0xFFE2DFD6),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(preview.line, fontSize = 11.sp, color = PayColors.Hint, lineHeight = 17.sp)
                    if (overLimit) {
                        Text(
                            "还款金额不得超过「" + preview.name + "」当前已用 " + preview.overText,
                            fontSize = 12.sp,
                            color = Color(0xFFD0342C),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, fontSize = 12.sp, color = Color(0xFFD0342C), lineHeight = 17.sp)
            }

            PrimaryButton(
                text = if (submitting) "正在记账…" else "确认记账",
                enabled = canSubmit,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (submitting) return@PrimaryButton
                if (selectedCard == null) {
                    errorMessage = "请先在「卡片」页添加支付方式"
                    return@PrimaryButton
                }
                if (amountCents <= 0L) {
                    errorMessage = "请输入大于 0 的金额"
                    return@PrimaryButton
                }
                if (type == Txns.TYPE_REPAY && amountCents > (Money.toCents(selectedCard.currentUsed) ?: 0L)) {
                    errorMessage = "还款金额不得超过「" + selectedCard.name + "」当前已用 " +
                        Money.money(selectedCard.currentUsed)
                    return@PrimaryButton
                }
                submitting = true
                errorMessage = ""
                val body = TxnInput(
                    paymentMethodId = selectedCard.id,
                    type = type,
                    amount = Money.fromCents(amountCents),
                    txnDate = Dates.toStr(date),
                    note = note,
                )
                scope.launch {
                    try {
                        val tx = onSubmit(body)
                        submitting = false
                        onSubmitted(tx)
                        onDismiss()
                    } catch (e: ApiError) {
                        submitting = false
                        errorMessage = e.message
                    } catch (e: Exception) {
                        submitting = false
                        errorMessage = e.message ?: "记账失败，请重试"
                    }
                }
            }

            Text(
                "切「还款」时金额不得超过已用 · 卡/金额/日期均可改",
                fontSize = 10.sp,
                color = Color(0xFFA09CAC),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        PayDatePickerDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = { date = it },
        )
    }
}

/** 余额预览模型 */
private data class Preview(
    val name: String,
    val line: String,
    val overLimit: Boolean,
    val overText: String,
)

/** 计算余额预览文案（分整数运算） */
private fun buildPreview(card: MethodDto?, type: String, amount: String): Preview? {
    if (card == null) return null
    val delta = Money.toCents(amount)?.takeIf { it >= 0L } ?: 0L
    val isRepay = type == Txns.TYPE_REPAY
    val usedC = Money.toCents(card.currentUsed) ?: 0L
    val afterC = if (isRepay) usedC - delta else usedC + delta
    val overLimit = isRepay && delta > usedC
    val limitC = Money.toCents(card.creditLimit)
    val beforePct = Money.percentOfCents(usedC, limitC)
    val afterPct = Money.percentOfCents(if (overLimit) usedC else afterC, limitC)
    val pctText = if (limitC == null) "" else beforePct.toString() + "%→" + afterPct.toString() + "%"
    val tailText = if (isRepay) "还款即冲减已用" else "消费允许超限"
    val line = "余额预览 · " + card.name + " 已用 " + Money.money(Money.fromCents(usedC)) +
        " → " + Money.money(Money.fromCents(if (overLimit) usedC else afterC)) +
        (if (pctText.isNotEmpty()) "（占用 " + pctText + "）· " else " · 不限额 · ") + tailText
    return Preview(
        name = card.name,
        line = line,
        overLimit = overLimit,
        overText = Money.money(Money.fromCents(usedC)),
    )
}

/** 弹层内的一行表单 */
@Composable
private fun SheetField(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    focused: Boolean = false,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFFFBF9FF) else PayColors.Surface)
            .border(
                width = 1.dp,
                color = if (focused) PayColors.Primary else PayColors.LineSoft,
                shape = RoundedCornerShape(8.dp),
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) PayColors.Primary else Color(0xFF43404C),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/**
 * 行内单行输入（金额 / 备注共用）。
 *
 * 关键点：`BasicTextField` 内部的文本布局宽度会收缩到文字宽度，
 * 所以只在 [TextStyle] 上写 `textAlign = End` 并不能真的右对齐——光标会停在行首，
 * 和右侧的占位符各站一边，看起来像「写不进去」。这里用一层
 * `Box(fillMaxWidth, contentAlignment = CenterEnd)` 把文本布局顶到右端：
 * 光标和文字都贴右边，占位符也在同一条基线上。
 *
 * [focusRequester] 由调用方持有：整行（含左侧标签）点击都要落到同一个输入框。
 */
@Composable
private fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFocusChange: (Boolean) -> Unit = {},
) {
    // clipToBounds：备注写满一行时文本向左溢出，不裁会压到左侧标签上
    Box(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 占位符是输入框的兄弟节点，不走 decorationBox：
        // 少一层包裹，输入法组词时不会有额外的重新布局介入。
        if (value.isEmpty()) {
            Text(
                placeholder,
                fontSize = 13.sp,
                color = PayColors.Care,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(PayColors.Primary),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PayColors.Text,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
    }
}
