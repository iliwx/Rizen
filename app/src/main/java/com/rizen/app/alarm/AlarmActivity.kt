package com.rizen.app.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.MainActivity
import com.rizen.app.WakeApp
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.MissionType
import com.rizen.app.core.model.displayName
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.prefs.AppSettings
import com.rizen.app.missions.CrackLockMission
import com.rizen.app.missions.EyeScanMission
import com.rizen.app.missions.MathMission
import com.rizen.app.missions.MemoryMission
import com.rizen.app.missions.MissionScaffold
import com.rizen.app.missions.QrMission
import com.rizen.app.missions.ShakeMission
import com.rizen.app.missions.StandUpMission
import com.rizen.app.missions.StepsMission
import com.rizen.app.missions.TypeCodeMission
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.TerminalBackground
import com.rizen.app.ui.components.TypewriterText
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeTheme
import kotlinx.coroutines.delay

/**
 * The mission runner. Launches over the lock screen, turns the display on, and refuses
 * to be dismissed by anything except finishing the ladder or the emergency code.
 */
class AlarmActivity : ComponentActivity() {

    private val engine: MissionEngine by viewModels()
    private var blockBack = true
    private var blockVolume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1)
        val sessionId = intent.getLongExtra(AlarmService.EXTRA_SESSION_ID, -1)
        val missionIndex = intent.getIntExtra(AlarmService.EXTRA_MISSION_INDEX, 0)
        engine.load(alarmId, sessionId, missionIndex)

        val settingsFlow = WakeApp.container(this).settings.settings

        setContent {
            val settings by settingsFlow.collectAsState(initial = AppSettings())
            blockBack = settings.blockBack
            blockVolume = settings.volumeLock

            WakeTheme(settings) {
                val ui by engine.ui.collectAsStateWithLifecycle()
                val emergencyOpen by engine.emergencyOpen.collectAsStateWithLifecycle()

                BackHandler(enabled = settings.blockBack) { /* deliberately swallowed */ }

                when (val state = ui) {
                    is MissionUi.Loading -> BootScreen()

                    is MissionUi.Running -> {
                        if (emergencyOpen) {
                            EmergencyScreen(
                                length = settings.emergencyCodeLength,
                                onAccepted = { engine.emergencyAccepted() },
                                onCancel = { engine.closeEmergency() },
                            )
                        } else {
                            RunningScreen(state)
                        }
                    }

                    is MissionUi.Handoff -> HandoffScreen(state) { finishAndRemoveTask() }

                    is MissionUi.Completed -> CompletedScreen(
                        state = state,
                        use24h = settings.use24h,
                        onStart = { engine.startPlanItem(it) },
                        onOpenApp = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            finishAndRemoveTask()
                        },
                        onDismiss = { finishAndRemoveTask() },
                    )
                }
            }
        }
    }

    // ── mission dispatch ────────────────────────────────────────────────────

    @Composable
    private fun RunningScreen(state: MissionUi.Running) {
        val s = LocalStrings.current
        val spec = state.spec
        val title = when (spec.type) {
            MissionType.EYE_SCAN -> s.mEyeTitle
            MissionType.STAND_UP -> s.mStandTitle
            MissionType.STEPS -> s.mStepsTitle
            MissionType.MATH -> s.mMathTitle
            MissionType.SHAKE -> s.mShakeTitle
            MissionType.MEMORY -> s.mMemoryTitle
            MissionType.TYPE_CODE -> s.mTypeTitle
            MissionType.CRACK_LOCK -> s.mGuessTitle
            MissionType.QR_SCAN -> s.mQrTitle
        }
        val brief = when (spec.type) {
            MissionType.EYE_SCAN -> s.mEyeBody
            MissionType.STAND_UP -> s.mStandBody
            MissionType.STEPS -> s.mStepsBody.fmt(spec.stepGoal)
            MissionType.MATH -> s.mMathBody
            MissionType.SHAKE -> s.mShakeBody
            MissionType.MEMORY -> s.mMemoryBody
            MissionType.TYPE_CODE -> s.mTypeBody.fmt(engine.settings.emergencyCodeLength)
            MissionType.CRACK_LOCK -> s.mGuessBody.fmt(spec.reps.coerceAtLeast(3))
            MissionType.QR_SCAN -> s.mQrBody
        }

        MissionScaffold(
            spec = spec,
            index = state.index,
            total = state.total,
            title = title,
            brief = brief,
            soundOn = state.soundOn,
            graceRemaining = state.graceRemaining,
            emergencyAvailable = state.emergencyAvailable,
            onEmergency = { engine.openEmergency() },
        ) {
            when (spec.type) {
                MissionType.EYE_SCAN -> EyeScanMission(
                    spec, engine::pass, engine::fail, engine::substituteCurrentMission
                )
                MissionType.STAND_UP -> StandUpMission(
                    spec, engine::pass, engine::fail, engine::substituteCurrentMission
                )
                MissionType.STEPS -> StepsMission(spec, engine::pass, engine::fail)
                MissionType.SHAKE -> ShakeMission(spec, engine::pass)
                MissionType.MATH -> MathMission(spec, engine::pass, engine::fail)
                MissionType.MEMORY -> MemoryMission(spec, engine::pass, engine::fail)
                MissionType.TYPE_CODE -> TypeCodeMission(
                    length = engine.settings.emergencyCodeLength,
                    onPass = engine::pass,
                )
                MissionType.CRACK_LOCK -> CrackLockMission(spec, engine::pass) { }
                MissionType.QR_SCAN -> QrMission(
                    expectedPayload = engine.settings.qrPayload,
                    onPass = engine::pass,
                    onWrong = { },
                    onSubstitute = engine::substituteCurrentMission,
                )
            }
        }
    }

    // ── anti-escape ─────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Swallow volume keys so the hardware buttons can't duck the alarm. The
        // AlarmSoundPlayer already pushes the level back up, this just stops the flicker.
        if (blockVolume && AlarmSessionState.isRinging) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        // Home button pressed mid-mission — come straight back.
        if (blockBack && AlarmSessionState.isRinging) {
            AlarmService.launchMissionUi(
                this,
                intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1),
                intent.getLongExtra(AlarmService.EXTRA_SESSION_ID, -1),
                intent.getIntExtra(AlarmService.EXTRA_MISSION_INDEX, 0),
            )
        }
        super.onUserLeaveHint()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREENS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BootScreen() {
    val c = LocalWake.current
    TerminalBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "RIZEN",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.accent,
                )
                Spacer(Modifier.height(10.dp))
                TypewriterText("initialising wake sequence…", speedMs = 24)
            }
        }
    }
}

/** Shown for a beat after clearing a mission that has a delayed successor. */
@Composable
private fun HandoffScreen(state: MissionUi.Handoff, onDone: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    LaunchedEffect(Unit) { delay(4500); onDone() }

    TerminalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.missionPassed, style = MaterialTheme.typography.headlineMedium, color = c.accent)
            Spacer(Modifier.height(14.dp))
            TypewriterText(
                s.missionSleeping.fmt("${state.minutes} ${s.minutesShort}"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            WPCard {
                SectionLabel(s.missionDelay)
                Text(
                    state.next.type.displayName(s),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    s.missionDelayHint.fmt(state.minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                )
            }
        }
    }
}

@Composable
private fun EmergencyScreen(length: Int, onAccepted: () -> Unit, onCancel: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    TerminalBackground(bloom = false) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        ) {
            Text(s.emgTitle, style = MaterialTheme.typography.headlineSmall, color = c.danger)
            Spacer(Modifier.height(6.dp))
            Text(s.emgBody, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f)) {
                TypeCodeMission(
                    length = length,
                    onPass = onAccepted,
                    allowRegenerate = false,
                )
            }
            WPButton(s.cancel, onCancel, Modifier.fillMaxWidth(), ghost = true)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CompletedScreen(
    state: MissionUi.Completed,
    use24h: Boolean,
    onStart: (TaskEntity) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    var started by remember { mutableStateOf<Long?>(null) }

    TerminalBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                if (state.viaEmergency) s.emgLogged else s.missionAllDoneTitle,
                style = MaterialTheme.typography.displaySmall,
                color = if (state.viaEmergency) c.warn else c.accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s.missionAllDoneBody.fmt(TimeFmt.humanDuration(state.elapsedMs, s)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
            )
            Spacer(Modifier.height(20.dp))

            if (state.plan.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        s.homeNothingPlanned,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                SectionLabel(s.homeTodayPlan, accent = true)
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.plan, key = { it.id }) { task ->
                        PlanRow(
                            task = task,
                            use24h = use24h,
                            isNext = task.id == state.plan.first().id,
                            started = started == task.id,
                        ) {
                            started = task.id
                            onStart(task)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WPButton(s.missionSeeMyPlan, onOpenApp, Modifier.weight(1f))
                WPButton(s.close, onDismiss, Modifier.weight(1f), ghost = true)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PlanRow(
    task: TaskEntity,
    use24h: Boolean,
    isNext: Boolean,
    started: Boolean,
    onStart: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    WPCard(highlighted = isNext, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${TimeFmt.clockOf(task.scheduledAt, use24h)} · ${task.durationMin}${s.minutesShort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
            if (isNext) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (started) c.accentSoft else c.surfaceHigh)
                        .clickable(enabled = !started) { onStart() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (started) s.taskRunning.fmt("${task.durationMin}${s.minutesShort}")
                        else s.taskStart,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                    )
                }
            }
        }
    }
}
