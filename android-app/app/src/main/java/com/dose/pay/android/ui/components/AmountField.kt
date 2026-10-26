package com.dose.pay.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dose.pay.android.ui.theme.PayColors

/**
 * 紫色渐变金额输入卡（计算页主输入区 / 更新已用弹层共用）。
 *
 * 逐键清洗由调用方完成（onValueChange 内），这里只负责展示与焦点管理。
 */
@Composable
fun GradientAmountField(
    label: String,
    displayText: String,
    inputText: String,
    focused: Boolean,
    onRequestFocus: () -> Unit,
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
    }

    val gradient = Brush.linearGradient(listOf(PayColors.PrimaryDeep, PayColors.Primary))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .clickable { onRequestFocus() }
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "¥",
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (focused) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus) {
                                    hadFocus = false
                                    onBlur()
                                }
                            },
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = if (big) 32.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (inputText.isEmpty()) {
                                    Text(
                                        "0.00",
                                        fontSize = if (big) 32.sp else 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White.copy(alpha = 0.45f),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                } else {
                    Text(
                        displayText,
                        fontSize = if (big) 32.sp else 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = if (displayText == "0.00") 0.55f else 1f),
                    )
                }
            }
            if (focused && inputText.isNotEmpty() && onClear != null) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Text(
            if (focused) "仅支持数字，最多两位小数" else "点击输入金额 · 自动弹出数字键盘",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
