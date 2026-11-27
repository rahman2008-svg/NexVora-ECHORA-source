/*
 * file:    Theme.kt
 * author:  Typezer∅
 * version: 0.8.0-dev.1
 * desc:    Resound's visual system — a darkroom for sound. OLED-black
 *          surfaces, cyan interaction states, restrained amber live
 *          states, strong typography, and consistent studio-grade geometry.
 */
package com.nexvora.echora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Core palette
val Ink = Color(0xFF050714)
val InkRaised = Color(0xFF0B1020)
val Panel = Color(0xFF111A30)
val PanelHi = Color(0xFF18233F)
val PanelPressed = Color(0xFF22305A)
val Line = Color(0xFF2A3558)
val LineSoft = Color(0xFF1B2542)
// Data colours: waveform body and the first track identity. Not used for controls.
val Signal = Color(0xFF00D5FF)
val SignalDeep = Color(0xFF004D66)

// Interactive accent (ECHORA Primary Purple #702CFF; cyan #00D5FF is Signal): controls, selection and meaningful active states.
val Accent = Color(0xFF702CFF)
val AccentLine = Color(0xB3702CFF)       // thin, understated outline on primary actions (70%)
val AccentDeep = Color(0xFF2A1766)       // Material primaryContainer; no glow or tinted fills elsewhere
val ControlLabel = Color(0xFFE6E9F2)     // readable light-grey label on controls
val IconInfo = Color(0xFF9BA6BD)         // neutral grey for informational, non-interactive icons
val Amber = Color(0xFFFFC857)
val TextHi = Color(0xFFFFFFFF)
val TextMid = Color(0xFFC4CBDB)
val TextLo = Color(0xFF9BA6BD)

private val ResoundColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDeep,
    onPrimaryContainer = TextHi,
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = PanelHi,
    onSecondaryContainer = TextHi,
    tertiary = Signal,
    background = Ink,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceTint = Color.Transparent,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextMid,
    surfaceDim = Ink,
    surfaceBright = PanelPressed,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHi,
    surfaceContainerHighest = PanelPressed,
    outline = Line,
    outlineVariant = LineSoft,
    error = Color(0xFFFF7373),
)

val Mono = FontFamily.Monospace

private val ResoundType = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.55).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.25.sp,
        ),
        bodyLarge = base.bodyLarge.copy(color = TextHi),
        bodyMedium = base.bodyMedium.copy(color = TextMid),
        bodySmall = base.bodySmall.copy(color = TextLo),
    )
}

val TimecodeStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
)

private val ResoundShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Icon size targets; layouts may step down where a target would clip (always inside 48dp touch targets). */
object ControlIcon {
    val Primary = 30.dp
    val Toolbar = 28.dp
    val Navigation = 26.dp
    val Header = 26.dp
}

object ResoundDimens {
    val ScreenHorizontal = 20.dp
    val ScreenVertical = 16.dp
    val CardPadding = 16.dp
    val SectionGap = 16.dp
    val ControlHeight = 52.dp
}

@Composable
fun ResoundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ResoundColors,
        typography = ResoundType,
        shapes = ResoundShapes,
        content = content,
    )
}

/** ECHORA brand tokens (design system §3). */
object Echora {
    val Purple = Color(0xFF702CFF)
    val Cyan = Color(0xFF00D5FF)
    val Gold = Color(0xFFFFC857)
    val Gradient = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Purple, Cyan))
    val GradientV = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Purple, Cyan))
}
