package com.dosepay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.dosepay.app.ui.theme.PayColors
import com.dosepay.app.ui.theme.Radii

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

/** 空态：卡片底 + 居中标题与说明 */
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

/** 错误态：浅橙底 + 橙色说明 */
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
