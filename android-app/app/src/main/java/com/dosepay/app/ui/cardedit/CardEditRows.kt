package com.dosepay.app.ui.cardedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosepay.app.ui.theme.PayColors

/** 分组标题 */
@Composable
internal fun GroupTitle(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PayColors.Text)
}

/** 只读行：左标签 + 右侧取值（无选择器、无箭头） */
@Composable
internal fun ReadOnlyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF43404C))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
    }
}

/** 表单行：左标签 + 右输入 */
@Composable
internal fun EditRow(label: String, trailing: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
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

/** 选择器行 */
@Composable
internal fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF43404C))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PayColors.Text)
            Text("▾", fontSize = 12.sp, color = PayColors.Care)
        }
    }
}

/** 行内错误 */
@Composable
internal fun ErrorText(message: String?) {
    if (message == null) return
    Text(
        message,
        fontSize = 11.sp,
        color = Color(0xFFD0342C),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
