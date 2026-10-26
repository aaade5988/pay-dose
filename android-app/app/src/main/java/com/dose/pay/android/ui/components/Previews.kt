package com.dose.pay.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dose.pay.android.core.ChipStyle
import com.dose.pay.android.core.Methods
import com.dose.pay.android.ui.theme.PayColors

/** chip 样式预览 */
@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun PayChipPreview() {
    Column(
        modifier = Modifier.background(PayColors.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PayChip(text = "信用卡", style = ChipStyle.PRIMARY)
            PayChip(text = "电子钱包", style = ChipStyle.ACCENT)
            PayChip(text = "其他", style = ChipStyle.GRAY)
            PayChip(text = "安全", style = ChipStyle.GREEN)
            PayChip(text = "吃紧", style = ChipStyle.RED)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PayChip(text = "今天", active = true)
            PayChip(text = "明天")
        }
    }
}

/** 缺口环预览 */
@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun GapRingPreview() {
    Row(
        modifier = Modifier.background(PayColors.Surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GapRing(pct = 45.0, arc = Methods.ringArc(45.0), color = PayColors.RingOk, diameter = 54.dp, strokeWidth = 7.dp) {
            Text("45%", fontSize = 11.sp, color = PayColors.Text)
        }
        GapRing(pct = 62.0, arc = Methods.ringArc(62.0), color = PayColors.RingWarn, diameter = 54.dp, strokeWidth = 7.dp) {
            Text("62%", fontSize = 11.sp, color = PayColors.Text)
        }
        GapRing(pct = 88.0, arc = Methods.ringArc(88.0), color = PayColors.RingBad, diameter = 54.dp, strokeWidth = 7.dp) {
            Text("88%", fontSize = 11.sp, color = PayColors.Text)
        }
        GapRing(pct = null, arc = 0f, color = PayColors.RingTrack, none = true, diameter = 54.dp, strokeWidth = 7.dp) {
            Text("—", fontSize = 11.sp, color = PayColors.Care)
        }
    }
}
