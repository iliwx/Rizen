package com.rizen.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.model.MissionSpec

// ══════════════════════════════════════════════════════════════════════════════
// ALARMS
// ══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 means "fire once, then disable". */
    val daysMask: Int = 0,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val escalateVibration: Boolean = true,
    val rampSeconds: Int = 30,
    val maxVolumePercent: Int = 100,
    val snoozeAllowed: Boolean = false,
    val snoozeMinutes: Int = 5,
    val snoozeLimit: Int = 1,
    val emergencyExitEnabled: Boolean = true,
    val wakeChecksEnabled: Boolean = true,
    val nuclear: Boolean = false,
    val missionsJson: String = MissionCodec.encode(MissionSpec.defaultChain()),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val missions: List<MissionSpec> get() = MissionCodec.decode(missionsJson)

    /** The missions that will actually run, in order, with NUCLEAR applied. */
    fun activeMissions(): List<MissionSpec> {
        val base = missions.filter { it.enabled }
        return if (nuclear) missions.map { it.hardened() } else base
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TASKS  (the to-do list with real times)
// ══════════════════════════════════════════════════════════════════════════════

enum class TaskStatus { PENDING, RUNNING, DONE, SKIPPED, MISSED }

@Entity(tableName = "tasks", indices = [Index("scheduledAt")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** Epoch millis of when it should happen. */
    val scheduledAt: Long,
    val durationMin: Int = 15,
    val status: TaskStatus = TaskStatus.PENDING,
    /** The app never marks this done by itself — it asks. */
    val askConfirm: Boolean = true,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    /** Set when this task was spawned from a routine block. */
    val routineId: Long? = null,
    val sortIndex: Int = 0,
    val rescheduleCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

// ══════════════════════════════════════════════════════════════════════════════
// ROUTINES  (fixed daily blocks: wash up 15m, workout 20m, breakfast 30m …)
// ══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val minutes: Int = 15,
    val sortIndex: Int = 0,
    val enabled: Boolean = true,
    val icon: String = "dot",
)

// ══════════════════════════════════════════════════════════════════════════════
// LOG  (everything that happens, with a real timestamp)
// ══════════════════════════════════════════════════════════════════════════════

enum class LogKind {
    ALARM_FIRED, MISSION_PASSED, MISSION_FAILED, MISSION_TIMEOUT,
    EMERGENCY_EXIT, SNOOZED, WOKE_UP,
    WAKE_CHECK_OK, WAKE_CHECK_MISSED,
    TASK_DONE, TASK_SKIPPED, TASK_MISSED, TASK_RESCHEDULED, TASK_STARTED,
    ROUTINE_DONE, TIMER_DONE,
}

@Entity(tableName = "activity_log", indices = [Index("timestamp")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LogKind,
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val refId: Long? = null,
    val meta: String = "",
)

// ══════════════════════════════════════════════════════════════════════════════
// WAKE SESSION  (one morning's fight, from first ring to "you're up")
// ══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "wake_sessions")
data class WakeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Index into the alarm's active mission list. */
    val missionIndex: Int = 0,
    val failures: Int = 0,
    val snoozes: Int = 0,
    val emergencyUsed: Boolean = false,
    val active: Boolean = true,
    /** Ordered mission list frozen at fire time, so editing the alarm mid-session is safe. */
    val missionsJson: String = "",
)
