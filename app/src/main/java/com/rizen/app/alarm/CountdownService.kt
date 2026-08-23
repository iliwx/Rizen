package com.rizen.app.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.rizen.app.MainActivity
import com.rizen.app.R
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.util.Channels
import com.rizen.app.core.util.NotifIds
import com.rizen.app.core.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Live countdown shared with the UI. Survives the activity being destroyed. */
object TimerState {
    data class Run(
        val label: String,
        val endAt: Long,
        val totalMs: Long,
        val paused: Boolean = false,
        val pausedRemaining: Long = 0,
        /** Set when this countdown belongs to a task, so finishing can ask about it. */
        val taskId: Long? = null,
    ) {
        fun remaining(now: Long = System.currentTimeMillis()): Long =
            if (paused) pausedRemaining else (endAt - now).coerceAtLeast(0)

        fun progress(now: Long = System.currentTimeMillis()): Float =
            if (totalMs <= 0) 0f else 1f - (remaining(now).toFloat() / totalMs)
    }

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run.asStateFlow()

    internal fun set(r: Run?) { _run.value = r }
    internal fun current() = _run.value
}

/**
 * One countdown at a time, running in a foreground service so it keeps ticking with the
 * screen off. Used by the standalone timer, routine blocks and task timers alike.
 */
class CountdownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                val ms = intent.getLongExtra(EXTRA_MILLIS, 0)
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it > 0 }
                if (ms <= 0) { stopSelf(); return START_NOT_STICKY }
                TimerState.set(
                    TimerState.Run(
                        label = label,
                        endAt = System.currentTimeMillis() + ms,
                        totalMs = ms,
                        taskId = taskId,
                    )
                )
                goForeground()
                tick()
            }

            ACTION_ADD -> {
                val extra = intent.getLongExtra(EXTRA_MILLIS, 0)
                TimerState.current()?.let {
                    TimerState.set(it.copy(endAt = it.endAt + extra, totalMs = it.totalMs + extra))
                }
            }

            ACTION_PAUSE -> TimerState.current()?.let {
                if (!it.paused) TimerState.set(
                    it.copy(paused = true, pausedRemaining = it.remaining())
                )
            }

            ACTION_RESUME -> TimerState.current()?.let {
                if (it.paused) TimerState.set(
                    it.copy(
                        paused = false,
                        endAt = System.currentTimeMillis() + it.pausedRemaining,
                    )
                )
            }

            ACTION_STOP -> {
                TimerState.set(null)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val run = TimerState.current() ?: break
                if (!run.paused && run.remaining() <= 0L) {
                    finish(run)
                    break
                }
                notifyProgress(run)
                delay(1000)
            }
        }
    }

    private fun finish(run: TimerState.Run) {
        val s = StringsEn
        val builder = NotificationCompat.Builder(this, Channels.WAKE_CHECK)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(s.timerFinished.fmt(run.label.ifBlank { s.timerTitle }))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)

        // A task countdown never self-completes — it turns into a question.
        run.taskId?.let { id ->
            builder.setContentTitle(s.taskDidYouDoIt)
                .setContentText(run.label)
                .addAction(
                    R.drawable.ic_stat_task, s.taskYesDid,
                    broadcast(TaskReceiver.ACTION_DONE, id)
                )
                .addAction(
                    R.drawable.ic_stat_task, s.taskNotYet,
                    broadcast(TaskReceiver.ACTION_NOT_YET, id)
                )
        }

        runCatching {
            NotificationManagerCompat.from(this).notify(NotifIds.TIMER_DONE, builder.build())
        }
        TimerState.set(null)
        stopSelf()
    }

    private fun broadcast(action: String, taskId: Long) = PendingIntent.getBroadcast(
        this, (action.hashCode() and 0xFFF) * 1000 + taskId.toInt(),
        Intent(this, TaskReceiver::class.java).apply {
            this.action = action
            putExtra(TaskReceiver.EXTRA_TASK_ID, taskId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun goForeground() {
        Channels.ensure(this, StringsEn)
        ServiceCompat.startForeground(
            this, NotifIds.TIMER_FOREGROUND, buildNotification(TimerState.current()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun notifyProgress(run: TimerState.Run) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotifIds.TIMER_FOREGROUND, buildNotification(run))
        }
    }

    private fun buildNotification(run: TimerState.Run?): android.app.Notification {
        val s = StringsEn
        val open = PendingIntent.getActivity(
            this, 91,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Channels.TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(run?.label?.ifBlank { s.timerTitle } ?: s.timerTitle)
            .setContentText(TimeFmt.hhmmss(run?.remaining() ?: 0))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.rizen.TIMER_START"
        const val ACTION_STOP = "com.rizen.TIMER_STOP"
        const val ACTION_PAUSE = "com.rizen.TIMER_PAUSE"
        const val ACTION_RESUME = "com.rizen.TIMER_RESUME"
        const val ACTION_ADD = "com.rizen.TIMER_ADD"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MILLIS = "millis"
        const val EXTRA_TASK_ID = "task_id"

        fun start(context: Context, label: String, millis: Long, taskId: Long? = null) {
            context.startForegroundService(
                Intent(context, CountdownService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_MILLIS, millis)
                    taskId?.let { putExtra(EXTRA_TASK_ID, it) }
                }
            )
        }

        fun add(context: Context, millis: Long) = simple(context, ACTION_ADD, millis)
        fun pause(context: Context) = simple(context, ACTION_PAUSE)
        fun resume(context: Context) = simple(context, ACTION_RESUME)
        fun stop(context: Context) = simple(context, ACTION_STOP)

        private fun simple(context: Context, action: String, millis: Long = 0) {
            runCatching {
                context.startService(
                    Intent(context, CountdownService::class.java)
                        .setAction(action)
                        .putExtra(EXTRA_MILLIS, millis)
                )
            }
        }
    }
}
