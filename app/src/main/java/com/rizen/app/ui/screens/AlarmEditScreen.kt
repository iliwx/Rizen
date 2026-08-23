package com.rizen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.model.MissionType
import com.rizen.app.core.model.displayName
import com.rizen.app.core.util.DayMask
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.AlarmEntity
import com.rizen.app.data.prefs.AppLanguage
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.Divider
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.components.WPSegmented
import com.rizen.app.ui.components.WPStepper
import com.rizen.app.ui.components.WPSwitchRow
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeShape

@Composable
fun AlarmEditScreen(vm: AppViewModel, alarmId: Long, onDone: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf<AlarmEntity?>(null) }
    var missions by remember { mutableStateOf<List<MissionSpec>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        val loaded = if (alarmId > 0) vm.alarm(alarmId) else null
        val a = loaded ?: vm.newAlarmTemplate()
        draft = a
        missions = MissionSpec.ensureAllTypes(a.missions)
    }

    val alarm = draft ?: return
    fun update(block: AlarmEntity.() -> AlarmEntity) { draft = alarm.block() }

    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (to !in missions.indices) return
        missions = missions.toMutableList().also {
            val item = it.removeAt(index)
            it.add(to, item)
        }
    }

    fun replace(index: Int, updated: MissionSpec) {
        missions = missions.toMutableList().also { it[index] = updated }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── header ───────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (alarmId > 0) s.alarmEditTitle else s.alarmNew,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[ ${s.cancel} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                    modifier = Modifier.clickable { onDone() }.padding(8.dp),
                )
            }
        }

        // ── time ─────────────────────────────────────────────────────
        item {
            WPCard(highlighted = true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeWheel(alarm.hour, 0..23) { update { copy(hour = it) } }
                    Text(":", style = MaterialTheme.typography.displayMedium, color = c.textFaint)
                    TimeWheel(alarm.minute, 0..59) { update { copy(minute = it) } }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    TimeFmt.clock(alarm.hour, alarm.minute, settings.use24h) +
                        if (settings.use24h) "" else " ${TimeFmt.meridiem(alarm.hour)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── days ─────────────────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.alarmRepeat)
                val labels = if (settings.language == AppLanguage.FA) DayMask.labelsFa
                else DayMask.labelsEn
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { i, d ->
                        val on = DayMask.has(alarm.daysMask, i)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(if (on) c.accentSoft else c.surfaceHigh)
                                .clickable { update { copy(daysMask = DayMask.toggle(daysMask, i)) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) c.accent else c.textFaint,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        s.everyDay to DayMask.EVERY_DAY,
                        s.weekdays to DayMask.WEEKDAYS,
                        s.weekends to DayMask.WEEKENDS,
                        s.onceOnly to DayMask.NONE,
                    ).forEach { (label, mask) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (alarm.daysMask == mask) c.accentSoft else c.surfaceHigh
                                )
                                .clickable { update { copy(daysMask = mask) } }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alarm.daysMask == mask) c.accent else c.textFaint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ── label ────────────────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.alarmLabel)
                TerminalField(
                    value = alarm.label,
                    placeholder = s.alarmLabelHint,
                ) { update { copy(label = it) } }
            }
        }

        // ── missions ─────────────────────────────────────────────────
        item {
            Column {
                SectionLabel(s.missionsTitle, accent = true)
                Text(
                    s.missionsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                )
                if (missions.none { it.enabled }) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.missionsNoneWarn,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.warn,
                    )
                }
            }
        }

        missions.forEachIndexed { index, spec ->
            item(key = "mission_${spec.type.name}") {
                MissionEditorCard(
                    spec = spec,
                    order = index,
                    canMoveUp = index > 0,
                    canMoveDown = index < missions.lastIndex,
                    onMoveUp = { move(index, -1) },
                    onMoveDown = { move(index, 1) },
                    onChange = { updated -> replace(index, updated) },
                )
            }
        }

        // ── sound ────────────────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.alarmSound)
                WPStepper(
                    s.alarmRamp, alarm.rampSeconds, { update { copy(rampSeconds = it) } },
                    range = 0..180, step = 5, suffix = s.secondsShort,
                )
                Text(
                    s.alarmRampHint.fmt(alarm.rampSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                )
                Divider(Modifier.padding(vertical = 8.dp))
                WPStepper(
                    s.alarmMaxVolume, alarm.maxVolumePercent,
                    { update { copy(maxVolumePercent = it) } },
                    range = 20..100, step = 5, suffix = "%",
                )
                Divider(Modifier.padding(vertical = 8.dp))
                WPSwitchRow(
                    s.alarmVibrate, alarm.vibrate,
                    onCheckedChange = { v -> update { copy(vibrate = v) } },
                )
                WPSwitchRow(
                    s.alarmVibrateEscalate, alarm.escalateVibration,
                    { update { copy(escalateVibration = it) } },
                    enabled = alarm.vibrate,
                )
            }
        }

        // ── escape policy ────────────────────────────────────────────
        item {
            WPCard {
                SectionLabel(s.setDefence)
                WPSwitchRow(
                    s.alarmSnooze, alarm.snoozeAllowed,
                    { update { copy(snoozeAllowed = it) } },
                    subtitle = s.alarmSnoozeHint,
                )
                AnimatedVisibility(alarm.snoozeAllowed) {
                    Column {
                        WPStepper(
                            s.alarmSnoozeMinutes, alarm.snoozeMinutes,
                            { update { copy(snoozeMinutes = it) } },
                            range = 1..30, suffix = s.minutesShort,
                        )
                        WPStepper(
                            s.alarmSnoozeLimit, alarm.snoozeLimit,
                            { update { copy(snoozeLimit = it) } },
                            range = 1..10,
                        )
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))
                WPSwitchRow(
                    s.alarmEmergency, alarm.emergencyExitEnabled,
                    { update { copy(emergencyExitEnabled = it) } },
                    subtitle = s.alarmEmergencyHint,
                    enabled = !alarm.nuclear,
                )
                Divider(Modifier.padding(vertical = 8.dp))
                WPSwitchRow(
                    s.alarmWakeCheck, alarm.wakeChecksEnabled,
                    { update { copy(wakeChecksEnabled = it) } },
                    subtitle = s.alarmWakeCheckHint.fmt(
                        settings.wakeCheckMinutes.joinToString(" / ")
                    ),
                )
                Divider(Modifier.padding(vertical = 8.dp))
                WPSwitchRow(
                    s.alarmNuclear, alarm.nuclear,
                    { update { copy(nuclear = it, emergencyExitEnabled = if (it) false else emergencyExitEnabled) } },
                    subtitle = s.alarmNuclearHint,
                )
            }
        }

        // ── actions ──────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WPButton(
                    s.save,
                    {
                        vm.saveAlarm(
                            alarm.copy(
                                missionsJson = MissionCodec.encode(missions),
                                enabled = true,
                            )
                        )
                        onDone()
                    },
                    Modifier.fillMaxWidth(),
                )
                if (alarmId > 0) {
                    if (confirmDelete) {
                        WPButton(
                            s.alarmDeleteConfirm,
                            { vm.deleteAlarm(alarm); onDone() },
                            Modifier.fillMaxWidth(),
                            danger = true,
                        )
                    } else {
                        WPButton(
                            s.delete, { confirmDelete = true },
                            Modifier.fillMaxWidth(), ghost = true, danger = true,
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MISSION EDITOR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MissionEditorCard(
    spec: MissionSpec,
    order: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onChange: (MissionSpec) -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    WPCard(highlighted = spec.enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(order + 1),
                style = MaterialTheme.typography.labelMedium,
                color = if (spec.enabled) c.accent else c.textFaint,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(
                    spec.type.displayName(s),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (spec.enabled) c.text else c.textFaint,
                )
                Text(
                    buildString {
                        append(
                            when (spec.difficulty) {
                                Difficulty.EASY -> s.diffEasy
                                Difficulty.NORMAL -> s.diffNormal
                                Difficulty.HARD -> s.diffHard
                                Difficulty.BRUTAL -> s.diffBrutal
                            }
                        )
                        if (spec.delayBeforeMin > 0)
                            append(" · +${spec.delayBeforeMin}${s.minutesShort}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
            ArrowChip("↑", canMoveUp, onMoveUp)
            Spacer(Modifier.width(4.dp))
            ArrowChip("↓", canMoveDown, onMoveDown)
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Switch(
                checked = spec.enabled,
                onCheckedChange = { onChange(spec.copy(enabled = it)) },
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

        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                Divider(Modifier.padding(bottom = 10.dp))

                SectionLabel(s.missionDifficulty)
                WPSegmented(
                    options = listOf(
                        Difficulty.EASY to s.diffEasy,
                        Difficulty.NORMAL to s.diffNormal,
                        Difficulty.HARD to s.diffHard,
                        Difficulty.BRUTAL to s.diffBrutal,
                    ),
                    selected = spec.difficulty,
                    onSelect = { onChange(spec.copy(difficulty = it)) },
                )

                Spacer(Modifier.height(10.dp))
                WPStepper(
                    s.missionDelay, spec.delayBeforeMin,
                    { onChange(spec.copy(delayBeforeMin = it)) },
                    range = 0..60, suffix = s.minutesShort,
                )
                if (spec.delayBeforeMin > 0) {
                    Text(
                        s.missionDelayHint.fmt(spec.delayBeforeMin),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textFaint,
                    )
                }

                when (spec.type) {
                    MissionType.EYE_SCAN, MissionType.STAND_UP -> WPStepper(
                        s.missionHoldSec, spec.holdSeconds,
                        { onChange(spec.copy(holdSeconds = it)) },
                        range = 1..10, suffix = s.secondsShort,
                    )
                    MissionType.STEPS -> WPStepper(
                        s.missionStepGoal, spec.stepGoal,
                        { onChange(spec.copy(stepGoal = it)) },
                        range = 5..200, step = 5,
                    )
                    MissionType.MATH, MissionType.MEMORY, MissionType.SHAKE,
                    MissionType.CRACK_LOCK,
                    -> WPStepper(
                        s.missionReps, spec.reps,
                        { onChange(spec.copy(reps = it)) },
                        range = 1..40,
                    )
                    else -> Unit
                }

                WPStepper(
                    s.missionTimeLimit, spec.timeLimitSec,
                    { onChange(spec.copy(timeLimitSec = it)) },
                    range = 15..600, step = 15, suffix = s.secondsShort,
                )
            }
        }
    }
}

@Composable
private fun ArrowChip(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalWake.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceHigh)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) c.textDim else c.outline,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SMALL INPUTS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimeWheel(value: Int, range: IntRange, onValue: (Int) -> Unit) {
    val c = LocalWake.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "▲",
            style = MaterialTheme.typography.labelMedium,
            color = c.textFaint,
            modifier = Modifier
                .clickable { onValue(if (value >= range.last) range.first else value + 1) }
                .padding(10.dp),
        )
        Text(
            "%02d".format(value),
            style = MaterialTheme.typography.displayLarge,
            color = c.text,
        )
        Text(
            "▼",
            style = MaterialTheme.typography.labelMedium,
            color = c.textFaint,
            modifier = Modifier
                .clickable { onValue(if (value <= range.first) range.last else value - 1) }
                .padding(10.dp),
        )
    }
}

@Composable
fun TerminalField(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onValue: (String) -> Unit,
) {
    val c = LocalWake.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WakeShape.radiusSmall))
            .background(c.surfaceHigh)
            .border(1.dp, c.outline, RoundedCornerShape(WakeShape.radiusSmall))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.textFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = c.text,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
