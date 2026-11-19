package com.example.visitorcapture

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The app's colour tokens. The old names in Ui.kt (Navy, Panel, Amber, …) read these, so both themes share all screens. */
@Immutable
data class AppColors(
    val bg: Color,          // screen background            (was Navy)
    val bgDeep: Color,      // drawer, sheets, bars          (was NavyDeep)
    val card: Color,        // cards, fields, tiles          (was Panel)
    val cardActive: Color,  // selected / raised card        (was PanelOn)
    val text: Color,        // primary text                  (was LineC)
    val textDim: Color,     // secondary text                (was LineDim)
    val muted: Color,       // captions, icons               (was Steel)
    val accent: Color,      // brand amber: fills, borders, icons
    val accentText: Color,  // amber that stays readable as text on the background
    val onAccent: Color,    // text / icons on an amber fill
    val danger: Color,
    val dangerText: Color,
    val outline: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf { Brand.colors }

/** Plus Jakarta Sans, bundled in res/font (SIL Open Font License, see assets/licenses). */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold))

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Float = 0f) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

val AppTypography = Typography(
    displayLarge = style(48, FontWeight.ExtraBold, 52, -1f),
    displayMedium = style(40, FontWeight.ExtraBold, 44, -0.8f),
    displaySmall = style(34, FontWeight.Bold, 40, -0.5f),
    headlineLarge = style(30, FontWeight.Bold, 36, -0.4f),
    headlineMedium = style(26, FontWeight.Bold, 32, -0.3f),
    headlineSmall = style(22, FontWeight.Bold, 28, -0.2f),
    titleLarge = style(20, FontWeight.SemiBold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.SemiBold, 20, 0.1f),
    labelMedium = style(12, FontWeight.Medium, 16, 0.3f),
    labelSmall = style(11, FontWeight.Medium, 16, 0.4f))

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp))

/** Brand theme: tokens + a matching Material scheme, so dialogs, pickers, chips and menus follow the app's colours. */
@Composable
fun VisitorTheme(content: @Composable () -> Unit) {
    val c = Brand.colors
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.cardActive, onPrimaryContainer = c.text,
        secondary = c.textDim, onSecondary = c.bgDeep, secondaryContainer = c.cardActive, onSecondaryContainer = c.text,
        tertiary = c.accentText, background = c.bg, onBackground = c.text,
        surface = c.bgDeep, onSurface = c.text, surfaceVariant = c.card, onSurfaceVariant = c.textDim,
        surfaceContainerLowest = c.bgDeep, surfaceContainerLow = c.bgDeep, surfaceContainer = c.card,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.cardActive, surfaceTint = Color.Transparent,
        inverseSurface = c.text, inverseOnSurface = c.bgDeep, inversePrimary = c.accentText,
        outline = c.muted, outlineVariant = c.outline, error = c.danger, onError = Color.White, scrim = Color.Black)
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** App-wide snackbar (replaces Toasts), provided by App(). */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

/** Snackbar styled with the theme's card colour and an amber action. */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) =
    SnackbarHost(state, modifier) { data ->
        val c = LocalAppColors.current
        Snackbar(data, shape = RoundedCornerShape(14.dp),
            containerColor = if (c.isDark) c.cardActive else Color(0xFF0B2033),
            contentColor = if (c.isDark) c.text else Color.White,
            actionColor = c.accent, dismissActionContentColor = c.muted)
    }
