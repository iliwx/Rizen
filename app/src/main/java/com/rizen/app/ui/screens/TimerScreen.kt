package com.rizen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.alarm.CountdownService
import com.rizen.app.alarm.TimerState
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.components.WPStepper
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun TimerScreen(vm: AppViewModel) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val run by TimerState.run.collectAsStateWithLifecycle()

    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(250) }
    }

    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(10) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(s.timerTitle, style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(16.dp))

        val active = run
        if (active != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                CountdownRing(active.progress(now))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TimeFmt.hhmmss(active.remaining(now)),
                        style = MaterialTheme.typography.displayLarge,
                        color = c.accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        active.label.ifBlank { s.timerTitle },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textDim,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WPButton(s.timerAdd1, { CountdownService.add(context, 60_000) },
                    Modifier.weight(1f), ghost = true)
                WPButton(s.timerAdd5, { CountdownService.add(context, 300_000) },
                    Modifier.weight(1f), ghost = true)
                WPButton(s.timerAdd10, { CountdownService.add(context, 600_000) },
                    Modifier.weight(1f), ghost = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WPButton(
                    if (active.paused) s.resume else s.pause,
                    {
                        if (active.paused) CountdownService.resume(context)
                        else CountdownService.pause(context)
                    },
                    Modifier.weight(1f),
                )
                WPButton(s.stop, { CountdownService.stop(context) },
                    Modifier.weight(1f), danger = true, ghost = true)
            }
        } else {
            WPCard {
                SectionLabel(s.timerSet, accent = true)
                TerminalField(label, s.timerLabelHint) { label = it }
                Spacer(Modifier.height(12.dp))
                WPStepper(s.minutesShort, minutes, { minutes = it },
                    range = 1..600, step = 1, suffix = s.minutesShort)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10).forEach { preset ->
                        PresetChip("$preset", Modifier.weight(1f)) { minutes = preset }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 20, 30, 45, 60).forEach { preset ->
                        PresetChip("$preset", Modifier.weight(1f)) { minutes = preset }
                    }
                }
                Spacer(Modifier.height(14.dp))
                WPButton(
                    s.start,
                    {
                        CountdownService.start(
                            context, label, TimeUnit.MINUTES.toMillis(minutes.toLong())
                        )
                        label = ""
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                s.timerNone,
                style = MaterialTheme.typography.bodySmall,
                color = c.textFaint,
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalWake.current
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceHigh)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textDim)
    }
}

@Composable
private fun CountdownRing(progress: Float) {
    val c = LocalWake.current
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        val d = size.minDimension
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)

        // tick marks every 5% for that instrument-panel feel
        repeat(60) { i ->
            val angle = Math.toRadians((i * 6f - 90f).toDouble())
            val outer = d / 2f
            val inner = outer - if (i % 5 == 0) 16f else 8f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(
                color = if (i / 60f <= progress) c.accent.copy(alpha = 0.75f) else c.outlineSoft,
                start = Offset(
                    cx + (inner * kotlin.math.cos(angle)).toFloat(),
                    cy + (inner * kotlin.math.sin(angle)).toFloat(),
                ),
                end = Offset(
                    cx + (outer * kotlin.math.cos(angle)).toFloat(),
                    cy + (outer * kotlin.math.sin(angle)).toFloat(),
                ),
                strokeWidth = if (i % 5 == 0) 3f else 1.5f,
                cap = StrokeCap.Round,
            )
        }

        drawArc(
            color = c.accent,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(topLeft.x + 26f, topLeft.y + 26f),
            size = Size(d - 52f, d - 52f),
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )
    }
}
