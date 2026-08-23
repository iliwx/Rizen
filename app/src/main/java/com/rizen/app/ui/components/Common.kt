package com.rizen.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeShape
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Barely-there CRT scanlines plus a soft accent bloom at the top. Subtle on purpose:
 * the brief was "terminal", not "1998 screensaver".
 */
@Composable
fun TerminalBackground(
    modifier: Modifier = Modifier,
    bloom: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = LocalWake.current
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .drawBehind {
                if (bloom) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(c.accent.copy(alpha = 0.055f), Color.Transparent),
                            center = Offset(size.width * 0.5f, -size.height * 0.05f),
                            radius = size.width * 0.95f,
                        ),
                        radius = size.width * 0.95f,
                        center = Offset(size.width * 0.5f, -size.height * 0.05f),
                    )
                }
                var y = 0f
                val gap = 3.5f
                while (y < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.011f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += gap
                }
            }
    ) { content() }
}

// ══════════════════════════════════════════════════════════════════════════════
// TEXT
// ══════════════════════════════════════════════════════════════════════════════

/** `// section` — the terminal equivalent of a heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = LocalWake.current
    Text(
        text = "// ${text.lowercase()}",
        style = MaterialTheme.typography.labelMedium,
        color = if (accent) c.accent else c.textFaint,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** Types text out character by character. Used sparingly — intros and mission briefs. */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    speedMs: Long = 18,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalWake.current.textDim,
    textAlign: TextAlign? = null,
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shown = 0
        while (shown < text.length) {
            delay(speedMs)
            shown++
        }
    }
    Text(
        text = text.take(shown),
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Blinking block cursor. */
@Composable
fun Caret(modifier: Modifier = Modifier, color: Color = LocalWake.current.accent) {
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Box(
        modifier
            .size(width = 9.dp, height = 18.dp)
            .alpha(a)
            .background(color, RoundedCornerShape(1.dp))
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// CONTAINERS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WPCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalWake.current
    val border by animateColorAsState(
        if (highlighted) c.accent.copy(alpha = 0.55f) else c.outlineSoft,
        label = "cardBorder",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WakeShape.radius))
            .background(if (highlighted) c.surfaceHigh else c.surface)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(WakeShape.radius))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// BUTTONS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WPButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    ghost: Boolean = false,
    leading: String? = null,
) {
    val c = LocalWake.current
    val tint = when {
        danger -> c.danger
        else -> c.accent
    }
    val bg = when {
        ghost -> Color.Transparent
        else -> tint.copy(alpha = if (enabled) 0.14f else 0.05f)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(WakeShape.radius))
            .background(bg)
            .border(
                BorderStroke(1.dp, if (enabled) tint.copy(alpha = 0.5f) else c.outlineSoft),
                RoundedCornerShape(WakeShape.radius),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (leading?.let { "$it " } ?: "") + text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else c.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ROWS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WPSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalWake.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) c.text else c.textFaint,
            )
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.textFaint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.bg,
                checkedTrackColor = c.accent,
                checkedBorderColor = c.accent,
                uncheckedThumbColor = c.textFaint,
                uncheckedTrackColor = c.surfaceHigh,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/** −  value  + stepper, because sliders are unusable half-asleep. */
@Composable
fun WPStepper(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999,
    step: Int = 1,
    suffix: String = "",
) {
    val c = LocalWake.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text,
            modifier = Modifier.weight(1f))
        StepChip("−") { onValue((value - step).coerceIn(range)) }
        Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
            Text(
                "$value$suffix",
                style = MaterialTheme.typography.titleMedium,
                color = c.accent,
            )
        }
        StepChip("+") { onValue((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepChip(glyph: String, onClick: () -> Unit) {
    val c = LocalWake.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(WakeShape.radiusSmall))
            .background(c.surfaceHigh)
            .border(BorderStroke(1.dp, c.outline), RoundedCornerShape(WakeShape.radiusSmall))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = c.textDim)
    }
}

/** Horizontal pick-one row. */
@Composable
fun <T> WPSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalWake.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WakeShape.radiusSmall))
            .background(c.surfaceHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) c.accentSoft else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) c.accent else c.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FEEDBACK
// ══════════════════════════════════════════════════════════════════════════════

/** Chunky segmented progress bar — reads instantly at a glance. */
@Composable
fun SegmentBar(
    progress: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    color: Color = LocalWake.current.accent,
) {
    val c = LocalWake.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "segbar")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(segments) { i ->
            val on = i < (p * segments)
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (on) color else c.outlineSoft)
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        1f, 0.25f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        "dotAlpha",
    )
    Box(
        modifier
            .size(8.dp)
            .alpha(if (pulsing) a else 1f)
            .background(color, CircleShape)
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalWake.current.outlineSoft)
    )
}
