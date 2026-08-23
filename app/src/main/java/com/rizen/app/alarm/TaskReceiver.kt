package com.rizen.app.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.rizen.app.MainActivity
import com.rizen.app.R
import com.rizen.app.WakeApp
import com.rizen.app.core.i18n.Strings
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.i18n.StringsFa
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.util.Channels
import com.rizen.app.core.util.NotifIds
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.TaskEntity
import com.rizen.app.data.prefs.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Task reminders and the "did you do it?" prompt.
 *
 * Deliberate design rule: a task is **never** marked done because its timer ran out.
 * The countdown expiring only earns you a question. You answer it, and the answer is
 * stamped with the real time it was given.
 */
class TaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId <= 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = WakeApp.container(context)
                val settings = c.settings.settings.first()
                val s = if (settings.language == AppLanguage.FA) StringsFa else StringsEn
                Channels.ensure(context, s)
                val task = c.plan.task(taskId) ?: return@launch
                val nm = context.getSystemService<NotificationManager>()

                when (action) {
                    ACTION_REMIND -> notifyAsk(context, s, task, settings.use24h)

                    ACTION_DONE -> {
                        nm?.cancel(NotifIds.TASK_BASE + taskId.toInt())
                        c.plan.markDone(taskId)
                        // Routine blocks chain: finishing one starts the next automatically.
                        if (task.routineId != null && settings.routineAutoStart) {
                            c.plan.nextRoutineTask(taskId)?.let { next ->
                                c.plan.startTask(next.id)
                                notifyRoutineHandoff(context, s, next)
                            }
                        }
                    }

                    ACTION_NOT_YET -> {
                        nm?.cancel(NotifIds.TASK_BASE + taskId.toInt())
                        // Push it ten minutes and ask again. It does not go away.
                        c.plan.reschedule(
                            taskId,
                            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10),
                        )
                    }

                    ACTION_SKIP -> {
                        nm?.cancel(NotifIds.TASK_BASE + taskId.toInt())
                        c.plan.skip(taskId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyAsk(context: Context, s: Strings, task: TaskEntity, use24h: Boolean) {
        val body = s.taskDidYouDoItBody.fmt(
            task.title,
            TimeFmt.clockOf(task.scheduledAt, use24h),
        )
        val n = NotificationCompat.Builder(context, Channels.TASKS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(s.taskDidYouDoIt)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_task, s.taskYesDid, act(context, ACTION_DONE, task.id))
            .addAction(R.drawable.ic_stat_task, s.taskNotYet, act(context, ACTION_NOT_YET, task.id))
            .addAction(R.drawable.ic_stat_task, s.taskDrop, act(context, ACTION_SKIP, task.id))
            .setContentIntent(openApp(context, task.id))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NotifIds.TASK_BASE + task.id.toInt(), n)
        }
    }

    private fun notifyRoutineHandoff(context: Context, s: Strings, next: TaskEntity) {
        val n = NotificationCompat.Builder(context, Channels.TASKS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(s.routineUpNext.fmt(next.title))
            .setContentText("${next.durationMin} ${s.minutesShort}")
            .setAutoCancel(true)
            .setContentIntent(openApp(context, next.id))
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NotifIds.TASK_BASE + next.id.toInt(), n)
        }
    }

    private fun act(context: Context, action: String, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action.hashCode() and 0xFFFF) * 100_000 + taskId.toInt(),
            Intent(context, TaskReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, 800_000 + taskId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_REMIND = "com.rizen.TASK_REMIND"
        const val ACTION_DONE = "com.rizen.TASK_DONE"
        const val ACTION_NOT_YET = "com.rizen.TASK_NOT_YET"
        const val ACTION_SKIP = "com.rizen.TASK_SKIP"
        const val EXTRA_TASK_ID = "task_id"
    }
}
