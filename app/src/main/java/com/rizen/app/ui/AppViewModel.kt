package com.rizen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rizen.app.WakeApp
import com.rizen.app.alarm.AlarmScheduler
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.util.QrGen
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.AlarmEntity
import com.rizen.app.data.db.RoutineEntity
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.prefs.AppSettings
import com.rizen.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = WakeApp.container(app)
    val settingsRepo: SettingsRepository = c.settings

    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val alarms: StateFlow<List<AlarmEntity>> = c.db.alarms().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineEntity>> = c.plan.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTasks: StateFlow<List<TaskEntity>> =
        c.plan.observeDay(TimeFmt.startOfDay())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tomorrowTasks: StateFlow<List<TaskEntity>> =
        c.plan.observeDay(TimeFmt.startOfDay() + TimeUnit.DAYS.toMillis(1))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLog = c.logs.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions = c.db.sessions().observeRecent(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Millis until the soonest enabled alarm, or null. */
    val nextAlarmAt: StateFlow<Pair<AlarmEntity, Long>?> = alarms
        .map { list ->
            list.filter { it.enabled }
                .mapNotNull { a -> AlarmScheduler.nextTrigger(a)?.let { a to it } }
                .minByOrNull { it.second }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── alarms ──────────────────────────────────────────────────────────────

    suspend fun alarm(id: Long) = c.db.alarms().byId(id)

    fun saveAlarm(alarm: AlarmEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = c.db.alarms().upsert(alarm)
            val saved = c.db.alarms().byId(if (alarm.id == 0L) id else alarm.id)
            saved?.let { c.scheduler.schedule(it) }
            onSaved(id)
        }
    }

    fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            c.db.alarms().setEnabled(alarm.id, enabled)
            val fresh = c.db.alarms().byId(alarm.id) ?: return@launch
            if (enabled) c.scheduler.schedule(fresh) else c.scheduler.cancel(fresh.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            c.scheduler.cancel(alarm.id)
            c.db.alarms().delete(alarm)
        }
    }

    fun newAlarmTemplate(): AlarmEntity {
        val s = settings.value
        return AlarmEntity(
            hour = 7,
            minute = 0,
            missionsJson = s.defaultMissionsJson,
            wakeChecksEnabled = s.wakeChecksEnabled,
            emergencyExitEnabled = s.emergencyEnabled,
        )
    }

    // ── plan ────────────────────────────────────────────────────────────────

    fun saveTask(task: TaskEntity) = viewModelScope.launch { c.plan.upsertTask(task) }
    fun deleteTask(task: TaskEntity) = viewModelScope.launch { c.plan.deleteTask(task) }
    fun markTaskDone(id: Long) = viewModelScope.launch { c.plan.markDone(id) }
    fun skipTask(id: Long) = viewModelScope.launch { c.plan.skip(id) }
    fun rescheduleTask(id: Long, at: Long) = viewModelScope.launch { c.plan.reschedule(id, at) }
    fun startTask(id: Long) = viewModelScope.launch { c.plan.startTask(id) }

    fun saveRoutine(r: RoutineEntity) = viewModelScope.launch { c.plan.upsertRoutine(r) }
    fun deleteRoutine(r: RoutineEntity) = viewModelScope.launch { c.plan.deleteRoutine(r) }
    fun runRoutineNow() = viewModelScope.launch { c.plan.materialiseRoutine() }

    // ── settings ────────────────────────────────────────────────────────────

    fun updateSettings(block: suspend SettingsRepository.() -> Unit) =
        viewModelScope.launch { c.settings.block() }

    fun setDefaultMissions(list: List<MissionSpec>) =
        viewModelScope.launch { c.settings.setDefaultMissions(list) }

    fun regenerateQr(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val payload = QrGen.newPayload()
            c.settings.setQrPayload(payload)
            onDone(payload)
        }
    }

    fun wipeEverything() {
        viewModelScope.launch {
            c.db.alarms().all().forEach { c.scheduler.cancel(it.id) }
            c.db.alarms().wipe()
            c.plan.wipe()
            c.db.routines().wipe()
            c.logs.wipe()
            c.db.sessions().wipe()
        }
    }
}
