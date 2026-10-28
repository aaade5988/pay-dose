package com.dosepay.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dosepay.app.core.Methods
import com.dosepay.app.ui.theme.PayColors

/** 缺口环的起始角与总弧度（270° 弧，起点 135°） */
private const val START_ANGLE = 135f
private const val FULL_SWEEP = 270f

/**
 * 缺口环形进度环（看板单卡 / 计算页 Top1 / 更新弹层共用）。
 *
 * @param pct 占用率（百分比）；null 表示未设额度，走虚线灰环
 * @param arc Methods.ringArc(pct) 的结果（0..75）
 */
@Composable
fun GapRing(
    pct: Double?,
    arc: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 54.dp,
    strokeWidth: Dp = 7.dp,
    none: Boolean = false,
    center: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            if (none) {
                // 未设额度：虚线灰环
                val radius = (minOf(this.size.width, this.size.height) - stroke) / 2f
                val mid = Offset(this.size.width / 2f, this.size.height / 2f)
                val circle = Path().apply {
                    addOval(Rect(mid.x - radius, mid.y - radius, mid.x + radius, mid.y + radius))
                }
                drawPath(
                    path = circle,
                    color = PayColors.RingNoneTrack,
                    style = Stroke(
                        width = stroke * 0.7f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.1f, stroke * 1.1f)),
                    ),
                )
            } else {
                drawArc(
                    color = PayColors.RingTrack,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_SWEEP,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                val sweep = (arc / 100f) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = START_ANGLE,
                        sweepAngle = sweep.coerceAtMost(FULL_SWEEP),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
            }
        }
        center()
    }
}

/**
 * 断开式环形仪表盘（看板聚合）：270° 弧 + 每 18° 一条 1.8° 的刻度断口。
 *
 * [frozenP] 是「已还清」冻结段：固定画在弧的**起点**（环的左下角 = 左侧起点），
 * 用另一种颜色表示这部分钱已经还了、不再计入额度占用，但仍然占着环上的位置。
 *
 * 之所以锚在起点而不是「跟在主色弧后面」：跟在后面的话，占用率一变绿色段就跟着漂，
 * 同一张卡在别的时候看会是两个位置。锚在左端则永远只看一个地方。
 */
@Composable
fun RingGauge(
    pct: Double?,
    modifier: Modifier = Modifier,
    diameter: Dp = 148.dp,
    strokeWidth: Dp = 14.dp,
    frozenP: Float = 0f,
    frozenColor: Color = PayColors.RepayGreen,
    gapColor: Color = PayColors.Surface,
    center: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = PayColors.RingTrack,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            // 冻结段（已还清）：锚在弧的左端，位置固定
            val frozenSweep = (frozenP / 100f) * 360f
            val frozen = frozenSweep.coerceIn(0f, FULL_SWEEP)
            if (frozen > 0f) {
                drawArc(
                    color = frozenColor,
                    startAngle = START_ANGLE,
                    sweepAngle = frozen,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            // 主色弧（待还占用）：接在冻结段之后
            val sweep = pct?.let { (Methods.ringArc(it) / 100f) * 360f } ?: 0f
            if (sweep > 0f) {
                drawArc(
                    color = PayColors.Primary,
                    startAngle = START_ANGLE + frozen,
                    sweepAngle = sweep.coerceAtMost((FULL_SWEEP - frozen).coerceAtLeast(0f)),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            // 刻度断口：每 18° 切掉 1.8°（用卡片底色覆盖）
            val slotSweep = 1.8f
            val step = 18f
            var angle = START_ANGLE
            while (angle < START_ANGLE + FULL_SWEEP) {
                drawArc(
                    color = gapColor,
                    startAngle = angle,
                    sweepAngle = slotSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                angle += step
            }
        }
        center()
    }
}
