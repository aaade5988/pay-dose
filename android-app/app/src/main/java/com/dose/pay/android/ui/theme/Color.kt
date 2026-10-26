package com.dose.pay.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 设计令牌（对应原小程序 app.wxss 的 CSS 变量与视觉稿色板）。
 * 全部为不透明常量色，避免运行时透明度叠加导致色偏。
 */
object PayColors {
    /** 主色：选中导航 / 主按钮 / 强调 */
    val Primary = Color(0xFF5B50E6)
    /** 深紫：渐变卡另一端 */
    val PrimaryDeep = Color(0xFF231F4E)
    /** 浅紫：chip 底色 */
    val Secondary = Color(0xFFDCD8FF)

    /** 橙：消费 / 警示 */
    val Accent = Color(0xFFF26B38)
    val AccentSoft = Color(0xFFFFE9D9)
    val AccentText = Color(0xFFB34A1F)

    /** 绿：还款 */
    val RepayGreen = Color(0xFF0D8A63)
    val GreenSoft = Color(0xFFE2F7EE)

    /** 页面底色 / 卡片底色 */
    val Bg = Color(0xFFF2F0EB)
    val Surface = Color(0xFFFFFFFF)

    /** 文本 */
    val Text = Color(0xFF171717)
    val TextMuted = Color(0xFF66645F)
    val Hint = Color(0xFF8B8792)
    val Care = Color(0xFFB9B5C0)

    /** 描边 */
    val Border = Color(0xFFD7D3CA)
    val LineSoft = Color(0xFFF0EEE8)
    val Slot = Color(0xFFF6F5F1)

    /** 灰 chip */
    val ChipGrayBg = Color(0xFFECEAE4)
    val ChipGrayText = Color(0xFF79766F)

    /** 红：吃紧 / 超限 */
    val RedSoft = Color(0xFFFEE4E0)
    val RedText = Color(0xFFB3261E)

    /** 环形语义色 */
    val RingOk = Color(0xFF13C296)
    val RingWarn = Color(0xFFFAAD14)
    val RingBad = Color(0xFFF5533D)
    val RingTrack = Color(0xFFE8E5DD)

    /** 虚线灰环 */
    val RingNoneTrack = Color(0xFFD3CEC3)

    /** 字头色板：按名称首字哈希循环取色 */
    val AvatarPalette: List<Color> = listOf(
        Color(0xFF5B50E6),
        Color(0xFFE60012),
        Color(0xFF95D475),
        Color(0xFF1677FF),
        Color(0xFFF26B38),
        Color(0xFF0F9D8F),
        Color(0xFFB0407F),
        Color(0xFF6B7280),
        Color(0xFFC9A227),
        Color(0xFF3F51B5),
    )
}
