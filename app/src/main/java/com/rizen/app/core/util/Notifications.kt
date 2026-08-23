package com.rizen.app.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.core.content.getSystemService
import com.rizen.app.core.i18n.Strings

object Channels {
    const val ALARM = "wp_alarm"
    const val WAKE_CHECK = "wp_wake_check"
    const val TASKS = "wp_tasks"
    const val TIMER = "wp_timer"

    fun ensure(context: Context, s: Strings) {
        val nm = context.getSystemService<NotificationManager>() ?: return

        // The alarm channel is silent on purpose: AlarmService owns the audio so it can
        // ramp volume, loop, and duck for puzzles. A channel sound would fight it.
        nm.createNotificationChannel(
            NotificationChannel(ALARM, s.chanAlarmName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.chanAlarmDesc
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                WAKE_CHECK, s.chanCheckName, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = s.chanCheckDesc
                setBypassDnd(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    android.media.RingtoneManager
                        .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(TASKS, s.chanTaskName, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = s.chanTaskDesc }
        )

        nm.createNotificationChannel(
            NotificationChannel(TIMER, s.chanTimerName, NotificationManager.IMPORTANCE_LOW).apply {
                description = s.chanTimerDesc
                setSound(null, null)
            }
        )
    }
}

object NotifIds {
    const val ALARM_FOREGROUND = 1001
    const val TIMER_FOREGROUND = 1002
    const val WAKE_CHECK = 1100
    const val TIMER_DONE = 1200
    const val TASK_BASE = 5000   // + task id
}
