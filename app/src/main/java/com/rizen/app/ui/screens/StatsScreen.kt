package com.rizen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.ActivityLogEntity
import com.rizen.app.data.db.LogKind
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.components.WPSegmented
import com.rizen.app.ui.theme.LocalWake
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

private enum class Range(val days: Int) { DAY(1), WEEK(7), MONTH(30) }

@Composable
fun StatsScreen(vm: AppViewModel) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val log by vm.recentLog.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    var range by remember { mutableStateOf(Range.WEEK) }
    val since = TimeFmt.startOfDay() - TimeUnit.DAYS.toMillis((range.days - 1).toLong())
    val inRange = log.filter { it.timestamp >= since }

    val wakeEvents = inRange.filter { it.kind == LogKind.WOKE_UP }
    val avgWakeMinutes = wakeEvents
        .map { TimeFmt.hourOf(it.timestamp) * 60 + TimeFmt.minuteOf(it.timestamp) }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
    val avgFightMs = wakeEvents.map { it.durationMs }.filter { it > 0 }
        .takeIf { it.isNotEmpty() }?.average()?.toLong()

    val escapes = inRange.count { it.kind == LogKind.EMERGENCY_EXIT } +
        inRange.count { it.kind == LogKind.SNOOZED }
    val failed = inRange.filter { it.kind == LogKind.MISSION_FAILED }
    val nemesis = failed.groupingBy { it.label }.eachCount().maxByOrNull { it.value }?.key
    val tasksDone = inRange.count { it.kind == LogKind.TASK_DONE || it.kind == LogKind.ROUTINE_DONE }
    val streak = computeStreak(log)

    val todayEvents = log.filter { it.timestamp >= TimeFmt.startOfDay() }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(s.statsTitle, style = MaterialTheme.typography.headlineMedium, color = c.text)
            Spacer(Modifier.height(10.dp))
            WPSegmented(
                options = listOf(
                    Range.DAY to s.statsDay,
                    Range.WEEK to s.statsWeek,
                    Range.MONTH to s.statsMonth,
                ),
                selected = range,
                onSelect = { range = it },
            )
        }

        if (log.isEmpty()) {
            item {
                WPCard {
                    Text(
                        s.statsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            return@LazyColumn
        }

        // ── the dial ─────────────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.statsTimeline, accent = true)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    DayDial(todayEvents)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            avgWakeMinutes?.let {
                                TimeFmt.clock(it / 60, it % 60, settings.use24h)
                            } ?: "--:--",
                            style = MaterialTheme.typography.displaySmall,
                            color = c.accent,
                        )
                        Text(
                            s.statsAvgWake,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textFaint,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(c.accent, s.statsWokeAt)
                    LegendDot(c.warn, s.statsFailed)
                    LegendDot(c.textDim, s.statsTasksDone)
                }
            }
        }

        // ── KPI tiles ────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(s.statsStreak, s.statsStreakDays.fmt(streak), Modifier.weight(1f), c.accent)
                Tile(
                    s.statsTookYou,
                    avgFightMs?.let { TimeFmt.humanDuration(it, s) } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(s.statsSnoozes, "$escapes", Modifier.weight(1f),
                    if (escapes > 0) c.warn else c.textDim)
                Tile(s.statsTasksDone, "$tasksDone", Modifier.weight(1f))
            }
        }
        item {
            WPCard {
                SectionLabel(s.statsHardest)
                Text(
                    nemesis?.lowercase()?.replace('_', ' ') ?: s.statsNoNemesis,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (nemesis != null) c.warn else c.textFaint,
                )
                if (nemesis != null) {
                    Text(
                        "${failed.count { it.label == nemesis }}× ${s.statsFailed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textFaint,
                    )
                }
            }
        }

        // ── 30-day heatmap ───────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.statsMonth)
                Heatmap(log)
            }
        }

        // ── raw log ──────────────────────────────────────────────────
        item { SectionLabel(s.statsLog, accent = true) }
        items(inRange.take(60), key = { it.id }) { entry ->
            LogRow(entry, settings.use24h)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val c = LocalWake.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textFaint)
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalWake.current.text,
) {
    val c = LocalWake.current
    WPCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textFaint)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
    }
}

/**
 * A 24-hour clock face where every logged event is a tick at its real time of day.
 * Reading your morning as a shape rather than a list is the whole reason this exists.
 */
@Composable
private fun DayDial(events: List<ActivityLogEntity>) {
    val c = LocalWake.current
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f

        // hour grid
        repeat(24) { h ->
            val angle = Math.toRadians((h * 15f - 90f).toDouble())
            val inner = outer - if (h % 6 == 0) 22f else 10f
            drawLine(
                color = if (h % 6 == 0) c.outline else c.outlineSoft,
                start = Offset(
                    cx + (inner * cos(angle)).toFloat(),
                    cy + (inner * sin(angle)).toFloat()
                ),
                end = Offset(
                    cx + (outer * cos(angle)).toFloat(),
                    cy + (outer * sin(angle)).toFloat()
                ),
                strokeWidth = if (h % 6 == 0) 2.5f else 1.2f,
            )
        }

        drawCircle(
            color = c.outlineSoft,
            radius = outer - 26f,
            center = Offset(cx, cy),
            style = Stroke(1.5f),
        )

        // events
        events.forEach { e ->
            val f = TimeFmt.dayFraction(e.timestamp)
            val angle = Math.toRadians((f * 360f - 90f).toDouble())
            val color = when (e.kind) {
                LogKind.WOKE_UP, LogKind.MISSION_PASSED -> c.accent
                LogKind.MISSION_FAILED, LogKind.MISSION_TIMEOUT,
                LogKind.WAKE_CHECK_MISSED, LogKind.EMERGENCY_EXIT -> c.warn
                LogKind.ALARM_FIRED -> c.danger
                else -> c.textDim
            }
            val len = when (e.kind) {
                LogKind.WOKE_UP -> 44f
                LogKind.ALARM_FIRED -> 34f
                else -> 22f
            }
            val r0 = outer - 30f
            drawLine(
                color = color,
                start = Offset(
                    cx + ((r0 - len) * cos(angle)).toFloat(),
                    cy + ((r0 - len) * sin(angle)).toFloat()
                ),
                end = Offset(
                    cx + (r0 * cos(angle)).toFloat(),
                    cy + (r0 * sin(angle)).toFloat()
                ),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round,
            )
        }

        // now hand
        val nowAngle = Math.toRadians(
            (TimeFmt.dayFraction(System.currentTimeMillis()) * 360f - 90f).toDouble()
        )
        drawLine(
            color = c.accent.copy(alpha = 0.45f),
            start = Offset(cx, cy),
            end = Offset(
                cx + ((outer - 34f) * cos(nowAngle)).toFloat(),
                cy + ((outer - 34f) * sin(nowAngle)).toFloat()
            ),
            strokeWidth = 1.5f,
        )
    }
}

/** Last 30 days, one cell per day, brightness = how much you actually got done. */
@Composable
private fun Heatmap(log: List<ActivityLogEntity>) {
    val c = LocalWake.current
    val byDay = log.groupBy { TimeFmt.dayKey(it.timestamp) }
        .mapValues { (_, v) ->
            v.count { it.kind == LogKind.TASK_DONE || it.kind == LogKind.ROUTINE_DONE ||
                it.kind == LogKind.WOKE_UP }
        }
    val today = TimeFmt.startOfDay()
    val cells = (29 downTo 0).map { back ->
        byDay[TimeFmt.dayKey(today - TimeUnit.DAYS.toMillis(back.toLong()))] ?: 0
    }
    val max = (cells.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val cols = 10
        val rows = 3
        val gap = 6f
        val cw = (size.width - gap * (cols - 1)) / cols
        val ch = (size.height - gap * (rows - 1)) / rows
        cells.forEachIndexed { i, v ->
            val r = i / cols
            val col = i % cols
            drawRoundRect(
                color = if (v == 0) c.outlineSoft
                else c.accent.copy(alpha = 0.22f + 0.68f * (v.toFloat() / max)),
                topLeft = Offset(col * (cw + gap), r * (ch + gap)),
                size = Size(cw, ch),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
        }
    }
}

@Composable
private fun LogRow(entry: ActivityLogEntity, use24h: Boolean) {
    val c = LocalWake.current
    val color = when (entry.kind) {
        LogKind.WOKE_UP -> c.accent
        LogKind.MISSION_FAILED, LogKind.WAKE_CHECK_MISSED, LogKind.EMERGENCY_EXIT -> c.warn
        LogKind.ALARM_FIRED -> c.danger
        else -> c.textDim
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TimeFmt.clockOf(entry.timestamp, use24h),
            style = MaterialTheme.typography.labelSmall,
            color = c.textFaint,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            entry.kind.name.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (entry.label.isNotBlank()) {
            Text(
                entry.label.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = c.textFaint,
                maxLines = 1,
            )
        }
    }
}

/** Consecutive days ending today with at least one completed wake-up. */
private fun computeStreak(log: List<ActivityLogEntity>): Int {
    val wakeDays = log.filter { it.kind == LogKind.WOKE_UP }
        .map { TimeFmt.dayKey(it.timestamp) }
        .toSet()
    var streak = 0
    var cursor = TimeFmt.startOfDay()
    while (wakeDays.contains(TimeFmt.dayKey(cursor))) {
        streak++
        cursor -= TimeUnit.DAYS.toMillis(1)
    }
    return streak
}
