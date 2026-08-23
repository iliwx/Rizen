package com.rizen.app.data.repo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.rizen.app.alarm.TaskReceiver
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.LogKind
import com.rizen.app.data.db.RoutineDao
import com.rizen.app.data.db.RoutineEntity
import com.rizen.app.data.db.TaskDao
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.db.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Tasks + routines. The hard rule that shows up everywhere in here: **nothing is ever
 * marked done automatically.** A timer running out only triggers a question.
 */
class PlanRepository(
    private val taskDao: TaskDao,
    private val routineDao: RoutineDao,
    private val logs: LogRepository,
    private val context: Context,
) {

    // ── reads ───────────────────────────────────────────────────────────────

    fun observeDay(dayStart: Long): Flow<List<TaskEntity>> =
        taskDao.observeBetween(dayStart, dayStart + TimeUnit.DAYS.toMillis(1) - 1)

    fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observeRoutines(): Flow<List<RoutineEntity>> = routineDao.observeAll()

    suspend fun task(id: Long) = taskDao.byId(id)

    suspend fun openTasksToday(): List<TaskEntity> {
        val start = TimeFmt.startOfDay()
        return taskDao.pendingBetween(start, start + TimeUnit.DAYS.toMillis(1) - 1)
    }

    // ── task writes ─────────────────────────────────────────────────────────

    suspend fun upsertTask(task: TaskEntity): Long {
        val id = taskDao.upsert(task)
        val saved = if (task.id == 0L) task.copy(id = id) else task
        scheduleReminder(saved)
        return id
    }

    suspend fun deleteTask(task: TaskEntity) {
        cancelReminder(task.id)
        taskDao.delete(task)
    }

    suspend fun startTask(id: Long) {
        val t = taskDao.byId(id) ?: return
        val now = System.currentTimeMillis()
        taskDao.update(t.copy(status = TaskStatus.RUNNING, startedAt = now))
        logs.log(LogKind.TASK_STARTED, t.title, refId = t.id, at = now)
        // The countdown asks "did you do it?" when it expires — it never auto-completes.
        scheduleReminder(t.copy(scheduledAt = now + TimeUnit.MINUTES.toMillis(t.durationMin.toLong())))
    }

    suspend fun markDone(id: Long) {
        val t = taskDao.byId(id) ?: return
        val now = System.currentTimeMillis()
        taskDao.update(t.copy(status = TaskStatus.DONE, completedAt = now))
        cancelReminder(id)
        logs.log(
            if (t.routineId != null) LogKind.ROUTINE_DONE else LogKind.TASK_DONE,
            t.title,
            durationMs = t.startedAt?.let { now - it } ?: 0,
            refId = t.id,
            at = now,
        )
    }

    suspend fun skip(id: Long) {
        val t = taskDao.byId(id) ?: return
        taskDao.update(t.copy(status = TaskStatus.SKIPPED))
        cancelReminder(id)
        logs.log(LogKind.TASK_SKIPPED, t.title, refId = t.id)
    }

    suspend fun markMissed(id: Long) {
        val t = taskDao.byId(id) ?: return
        if (t.status != TaskStatus.PENDING && t.status != TaskStatus.RUNNING) return
        taskDao.update(t.copy(status = TaskStatus.MISSED))
        logs.log(LogKind.TASK_MISSED, t.title, refId = t.id)
    }

    suspend fun reschedule(id: Long, newTime: Long) {
        val t = taskDao.byId(id) ?: return
        val updated = t.copy(
            scheduledAt = newTime,
            status = TaskStatus.PENDING,
            startedAt = null,
            rescheduleCount = t.rescheduleCount + 1,
        )
        taskDao.update(updated)
        scheduleReminder(updated)
        logs.log(LogKind.TASK_RESCHEDULED, t.title, refId = t.id, meta = newTime.toString())
    }

    // ── routines ────────────────────────────────────────────────────────────

    suspend fun upsertRoutine(r: RoutineEntity) = routineDao.upsert(r)
    suspend fun deleteRoutine(r: RoutineEntity) = routineDao.delete(r)
    suspend fun enabledRoutines() = routineDao.enabled()

    /**
     * Turns the fixed routine blocks into real, timed tasks starting [from].
     * Called right after the wake missions are cleared. Existing routine instances for
     * today are replaced so a second wake-up doesn't duplicate the chain.
     */
    suspend fun materialiseRoutine(from: Long = System.currentTimeMillis()): List<TaskEntity> {
        val blocks = routineDao.enabled()
        if (blocks.isEmpty()) return emptyList()

        val dayStart = TimeFmt.startOfDay(from)
        val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1) - 1
        taskDao.pendingBetween(dayStart, dayEnd)
            .filter { it.routineId != null }
            .forEach { taskDao.delete(it) }

        var cursor = from
        val created = mutableListOf<TaskEntity>()
        blocks.forEachIndexed { i, b ->
            val t = TaskEntity(
                title = b.title,
                scheduledAt = cursor,
                durationMin = b.minutes,
                routineId = b.id,
                sortIndex = -1000 + i,   // routine always sorts above normal tasks
                askConfirm = true,
            )
            val id = taskDao.upsert(t)
            created += t.copy(id = id)
            cursor += TimeUnit.MINUTES.toMillis(b.minutes.toLong())
        }
        // Only the first block gets an alarm; each "done" answer chains the next one.
        created.firstOrNull()?.let { scheduleReminder(it.copy(scheduledAt = from + 1000)) }
        return created
    }

    /** After finishing a routine block, hand over to the next one. */
    suspend fun nextRoutineTask(afterId: Long): TaskEntity? {
        val dayStart = TimeFmt.startOfDay()
        val open = taskDao.pendingBetween(dayStart, dayStart + TimeUnit.DAYS.toMillis(1) - 1)
        return open.filter { it.routineId != null && it.id != afterId }
            .minByOrNull { it.sortIndex }
    }

    // ── reminder alarms ─────────────────────────────────────────────────────

    fun scheduleReminder(task: TaskEntity) {
        if (task.id == 0L) return
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.SKIPPED) return
        if (task.scheduledAt <= System.currentTimeMillis()) return
        val am = context.getSystemService<AlarmManager>() ?: return
        val pi = reminderIntent(task.id)
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.scheduledAt, pi)
        }.onFailure {
            am.set(AlarmManager.RTC_WAKEUP, task.scheduledAt, pi)
        }
    }

    fun cancelReminder(taskId: Long) {
        context.getSystemService<AlarmManager>()?.cancel(reminderIntent(taskId))
    }

    suspend fun rescheduleAllReminders() {
        taskDao.allOpen().forEach { scheduleReminder(it) }
    }

    private fun reminderIntent(taskId: Long): PendingIntent {
        val i = Intent(context, TaskReceiver::class.java).apply {
            action = TaskReceiver.ACTION_REMIND
            putExtra(TaskReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context, (900_000 + taskId).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    suspend fun wipe() {
        taskDao.allOpen().forEach { cancelReminder(it.id) }
        taskDao.wipe()
    }
}
