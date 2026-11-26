package br.vendas.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colors beyond Material's roles: status tones (tinted background + strong
 * text) and the chart palette. Inspired by clean dashboard designs: warm light
 * gray background, white cards, near-black accents.
 */
@Immutable
data class AppColors(
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val track: Color,
    val inverseCard: Color,
    val onInverseCard: Color,
    val dangerSolid: Color,
    val onDangerSolid: Color,
    val dangerBg: Color,
    val dangerText: Color,
    val warningBg: Color,
    val warningText: Color,
    val successBg: Color,
    val successText: Color,
    val infoBg: Color,
    val infoText: Color,
    val neutralBg: Color,
    val neutralText: Color,
)

private val LightAppColors = AppColors(
    canvas = Color(0xFFF2F1EE),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE6E4DF),
    ink = Color(0xFF111111),
    muted = Color(0xFF6E6C67),
    faint = Color(0xFF9E9B95),
    track = Color(0xFFE9E7E2),
    inverseCard = Color(0xFF111111),
    onInverseCard = Color(0xFFFFFFFF),
    dangerSolid = Color(0xFFD7263D),
    onDangerSolid = Color(0xFFFFFFFF),
    dangerBg = Color(0xFFFCE4E4),
    dangerText = Color(0xFFB4232F),
    warningBg = Color(0xFFFBF0DA),
    warningText = Color(0xFF9A6410),
    successBg = Color(0xFFDFF3E6),
    successText = Color(0xFF23774A),
    infoBg = Color(0xFFE4E9FB),
    infoText = Color(0xFF3B54C4),
    neutralBg = Color(0xFFEDEBE7),
    neutralText = Color(0xFF55534E),
)

private val DarkAppColors = AppColors(
    canvas = Color(0xFF121212),
    card = Color(0xFF1D1D1C),
    cardBorder = Color(0xFF2C2B29),
    ink = Color(0xFFF4F3F0),
    muted = Color(0xFFA9A6A0),
    faint = Color(0xFF7C7974),
    track = Color(0xFF34332F),
    inverseCard = Color(0xFFF4F3F0),
    onInverseCard = Color(0xFF111111),
    dangerSolid = Color(0xFFE5484D),
    onDangerSolid = Color(0xFFFFFFFF),
    dangerBg = Color(0xFF3B1D1F),
    dangerText = Color(0xFFFF8A8E),
    warningBg = Color(0xFF3A2C12),
    warningText = Color(0xFFF2C46B),
    successBg = Color(0xFF16321F),
    successText = Color(0xFF7FD6A0),
    infoBg = Color(0xFF1C2445),
    infoText = Color(0xFF9DB0FF),
    neutralBg = Color(0xFF2A2927),
    neutralText = Color(0xFFC9C6C0),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Shortcut: `AppTheme.colors.ink`. */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun appTypography(base: Typography = Typography()) = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun VendasTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val app = if (dark) DarkAppColors else LightAppColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = app.ink,
            onPrimary = app.canvas,
            primaryContainer = app.neutralBg,
            onPrimaryContainer = app.ink,
            secondary = app.successText,
            secondaryContainer = app.neutralBg,
            onSecondaryContainer = app.ink,
            background = app.canvas,
            onBackground = app.ink,
            surface = app.card,
            onSurface = app.ink,
            surfaceVariant = app.neutralBg,
            onSurfaceVariant = app.muted,
            surfaceContainer = app.card,
            surfaceContainerHigh = app.card,
            surfaceContainerHighest = app.neutralBg,
            surfaceContainerLow = app.card,
            outline = app.cardBorder,
            outlineVariant = app.cardBorder,
            error = app.dangerSolid,
            errorContainer = app.dangerBg,
            onErrorContainer = app.dangerText,
        )
    } else {
        lightColorScheme(
            primary = app.ink,
            onPrimary = Color.White,
            primaryContainer = app.neutralBg,
            onPrimaryContainer = app.ink,
            secondary = app.successText,
            secondaryContainer = app.neutralBg,
            onSecondaryContainer = app.ink,
            tertiaryContainer = app.warningBg,
            onTertiaryContainer = app.warningText,
            background = app.canvas,
            onBackground = app.ink,
            surface = app.card,
            onSurface = app.ink,
            surfaceVariant = app.neutralBg,
            onSurfaceVariant = app.muted,
            surfaceContainer = app.card,
            surfaceContainerHigh = app.card,
            surfaceContainerHighest = app.neutralBg,
            surfaceContainerLow = app.card,
            outline = app.cardBorder,
            outlineVariant = app.cardBorder,
            error = app.dangerSolid,
            errorContainer = app.dangerBg,
            onErrorContainer = app.dangerText,
        )
    }
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = appTypography(), content = content)
    }
}
