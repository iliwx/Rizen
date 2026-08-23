package com.rizen.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.rizen.app.MainActivity
import com.rizen.app.core.util.DayMask
import com.rizen.app.data.db.AlarmDao
import com.rizen.app.data.db.AlarmEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Everything that puts a future event on the OS clock.
 *
 * Uses [AlarmManager.setAlarmClock] for the real alarm — it is the only API Android will
 * not defer under Doze, and it also puts the alarm icon in the status bar, which is a
 * useful "yes, this is armed" signal for the user.
 */
class AlarmScheduler(private val context: Context) {

    private val am: AlarmManager? get() = context.getSystemService()

    // ── main alarm ──────────────────────────────────────────────────────────

    fun schedule(alarm: AlarmEntity): Long? {
        if (!alarm.enabled) { cancel(alarm.id); return null }
        val at = nextTrigger(alarm) ?: return null
        val show = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fire = firePendingIntent(alarm.id)
        val manager = am ?: return null
        runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        }.onFailure {
            // SCHEDULE_EXACT_ALARM revoked — degrade instead of crashing at 3am.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
        return at
    }

    fun cancel(alarmId: Long) {
        am?.cancel(firePendingIntent(alarmId))
    }

    suspend fun rescheduleAll(dao: AlarmDao) {
        dao.all().forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    /** Snooze, when the user has explicitly allowed themselves that luxury. */
    fun snooze(alarmId: Long, minutes: Int, sessionId: Long, missionIndex: Int) {
        val at = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        scheduleResume(alarmId, sessionId, missionIndex, at)
    }

    /**
     * The signature move: the alarm goes genuinely silent and comes back later for the
     * next mission, when the user has had time to drift off again.
     */
    fun scheduleResume(alarmId: Long, sessionId: Long, missionIndex: Int, atMillis: Long) {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmReceiver.EXTRA_MISSION_INDEX, missionIndex)
        }
        val pi = PendingIntent.getBroadcast(
            context, RESUME_BASE + sessionId.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        exact(atMillis, pi)
    }

    fun cancelResume(sessionId: Long) {
        val i = Intent(context, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_FIRE }
        am?.cancel(
            PendingIntent.getBroadcast(
                context, RESUME_BASE + sessionId.toInt(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
    }

    // ── still-awake checks ──────────────────────────────────────────────────

    /**
     * Schedules the escalating "still awake?" pings at [minutesAfter] minutes past the
     * moment the user finished their missions. This is the fix for the obvious hole in
     * any alarm app: clearing the challenge and then rolling straight back over.
     */
    fun scheduleWakeChecks(alarmId: Long, sessionId: Long, minutesAfter: List<Int>, from: Long) {
        minutesAfter.forEachIndexed { index, minutes ->
            val at = from + TimeUnit.MINUTES.toMillis(minutes.toLong())
            if (at <= System.currentTimeMillis()) return@forEachIndexed
            val i = Intent(context, WakeCheckReceiver::class.java).apply {
                action = WakeCheckReceiver.ACTION_PROMPT
                putExtra(WakeCheckReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(WakeCheckReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(WakeCheckReceiver.EXTRA_INDEX, index)
                putExtra(WakeCheckReceiver.EXTRA_TOTAL, minutesAfter.size)
            }
            exact(at, checkPendingIntent(WAKE_CHECK_BASE + index, i))
        }
    }

    fun scheduleWakeCheckTimeout(alarmId: Long, sessionId: Long, index: Int, atMillis: Long) {
        val i = Intent(context, WakeCheckReceiver::class.java).apply {
            action = WakeCheckReceiver.ACTION_TIMEOUT
            putExtra(WakeCheckReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(WakeCheckReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(WakeCheckReceiver.EXTRA_INDEX, index)
        }
        exact(atMillis, checkPendingIntent(WAKE_CHECK_TIMEOUT_BASE + index, i))
    }

    fun cancelWakeChecks(total: Int = 12) {
        repeat(total) { index ->
            am?.cancel(
                checkPendingIntent(
                    WAKE_CHECK_BASE + index,
                    Intent(context, WakeCheckReceiver::class.java)
                        .setAction(WakeCheckReceiver.ACTION_PROMPT)
                )
            )
            am?.cancel(
                checkPendingIntent(
                    WAKE_CHECK_TIMEOUT_BASE + index,
                    Intent(context, WakeCheckReceiver::class.java)
                        .setAction(WakeCheckReceiver.ACTION_TIMEOUT)
                )
            )
        }
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private fun exact(at: Long, pi: PendingIntent) {
        val manager = am ?: return
        runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            .onFailure { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun checkPendingIntent(requestCode: Int, intent: Intent) =
        PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun firePendingIntent(alarmId: Long) = PendingIntent.getBroadcast(
        context, alarmId.toInt(),
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val RESUME_BASE = 300_000
        private const val WAKE_CHECK_BASE = 400_000
        private const val WAKE_CHECK_TIMEOUT_BASE = 450_000

        /** Next epoch-millis this alarm should ring, or null if it never will. */
        fun nextTrigger(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long? {
            val base = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (alarm.daysMask == DayMask.NONE) {
                if (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
                return base.timeInMillis
            }
            for (offset in 0..7) {
                val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                val idx = DayMask.indexFromCalendar(c.get(Calendar.DAY_OF_WEEK))
                if (DayMask.has(alarm.daysMask, idx) && c.timeInMillis > now) return c.timeInMillis
            }
            return null
        }
    }
}
