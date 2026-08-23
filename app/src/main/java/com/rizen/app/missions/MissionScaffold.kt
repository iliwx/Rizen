package com.rizen.app.missions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.components.StatusDot
import com.rizen.app.ui.components.TerminalBackground
import com.rizen.app.ui.theme.LocalWake

/**
 * Shared chrome around every mission: where you are in the ladder, what you're being
 * asked, whether the alarm is currently screaming, and the emergency hatch.
 */
@Composable
fun MissionScaffold(
    spec: MissionSpec,
    index: Int,
    total: Int,
    title: String,
    brief: String,
    soundOn: Boolean,
    graceRemaining: Int,
    emergencyAvailable: Boolean,
    onEmergency: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current

    TerminalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // ── status line ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = if (soundOn) c.danger else c.accent,
                    pulsing = soundOn,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    s.missionStep.fmt(index + 1, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textFaint,
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(!soundOn && graceRemaining > 0) {
                    Text(
                        if (graceRemaining <= 10) s.missionGraceWarn.fmt(graceRemaining)
                        else s.missionGrace.fmt(graceRemaining),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (graceRemaining <= 10) c.warn else c.accent,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            SegmentBar(
                progress = if (total == 0) 0f else index.toFloat() / total,
                segments = total.coerceIn(1, 12),
                color = c.accent,
            )

            Spacer(Modifier.height(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = c.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                brief,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
            )

            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }

            if (emergencyAvailable) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEmergency() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "[ ${s.emgTitle} ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Big centred readout under a camera view. */
@Composable
fun MissionStatusLine(
    text: String,
    modifier: Modifier = Modifier,
    good: Boolean = false,
    bad: Boolean = false,
) {
    val c = LocalWake.current
    val color = when {
        good -> c.accent
        bad -> c.warn
        else -> c.textDim
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface.copy(alpha = 0.86f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
