package com.dosepay.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.core.BillMonth
import com.dosepay.app.core.BillRow
import com.dosepay.app.core.ChipStyle
import com.dosepay.app.ui.components.Avatar
import com.dosepay.app.ui.components.PayChip
import com.dosepay.app.ui.components.SwipeAction
import com.dosepay.app.ui.components.SwipeRevealRow
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.theme.Radii

/** 顶部总览：只显示消费合计金额（月份 / 笔数 / 还款都不在这里统计） */
@Composable
internal fun OverviewCard(
    spendText: String,
    filtered: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(Brush.linearGradient(listOf(PayColors.PrimaryDeep, PayColors.Primary)))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            if (filtered) "该卡消费合计" else "全部消费合计",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.72f),
        )
        Text(
            spendText,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 卡筛选圆点 */
@Composable
internal fun FilterDot(
    label: String,
    initial: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) color else PayColors.ChipGrayBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else PayColors.ChipGrayText,
            )
        }
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) PayColors.Primary else PayColors.Hint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 月卡：标题（月份 + 消费合计 · 笔数）可点击折叠，展开后是当月明细。
 */
@Composable
internal fun MonthCard(
    month: BillMonth,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDelete: (BillRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(PayColors.Surface),
    ) {
        /* 月标题 */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PayColors.Primary),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    month.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PayColors.Text,
                )
                Text(
                    month.summaryText,
                    fontSize = 11.sp,
                    color = PayColors.Hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PayChip(
                text = if (collapsed) "展开" else "收起",
                style = ChipStyle.GRAY,
            )
        }

        if (!collapsed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PayColors.LineSoft),
            )
            month.rows.forEach { row ->
                SwipeRevealRow(
                    modifier = Modifier.fillMaxWidth(),
                    actionWidthDp = 75,
                    actions = listOf(
                        SwipeAction("删除", PayColors.Accent) { onDelete(row) },
                    ),
                ) {
                    BillRowView(row)
                }
            }
        }
    }
}

/**
 * 明细行：字头色圆 + 卡名/备注 + 类型 chip + 金额 + 时间。
 *
 * 消费金额深色 `−¥`，还款绿色 `+¥`。
 */
@Composable
internal fun BillRowView(row: BillRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Avatar(row.initial, row.color, size = 24.dp, fontSize = 11)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    row.paymentMethodName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PayChip(
                    text = row.type,
                    style = if (row.isRepay) ChipStyle.GREEN else ChipStyle.ACCENT,
                )
            }
            Text(
                row.noteText,
                fontSize = 12.sp,
                color = PayColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.amountText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.isRepay) PayColors.RepayGreen else PayColors.Text,
            )
            Text(
                row.dateText,
                fontSize = 11.sp,
                color = PayColors.Hint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
