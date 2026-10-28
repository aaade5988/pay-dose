package com.dosepay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = PayColors.Primary,
    onPrimary = PayColors.Surface,
    primaryContainer = PayColors.Secondary,
    onPrimaryContainer = PayColors.PrimaryDeep,
    secondary = PayColors.Accent,
    onSecondary = PayColors.Surface,
    secondaryContainer = PayColors.AccentSoft,
    onSecondaryContainer = PayColors.AccentText,
    tertiary = PayColors.RepayGreen,
    onTertiary = PayColors.Surface,
    tertiaryContainer = PayColors.GreenSoft,
    onTertiaryContainer = PayColors.RepayGreen,
    background = PayColors.Bg,
    onBackground = PayColors.Text,
    surface = PayColors.Surface,
    onSurface = PayColors.Text,
    surfaceVariant = PayColors.Bg,
    onSurfaceVariant = PayColors.TextMuted,
    outline = PayColors.Border,
    outlineVariant = PayColors.LineSoft,
    error = PayColors.RedText,
    onError = PayColors.Surface,
    errorContainer = PayColors.RedSoft,
    onErrorContainer = PayColors.RedText,
)

/**
 * 应用主题。原小程序只有浅色一版，这里同样固定浅色，不跟随系统深色。
 */
@Composable
fun PayDoseTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = LightScheme,
        typography = PayTypography,
        content = content,
    )
}
