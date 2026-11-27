package com.nexvora.echora.ui.echora

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.echora.ui.theme.*

/** ECHORA design-system primitives (spec §3). Colours come from [Echora]/theme tokens only. */

@Composable
fun EchoraBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, InkRaised, Ink))).drawBehind {
            drawCircle(Echora.Purple.copy(alpha = 0.18f), radius = size.width * 0.7f, center = Offset(size.width * 0.9f, 0f))
            drawCircle(Echora.Cyan.copy(alpha = 0.10f), radius = size.width * 0.6f, center = Offset(0f, size.height * 0.85f))
        },
        content = content,
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Panel.copy(alpha = 0.92f), PanelHi.copy(alpha = 0.7f))))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Echora.Purple.copy(alpha = .45f), Echora.Cyan.copy(alpha = .25f)))), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.heightIn(min = 52.dp).clip(shape)
            .background(if (enabled) Echora.Gradient else Brush.linearGradient(listOf(PanelPressed, PanelPressed)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) Color.White else TextLo, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun OutlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (enabled) AccentLine else Line)) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) Echora.Cyan else TextLo); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (enabled) TextHi else TextLo)
    }
}

@Composable
fun <T> SegmentedPills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Echora.Gradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.White else TextMid, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoraTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = TextHi, navigationIconContentColor = TextHi, actionIconContentColor = TextHi),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 8.dp, bottom = 4.dp), color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Chip(text: String, color: Color = Echora.Cyan) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp))

/** Bars from real input levels (0..1). With no data it draws a flat, honest baseline — never fake activity. */
@Composable
fun LiveWaveform(levels: List<Float>, modifier: Modifier = Modifier, bars: Int = 48) {
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val gap = 3.dp.toPx(); val w = (size.width - gap * (bars - 1)) / bars
        val data = if (levels.isEmpty()) List(bars) { 0f } else levels.takeLast(bars).let { l -> List(bars - l.size) { 0f } + l }
        data.forEachIndexed { i, v ->
            val h = (4.dp.toPx() + v * (size.height - 4.dp.toPx()))
            drawRoundRect(Echora.GradientV, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** Animated mic orb. [level] 0..1 drives the glow; [active] enables the idle pulse. */
@Composable
fun MicOrb(level: Float, active: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 150.dp, onClick: (() -> Unit)? = null) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.08f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val glow by animateFloatAsState(level, label = "glow")
    Box(modifier.size(size).let { if (onClick != null) it.clip(CircleShape).clickable(onClick = onClick) else it }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = if (active) pulse else 1f
            drawCircle(Echora.Purple.copy(alpha = .20f + glow * .35f), radius = this.size.minDimension / 2 * s)
            drawCircle(Brush.linearGradient(listOf(Echora.Purple, Echora.Cyan)), radius = this.size.minDimension * .38f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()))
            drawCircle(Ink.copy(alpha = .85f), radius = this.size.minDimension * .35f)
        }
        Icon(Icons.Filled.Mic, "Microphone", tint = Color.White, modifier = Modifier.size(size * .3f))
    }
}

@Composable
fun InfoRow(label: String, value: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextLo); Text(value, color = TextHi, fontWeight = FontWeight.Medium)
    }

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(java.util.Locale.ROOT, b / 1e9)
    b >= 1_000_000 -> "%.1f MB".format(java.util.Locale.ROOT, b / 1e6)
    b >= 1_000 -> "%.0f KB".format(java.util.Locale.ROOT, b / 1e3)
    else -> "$b B"
}
fun formatDuration(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }
