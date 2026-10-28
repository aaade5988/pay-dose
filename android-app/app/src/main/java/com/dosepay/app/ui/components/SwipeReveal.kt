package com.dosepay.app.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.ui.theme.PayColors

/**
 * 左滑露出操作按钮的列表行（对齐原小程序的 swipe-item）。
 *
 * 账单页用 [actionLabel] =「删除」，看板周期卡用「已还清」，
 * 颜色走 [actionColor]，所以这里不写死「删除」语义。
 *
 * @param onAction 点击露出后的操作按钮
 * @param onContentClick 点击内容区（已展开时先收起）
 * @param content 行内容
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    actionWidthDp: Int = 75,
    actionLabel: String = "删除",
    actionColor: Color = PayColors.Accent,
    onAction: () -> Unit,
    onContentClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { (actionWidthDp.dp).toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val spec: FiniteAnimationSpec<Float> = if (dragging) snap() else tween(180)
    val animatedOffset by animateFloatAsState(targetValue = offsetX, animationSpec = spec, label = "swipeOffset")

    Box(
        modifier = modifier
            .fillMaxWidth()
            // IntrinsicSize.Min：把行高变成「有界」约束。
            // 列表在 verticalScroll 里高度约束是无穷的，直接 fillMaxHeight 会退化成 0 高度，
            // 删除按钮就成了「有宽度没高度」——左滑时右侧永远是一片空白。
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(13.dp))
    ) {
        // 底层：操作按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidthDp.dp)
                .fillMaxHeight()
                .background(actionColor)
                .clickable {
                    offsetX = 0f
                    onAction()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // 上层：内容（可左滑）
        // 顺序要紧：graphicsLayer 必须在 background 之前。
        // 反过来的话白色底留在原位不动（只有文字被平移），会把底下的删除按钮整块盖住。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffset }
                .background(PayColors.Surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            offsetX = if (offsetX < -actionWidthPx / 2f) -actionWidthPx else 0f
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                    )
                }
                .clickable {
                    if (offsetX < 0f) offsetX = 0f else onContentClick()
                },
        ) {
            content()
        }
    }
}
