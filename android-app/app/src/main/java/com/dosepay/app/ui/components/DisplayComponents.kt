package com.dosepay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.core.ChipStyle
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.theme.Radii

/** 字头圆：首字 + 哈希色 */
@Composable
fun Avatar(
    text: String,
    color: Color,
    size: Dp = 32.dp,
    fontSize: Int = 15,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** chip：全圆角，按 [ChipStyle] 取配色 */
@Composable
fun PayChip(
    text: String,
    style: ChipStyle = ChipStyle.PRIMARY,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg: Color
    val fg: Color
    when {
        active -> {
            bg = PayColors.Primary
            fg = Color.White
        }
        style == ChipStyle.PRIMARY -> {
            bg = PayColors.Secondary
            fg = PayColors.Primary
        }
        style == ChipStyle.ACCENT -> {
            bg = PayColors.AccentSoft
            fg = PayColors.AccentText
        }
        style == ChipStyle.GREEN -> {
            bg = PayColors.GreenSoft
            fg = PayColors.RepayGreen
        }
        style == ChipStyle.RED -> {
            bg = PayColors.RedSoft
            fg = PayColors.RedText
        }
        else -> {
            bg = PayColors.ChipGrayBg
            fg = PayColors.ChipGrayText
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.Chip))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    }
}

/** 分隔标题：左侧竖条 + 主标题 + 右侧副标 */
@Composable
fun SectionDivider(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PayColors.Primary)
            )
            Text(
                title,
                modifier = Modifier.padding(start = 7.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PayColors.Text,
            )
        }
        if (sub != null) {
            Text(sub, fontSize = 11.sp, color = PayColors.Hint)
        }
    }
}

/** 说明文字 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color = PayColors.Hint) {
    Text(text, modifier = modifier, fontSize = 12.sp, color = color, lineHeight = 17.sp)
}

/** 键值行 */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF6B6774))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF26232C))
    }
}
