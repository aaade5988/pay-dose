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
import androidx.compose.foundation.layout.Row
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
 * 左滑露出的一个操作按钮。
 *
 * 颜色走各自的 [color]，所以这里不写死任何语义（删除 / 已还清 / 分期）。
 */
data class SwipeAction(
    val label: String,
    val color: Color,
    val onClick: () -> Unit,
)

/**
 * 左滑露出操作按钮的列表行（对齐原小程序的 swipe-item）。
 *
 * 支持多个动作并排：看板周期卡用「已还清 + 分期」，
 * 两个动作同构（都作用于「这一期账单」），并排比藏在二级菜单里更好找。
 *
 * @param actions 露出的动作，按给定顺序从左到右排列；为空时不可左滑
 * @param onContentClick 点击内容区（已展开时先收起）
 * @param content 行内容
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    actionWidthDp: Int = 90,
    actions: List<SwipeAction> = emptyList(),
    onContentClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { (actionWidthDp.dp).toPx() }
    val totalWidthPx = actionWidthPx * actions.size
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
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width((actionWidthDp * actions.size).dp)
                .fillMaxHeight(),
        ) {
            actions.forEach { action ->
                Box(
                    modifier = Modifier
                        .width(actionWidthDp.dp)
                        .fillMaxHeight()
                        .background(action.color)
                        .clickable {
                            offsetX = 0f
                            action.onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
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
                            offsetX = if (offsetX < -totalWidthPx / 2f) -totalWidthPx else 0f
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-totalWidthPx, 0f)
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
