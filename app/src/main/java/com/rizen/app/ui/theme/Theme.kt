package com.rizen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.i18n.StringsFa
import com.rizen.app.data.prefs.AccentTheme
import com.rizen.app.data.prefs.AppLanguage
import com.rizen.app.data.prefs.AppSettings

/**
 * Soft terminal. Dark, never pure black — #0D1117 keeps OLED contrast without the
 * harsh void of #000, and every accent is desaturated enough to look at with one eye
 * open at 6am.
 */
@Immutable
data class WakeColors(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val outlineSoft: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val danger: Color,
    val warn: Color,
    val ok: Color,
)

private fun colorsFor(accent: AccentTheme): WakeColors {
    val a = when (accent) {
        AccentTheme.PHOSPHOR -> Color(0xFF5BE49B)
        AccentTheme.AMBER -> Color(0xFFF5B94A)
        AccentTheme.ICE -> Color(0xFF5AD1E8)
        AccentTheme.MAGENTA -> Color(0xFFF06BB0)
    }
    return WakeColors(
        bg = Color(0xFF0D1117),
        surface = Color(0xFF151B23),
        surfaceHigh = Color(0xFF1C242E),
        outline = Color(0xFF2A3543),
        outlineSoft = Color(0xFF1F2833),
        text = Color(0xFFDCE6F0),
        textDim = Color(0xFF8A9AAC),
        textFaint = Color(0xFF56677A),
        accent = a,
        accentSoft = a.copy(alpha = 0.16f),
        accentGlow = a.copy(alpha = 0.32f),
        danger = Color(0xFFFF6F6F),
        warn = Color(0xFFFFB454),
        ok = a,
    )
}

val LocalWake = staticCompositionLocalOf { colorsFor(AccentTheme.PHOSPHOR) }

/** Everything is monospace. That's the whole visual thesis. */
private val Mono = FontFamily.Monospace

private fun mono(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    spacing: Double = 0.0,
    lineHeight: Int = (size * 1.45).toInt(),
) = TextStyle(
    fontFamily = Mono,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

val WakeTypography = Typography(
    displayLarge = mono(56, FontWeight.Light, -2.0, 60),
    displayMedium = mono(42, FontWeight.Light, -1.5, 48),
    displaySmall = mono(32, FontWeight.Normal, -0.5),
    headlineMedium = mono(24, FontWeight.Medium, 0.5),
    headlineSmall = mono(20, FontWeight.Medium, 0.5),
    titleLarge = mono(18, FontWeight.Medium, 1.0),
    titleMedium = mono(15, FontWeight.Medium, 0.8),
    bodyLarge = mono(15, FontWeight.Normal, 0.2),
    bodyMedium = mono(13, FontWeight.Normal, 0.2),
    bodySmall = mono(12, FontWeight.Normal, 0.3),
    labelLarge = mono(13, FontWeight.Medium, 1.2),
    labelMedium = mono(11, FontWeight.Medium, 1.4),
    labelSmall = mono(10, FontWeight.Medium, 1.6),
)

object WakeShape {
    val radius = 14.dp
    val radiusSmall = 8.dp
    val radiusLarge = 22.dp
}

@Composable
fun WakeTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    // Dark-only by design: a bright alarm screen at 6am is a hostile act.
    val colors = colorsFor(settings.accent)
    val strings = if (settings.language == AppLanguage.FA) StringsFa else StringsEn
    val direction =
        if (settings.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.bg,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.accent,
        secondary = colors.textDim,
        background = colors.bg,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.textDim,
        outline = colors.outline,
        error = colors.danger,
        onError = colors.bg,
    )

    CompositionLocalProvider(
        LocalWake provides colors,
        LocalStrings provides strings,
        LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(colorScheme = scheme, typography = WakeTypography, content = content)
    }
}
