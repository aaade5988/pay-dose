package com.dosepay.app.ui.txnsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.core.Money
import com.dosepay.app.core.Txns
import com.dosepay.app.data.MethodDto
import com.dosepay.app.ui.theme.PayColors

/** 余额预览模型 */
internal data class Preview(
    val name: String,
    val line: String,
    val overLimit: Boolean,
    val overText: String,
)

/** 计算余额预览文案（分整数运算） */
internal fun buildPreview(card: MethodDto?, type: String, amount: String): Preview? {
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
internal fun SheetField(
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
internal fun SheetInput(
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
