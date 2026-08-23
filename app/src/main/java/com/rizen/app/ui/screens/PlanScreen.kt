package com.rizen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.RoutineEntity
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.db.TaskStatus
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.Divider
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.StatusDot
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.components.WPSegmented
import com.rizen.app.ui.components.WPStepper
import com.rizen.app.ui.theme.LocalWake
import java.util.concurrent.TimeUnit

private enum class PlanTab { TODAY, TOMORROW, ROUTINE }

@Composable
fun PlanScreen(vm: AppViewModel) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today by vm.todayTasks.collectAsStateWithLifecycle()
    val tomorrow by vm.tomorrowTasks.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(PlanTab.TODAY) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var editingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var rescheduling by remember { mutableStateOf<TaskEntity?>(null) }

    val dayOffset = if (tab == PlanTab.TOMORROW) 1 else 0

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(s.tasksTitle, style = MaterialTheme.typography.headlineMedium, color = c.text)
                Spacer(Modifier.height(10.dp))
                WPSegmented(
                    options = listOf(
                        PlanTab.TODAY to s.tasksToday,
                        PlanTab.TOMORROW to s.tasksTomorrow,
                        PlanTab.ROUTINE to s.routinesTitle,
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                )
            }

            when (tab) {
                PlanTab.ROUTINE -> {
                    item {
                        Text(
                            s.routinesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textFaint,
                        )
                    }
                    if (routines.isEmpty()) {
                        item { EmptyCard(s.routineEmpty) }
                    }
                    items(routines, key = { it.id }) { r ->
                        RoutineRow(
                            routine = r,
                            onToggle = { vm.saveRoutine(r.copy(enabled = it)) },
                            onEdit = { editingRoutine = r },
                            onDelete = { vm.deleteRoutine(r) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        WPButton(
                            s.routineNew,
                            {
                                editingRoutine = RoutineEntity(
                                    title = "",
                                    minutes = 15,
                                    sortIndex = routines.size,
                                )
                            },
                            Modifier.fillMaxWidth(),
                            ghost = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        WPButton(s.homeRunRoutine, { vm.runRoutineNow(); tab = PlanTab.TODAY },
                            Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    val list = if (tab == PlanTab.TODAY) today else tomorrow
                    if (list.isEmpty()) {
                        item { EmptyCard(s.tasksEmpty) }
                    }
                    items(list, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            use24h = settings.use24h,
                            onDone = { vm.markTaskDone(task.id) },
                            onStart = { vm.startTask(task.id) },
                            onSkip = { vm.skipTask(task.id) },
                            onReschedule = { rescheduling = task },
                            onEdit = { editing = task },
                            onDelete = { vm.deleteTask(task) },
                        )
                    }
                }
            }
        }

        if (tab != PlanTab.ROUTINE) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 20.dp, bottom = 92.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable {
                        editing = TaskEntity(
                            title = "",
                            scheduledAt = TimeFmt.startOfDay() +
                                TimeUnit.DAYS.toMillis(dayOffset.toLong()) +
                                TimeUnit.HOURS.toMillis(9),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = c.bg)
            }
        }
    }

    editing?.let { task ->
        TaskEditorSheet(
            task = task,
            use24h = settings.use24h,
            onSave = { vm.saveTask(it); editing = null },
            onDismiss = { editing = null },
        )
    }

    editingRoutine?.let { r ->
        RoutineEditorSheet(
            routine = r,
            onSave = { vm.saveRoutine(it); editingRoutine = null },
            onDismiss = { editingRoutine = null },
        )
    }

    rescheduling?.let { task ->
        TimePickerSheet(
            title = s.taskPickNewTime,
            initial = task.scheduledAt,
            onPick = { vm.rescheduleTask(task.id, it); rescheduling = null },
            onDismiss = { rescheduling = null },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyCard(text: String) {
    val c = LocalWake.current
    WPCard {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    use24h: Boolean,
    onDone: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    val closed = task.status == TaskStatus.DONE || task.status == TaskStatus.SKIPPED

    WPCard(highlighted = task.status == TaskStatus.RUNNING, onClick = { open = !open }) {
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
                    task.title.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (closed) c.textFaint else c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(TimeFmt.clockOf(task.scheduledAt, use24h))
                        append(" · ${task.durationMin}${s.minutesShort}")
                        when (task.status) {
                            TaskStatus.DONE -> task.completedAt?.let {
                                append(" · ${s.taskDoneAt.fmt(TimeFmt.clockOf(it, use24h))}")
                            }
                            TaskStatus.SKIPPED -> append(" · ${s.taskSkipped}")
                            TaskStatus.MISSED -> append(" · ${s.taskMissed}")
                            else -> Unit
                        }
                        if (task.rescheduleCount > 0) append(" · ↻${task.rescheduleCount}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
            if (task.routineId != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.accentSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("R", style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
            }
        }

        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp)) {
                Divider(Modifier.padding(bottom = 10.dp))
                if (task.note.isNotBlank()) {
                    Text(task.note, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!closed) {
                        MiniAction(s.taskYesDid, c.accent, Modifier.weight(1f), onDone)
                        MiniAction(s.taskStart, c.textDim, Modifier.weight(1f), onStart)
                    }
                    MiniAction(s.taskReschedule, c.textDim, Modifier.weight(1f), onReschedule)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniAction(s.edit, c.textDim, Modifier.weight(1f), onEdit)
                    if (!closed) MiniAction(s.taskDrop, c.textDim, Modifier.weight(1f), onSkip)
                    MiniAction(s.delete, c.danger, Modifier.weight(1f), onDelete)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalWake.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceHigh)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun RoutineRow(
    routine: RoutineEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    WPCard(highlighted = routine.enabled, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    routine.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (routine.enabled) c.text else c.textFaint,
                )
                Text(
                    "${routine.minutes} ${s.minutesShort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
            Text(
                "[ ${s.delete} ]",
                style = MaterialTheme.typography.labelSmall,
                color = c.danger,
                modifier = Modifier.clickable { onDelete() }.padding(6.dp),
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Switch(
                checked = routine.enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
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
}

// ══════════════════════════════════════════════════════════════════════════════
// EDITORS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TaskEditorSheet(
    task: TaskEntity,
    use24h: Boolean,
    onSave: (TaskEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var draft by remember(task.id) { mutableStateOf(task) }
    var hour by remember(task.id) { mutableIntStateOf(TimeFmt.hourOf(task.scheduledAt)) }
    var minute by remember(task.id) { mutableIntStateOf(TimeFmt.minuteOf(task.scheduledAt)) }

    Sheet(onDismiss) {
        SectionLabel(if (task.id == 0L) s.taskNew else s.taskEditTitle, accent = true)
        TerminalField(draft.title, s.taskNameHint) { draft = draft.copy(title = it) }
        Spacer(Modifier.height(10.dp))
        TerminalField(draft.note, s.taskNote, singleLine = false) { draft = draft.copy(note = it) }
        Spacer(Modifier.height(14.dp))
        SectionLabel(s.taskTime)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                WPStepper("h", hour, { hour = it }, range = 0..23)
            }
            Box(Modifier.weight(1f)) {
                WPStepper("m", minute, { minute = it }, range = 0..59, step = 5)
            }
        }
        WPStepper(s.taskDuration, draft.durationMin, { draft = draft.copy(durationMin = it) },
            range = 1..240, step = 5, suffix = s.minutesShort)
        com.rizen.app.ui.components.WPSwitchRow(
            s.taskAsk, draft.askConfirm, { draft = draft.copy(askConfirm = it) },
            subtitle = s.taskAskHint,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WPButton(s.cancel, onDismiss, Modifier.weight(1f), ghost = true)
            WPButton(
                s.save,
                {
                    val base = TimeFmt.startOfDay(draft.scheduledAt)
                    onSave(
                        draft.copy(
                            scheduledAt = base +
                                TimeUnit.HOURS.toMillis(hour.toLong()) +
                                TimeUnit.MINUTES.toMillis(minute.toLong()),
                        )
                    )
                },
                Modifier.weight(1f),
                enabled = draft.title.isNotBlank(),
            )
        }
    }
}

@Composable
private fun RoutineEditorSheet(
    routine: RoutineEntity,
    onSave: (RoutineEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var draft by remember(routine.id) { mutableStateOf(routine) }

    Sheet(onDismiss) {
        SectionLabel(s.routineNew, accent = true)
        TerminalField(draft.title, s.routineName) { draft = draft.copy(title = it) }
        Spacer(Modifier.height(10.dp))
        WPStepper(s.routineDuration, draft.minutes, { draft = draft.copy(minutes = it) },
            range = 1..180, step = 5, suffix = s.minutesShort)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WPButton(s.cancel, onDismiss, Modifier.weight(1f), ghost = true)
            WPButton(s.save, { onSave(draft) }, Modifier.weight(1f),
                enabled = draft.title.isNotBlank())
        }
    }
}

@Composable
fun TimePickerSheet(
    title: String,
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var hour by remember { mutableIntStateOf(TimeFmt.hourOf(initial)) }
    var minute by remember { mutableIntStateOf(TimeFmt.minuteOf(initial)) }

    Sheet(onDismiss) {
        SectionLabel(title, accent = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { WPStepper("h", hour, { hour = it }, range = 0..23) }
            Box(Modifier.weight(1f)) {
                WPStepper("m", minute, { minute = it }, range = 0..59, step = 5)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WPButton(s.cancel, onDismiss, Modifier.weight(1f), ghost = true)
            WPButton(
                s.save,
                {
                    onPick(
                        TimeFmt.startOfDay(initial) +
                            TimeUnit.HOURS.toMillis(hour.toLong()) +
                            TimeUnit.MINUTES.toMillis(minute.toLong())
                    )
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val c = LocalWake.current
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(20.dp),
        ) { content() }
    }
}
