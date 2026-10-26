package com.dose.pay.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dose.pay.android.core.ChipStyle
import com.dose.pay.android.ui.theme.PayColors

/** 圆角令牌：大卡 24dp / 普通卡 16dp */
object Radii {
    val Large = 24.dp
    val Card = 16.dp
    val Small = 12.dp
    val Chip = 100.dp
}

/** 白色圆角卡片 */
@Composable
fun PayCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radii.Card,
    background: Color = PayColors.Surface,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(contentPadding),
        content = content,
    )
}

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

/** chip：全圆角 */
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

/** 空态：虚线边框 */
@Composable
fun EmptyStateBox(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(PayColors.Surface)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6F6B7A))
        Text(description, fontSize = 13.sp, color = Color(0xFFA09CAC), lineHeight = 20.sp)
    }
}

/** 错误态：浅橙底 + 橙边 */
@Composable
fun ErrorStateBox(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .background(Color(0xFFFFF6F1))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF26232C))
        Text(description, fontSize = 13.sp, color = PayColors.AccentText, lineHeight = 20.sp)
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

/** 主按钮（紫底白字） */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = PayColors.Primary,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) background else background.copy(alpha = 0.55f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 描边按钮 */
@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PayColors.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PayColors.Primary)
    }
}

/** 分段控件 */
@Composable
fun SegControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Small))
            .background(Color(0xFFEFEDE6))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val on = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) PayColors.Surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (on) PayColors.Primary else Color(0xFF7D7A85),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 表单行：左标签 + 右内容 */
@Composable
fun FormFieldRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF43404C))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}
