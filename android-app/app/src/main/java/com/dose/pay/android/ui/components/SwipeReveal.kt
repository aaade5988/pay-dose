package com.dose.pay.android.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.dose.pay.android.ui.theme.PayColors

/**
 * 左滑露出删除按钮的列表行（对齐原小程序的 swipe-item）。
 *
 * @param onDelete 点击露出后的「删除」按钮
 * @param onContentClick 点击内容区（已展开时先收起）
 * @param content 行内容
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    actionWidthDp: Int = 75,
    deleteLabel: String = "删除",
    onDelete: () -> Unit,
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
            .clip(RoundedCornerShape(13.dp))
    ) {
        // 底层：删除按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidthDp.dp)
                .fillMaxHeight()
                .background(PayColors.Accent)
                .clickable {
                    offsetX = 0f
                    onDelete()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(deleteLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // 上层：内容（可左滑）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PayColors.Surface)
                .graphicsLayer { translationX = animatedOffset }
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
