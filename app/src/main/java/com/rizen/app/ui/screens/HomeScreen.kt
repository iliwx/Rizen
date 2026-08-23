package com.rizen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rizen.app.Routes
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.displayName
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.db.TaskStatus
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.StatusDot
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val next by vm.nextAlarmAt.collectAsStateWithLifecycle()
    val tasks by vm.todayTasks.collectAsStateWithLifecycle()

    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }

    val hour = TimeFmt.hourOf(now)
    val greeting = when {
        hour in 0..4 -> s.greetNight
        hour in 5..11 -> s.greetMorning
        hour in 12..17 -> s.greetDay
        else -> s.greetEvening
    }
    val open = tasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text(
                        TimeFmt.clockWithSeconds(now),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textFaint,
                    )
                }
                Text(
                    "[ ${s.navSettings} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                    modifier = Modifier
                        .clickable { nav.navigate(Routes.SETTINGS) }
                        .padding(8.dp),
                )
            }
        }

        // ── next alarm ───────────────────────────────────────────────
        item {
            WPCard(
                highlighted = next != null,
                onClick = { nav.navigate(Routes.ALARMS) },
            ) {
                SectionLabel(s.homeNextAlarm, accent = next != null)
                val pair = next
                if (pair == null) {
                    Text(s.homeNoAlarm, style = MaterialTheme.typography.titleMedium, color = c.textDim)
                } else {
                    val (alarm, at) = pair
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            TimeFmt.clock(alarm.hour, alarm.minute, settings.use24h),
                            style = MaterialTheme.typography.displayLarge,
                            color = c.accent,
                        )
                        if (!settings.use24h) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                TimeFmt.meridiem(alarm.hour),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textDim,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    Text(
                        s.homeRingsIn.fmt(TimeFmt.humanDuration(at - now, s)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textDim,
                    )
                    if (alarm.label.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(alarm.label, style = MaterialTheme.typography.bodySmall, color = c.textFaint)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        alarm.activeMissions().take(5).forEach { m ->
                            MissionChip(m.type.displayName(s))
                        }
                    }
                }
            }
        }

        // ── quick actions ────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(s.homeAddAlarm, Modifier.weight(1f)) {
                    nav.navigate(Routes.alarmEdit(0L))
                }
                QuickTile(s.homeQuickTimer, Modifier.weight(1f)) { nav.navigate(Routes.TIMER) }
                QuickTile(s.homeRunRoutine, Modifier.weight(1f)) {
                    vm.runRoutineNow(); nav.navigate(Routes.PLAN)
                }
            }
        }

        // ── today ────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(s.homeTodayPlan, Modifier.weight(1f))
                Text(
                    if (open.isEmpty()) s.homeAllDone else s.homeTasksLeft.fmt(open.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (open.isEmpty()) c.accent else c.textFaint,
                )
            }
        }

        if (tasks.isEmpty()) {
            item {
                WPCard {
                    Text(
                        s.homeNothingPlanned,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            items(tasks.take(6), key = { it.id }) { task ->
                HomeTaskRow(task, settings.use24h) { vm.markTaskDone(task.id) }
            }
        }
    }
}

@Composable
private fun MissionChip(label: String) {
    val c = LocalWake.current
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textDim, maxLines = 1)
    }
}

@Composable
private fun QuickTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalWake.current
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeTaskRow(task: TaskEntity, use24h: Boolean, onDone: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val done = task.status == TaskStatus.DONE
    WPCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = when (task.status) {
                    TaskStatus.DONE -> c.accent
                    TaskStatus.RUNNING -> c.warn
                    TaskStatus.MISSED -> c.danger
                    TaskStatus.SKIPPED -> c.textFaint
                    else -> c.outline
                },
                pulsing = task.status == TaskStatus.RUNNING,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) c.textFaint else c.text,
                )
                Text(
                    if (done && task.completedAt != null)
                        s.taskDoneAt.fmt(TimeFmt.clockOf(task.completedAt, use24h))
                    else "${TimeFmt.clockOf(task.scheduledAt, use24h)} · ${task.durationMin}${s.minutesShort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
            if (!done) {
                Text(
                    "[ ${s.taskYesDid} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    modifier = Modifier.clickable { onDone() }.padding(6.dp),
                )
            }
        }
    }
}
