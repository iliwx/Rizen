package com.rizen.app.alarm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rizen.app.WakeApp
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.model.MissionType
import com.rizen.app.core.model.isPuzzle
import com.rizen.app.data.db.AlarmEntity
import com.rizen.app.data.db.LogKind
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.db.WakeSessionEntity
import com.rizen.app.data.prefs.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

sealed interface MissionUi {
    data object Loading : MissionUi

    data class Running(
        val spec: MissionSpec,
        val index: Int,
        val total: Int,
        val soundOn: Boolean,
        val graceRemaining: Int,
        val emergencyAvailable: Boolean,
    ) : MissionUi

    /** Alarm has gone quiet; the next mission ambushes the user in [minutes]. */
    data class Handoff(val next: MissionSpec, val minutes: Int) : MissionUi

    data class Completed(
        val elapsedMs: Long,
        val plan: List<TaskEntity>,
        val viaEmergency: Boolean,
    ) : MissionUi
}

/**
 * Runs one morning's ladder of missions.
 *
 * The interesting behaviour lives in [pass]: clearing a mission that has a delay before
 * the next one genuinely stops the alarm and reschedules. That gap is the point — an
 * alarm you beat while still horizontal hasn't woken you up.
 */
class MissionEngine(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app.applicationContext
    private val container = WakeApp.container(app)

    private val _ui = MutableStateFlow<MissionUi>(MissionUi.Loading)
    val ui: StateFlow<MissionUi> = _ui.asStateFlow()

    private val _emergencyOpen = MutableStateFlow(false)
    val emergencyOpen: StateFlow<Boolean> = _emergencyOpen.asStateFlow()

    lateinit var settings: AppSettings
        private set

    private var alarm: AlarmEntity? = null
    private var session: WakeSessionEntity? = null
    private var missions: List<MissionSpec> = emptyList()
    private var index = 0
    private var graceJob: Job? = null
    private var graceLeft = 0
    private var soundOn = true

    fun load(alarmId: Long, sessionId: Long, startIndex: Int) {
        viewModelScope.launch {
            settings = container.settings.settings.first()
            alarm = container.db.alarms().byId(alarmId)
            val s = (if (sessionId > 0) container.db.sessions().byId(sessionId) else null)
                ?: container.db.sessions().activeSession()
                ?: WakeSessionEntity(
                    alarmId = alarmId,
                    missionsJson = MissionCodec.encode(
                        alarm?.activeMissions() ?: MissionSpec.defaultChain().filter { it.enabled }
                    ),
                ).let { it.copy(id = container.db.sessions().upsert(it)) }

            session = s
            missions = MissionCodec.decode(s.missionsJson).filter { it.enabled }
                .ifEmpty { alarm?.activeMissions().orEmpty() }
                .filter { it.type != MissionType.QR_SCAN || settings.qrMissionEnabled }
            index = startIndex.coerceIn(0, maxOf(missions.size - 1, 0))

            if (missions.isEmpty()) { finish(viaEmergency = false); return@launch }
            enterMission()
        }
    }

    // ── mission lifecycle ───────────────────────────────────────────────────

    private fun enterMission() {
        val spec = missions.getOrNull(index) ?: run { finish(false); return }
        graceJob?.cancel()

        if (spec.type.isPuzzle) {
            // Thinking is impossible with an alarm in your ear, so it stops — but only
            // for a fixed window. Run out of time and the noise is back while you solve.
            AlarmService.mute(appContext)
            soundOn = false
            graceLeft = settings.puzzleGraceSeconds
            graceJob = viewModelScope.launch {
                while (graceLeft > 0) {
                    publish(spec)
                    delay(1000)
                    graceLeft--
                }
                AlarmService.unmute(appContext)
                soundOn = true
                publish(spec)
            }
        } else {
            AlarmService.unmute(appContext)
            soundOn = true
            graceLeft = 0
        }
        publish(spec)
    }

    private fun publish(spec: MissionSpec) {
        _ui.value = MissionUi.Running(
            spec = spec,
            index = index,
            total = missions.size,
            soundOn = soundOn,
            graceRemaining = graceLeft,
            emergencyAvailable = settings.emergencyEnabled &&
                alarm?.emergencyExitEnabled == true &&
                alarm?.nuclear != true,
        )
    }

    /** Called by a mission screen when the user actually did the thing. */
    fun pass() {
        val spec = missions.getOrNull(index) ?: return
        graceJob?.cancel()
        viewModelScope.launch {
            container.logs.log(LogKind.MISSION_PASSED, spec.type.name, refId = session?.id)

            val nextIndex = index + 1
            if (nextIndex >= missions.size) { finish(false); return@launch }

            val next = missions[nextIndex]
            index = nextIndex
            session = session?.copy(missionIndex = nextIndex)?.also {
                container.db.sessions().upsert(it)
            }

            if (next.delayBeforeMin > 0) {
                // Genuine silence, then an ambush. The user is free to lie back down —
                // that's the trap.
                AlarmService.stop(appContext)
                AlarmSessionState.end()
                container.scheduler.scheduleResume(
                    alarmId = alarm?.id ?: return@launch,
                    sessionId = session?.id ?: return@launch,
                    missionIndex = nextIndex,
                    atMillis = System.currentTimeMillis() +
                        TimeUnit.MINUTES.toMillis(next.delayBeforeMin.toLong()),
                )
                _ui.value = MissionUi.Handoff(next, next.delayBeforeMin)
            } else {
                enterMission()
            }
        }
    }

    fun fail(reason: String = "") {
        val spec = missions.getOrNull(index) ?: return
        viewModelScope.launch {
            container.logs.log(LogKind.MISSION_FAILED, spec.type.name, meta = reason,
                refId = session?.id)
            session = session?.copy(failures = (session?.failures ?: 0) + 1)?.also {
                container.db.sessions().upsert(it)
            }
        }
        // Failing never advances anything: the alarm comes straight back on.
        AlarmService.unmute(appContext)
        soundOn = true
        graceLeft = 0
        graceJob?.cancel()
        publish(spec)
    }

    /** Some cameras genuinely don't work. Swap the current mission for a puzzle. */
    fun substituteCurrentMission() {
        val spec = missions.getOrNull(index) ?: return
        val replacement = MissionSpec(
            type = MissionType.CRACK_LOCK,
            difficulty = spec.difficulty,
            reps = 4,
            timeLimitSec = 120,
        )
        missions = missions.toMutableList().also { it[index] = replacement }
        viewModelScope.launch {
            container.logs.log(LogKind.MISSION_FAILED, "${spec.type.name}_SUBSTITUTED")
        }
        enterMission()
    }

    // ── endings ─────────────────────────────────────────────────────────────

    fun openEmergency() { _emergencyOpen.value = true }
    fun closeEmergency() { _emergencyOpen.value = false }

    fun emergencyAccepted() {
        viewModelScope.launch {
            container.logs.log(LogKind.EMERGENCY_EXIT, alarm?.label.orEmpty(), refId = alarm?.id)
            session = session?.copy(emergencyUsed = true)
            finish(viaEmergency = true)
        }
    }

    private fun finish(viaEmergency: Boolean) {
        viewModelScope.launch {
            graceJob?.cancel()
            val now = System.currentTimeMillis()
            val s = session
            val started = s?.startedAt ?: now

            AlarmService.stop(appContext)
            AlarmService.disarmWatchdog(appContext)
            AlarmSessionState.end()
            s?.let {
                container.db.sessions().upsert(
                    it.copy(completedAt = now, active = false, missionIndex = missions.size)
                )
            }
            container.logs.log(
                LogKind.WOKE_UP,
                alarm?.label.orEmpty(),
                durationMs = now - started,
                refId = alarm?.id,
            )

            // Escalating "still awake?" pings — unless the user opted out.
            if (!viaEmergency && settings.wakeChecksEnabled && alarm?.wakeChecksEnabled == true) {
                container.scheduler.scheduleWakeChecks(
                    alarmId = alarm?.id ?: 0L,
                    sessionId = s?.id ?: 0L,
                    minutesAfter = settings.wakeCheckMinutes,
                    from = now,
                )
            }

            // Morning routine turns into real timed tasks starting right now.
            val plan = if (!viaEmergency && settings.routineAutoStart) {
                container.plan.materialiseRoutine(now)
            } else emptyList()

            val rest = container.plan.openTasksToday().filter { it.routineId == null }

            _ui.value = MissionUi.Completed(
                elapsedMs = now - started,
                plan = plan + rest,
                viaEmergency = viaEmergency,
            )
        }
    }

    /** Kick off the first routine block from the completion screen. */
    fun startPlanItem(task: TaskEntity) {
        viewModelScope.launch {
            container.plan.startTask(task.id)
            CountdownService.start(
                appContext,
                task.title,
                TimeUnit.MINUTES.toMillis(task.durationMin.toLong()),
                task.id,
            )
        }
    }

    override fun onCleared() {
        graceJob?.cancel()
        super.onCleared()
    }
}
