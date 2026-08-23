package com.rizen.app.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rizen.app.R
import com.rizen.app.WakeApp
import com.rizen.app.core.i18n.Strings
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.i18n.StringsFa
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.model.MissionType
import com.rizen.app.core.util.Channels
import com.rizen.app.core.util.NotifIds
import com.rizen.app.data.db.LogKind
import com.rizen.app.data.db.WakeSessionEntity
import com.rizen.app.data.prefs.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Closes the biggest loophole in any alarm app: you beat the challenge, put the phone
 * down, and fall straight back asleep.
 *
 * After the missions are cleared, this fires at 5 / 10 / 20 / 30 / 60 minutes (all
 * user-configurable, and the whole thing can be switched off for people who genuinely
 * get up). Each ping gives you an answer window. Miss it and the alarm returns.
 */
class WakeCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        val index = intent.getIntExtra(EXTRA_INDEX, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 0)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = WakeApp.container(context)
                val settings = c.settings.settings.first()
                val s: Strings = if (settings.language == AppLanguage.FA) StringsFa else StringsEn
                Channels.ensure(context, s)
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                when (action) {
                    ACTION_PROMPT -> {
                        if (!settings.wakeChecksEnabled) return@launch
                        prefs.edit().putBoolean(key(sessionId, index), false).apply()
                        notifyPrompt(context, s, alarmId, sessionId, index, total,
                            settings.wakeCheckAnswerWindowSec)
                        c.scheduler.scheduleWakeCheckTimeout(
                            alarmId, sessionId, index,
                            System.currentTimeMillis() +
                                settings.wakeCheckAnswerWindowSec * 1000L,
                        )
                    }

                    ACTION_CONFIRM -> {
                        prefs.edit().putBoolean(key(sessionId, index), true).apply()
                        context.getSystemService<NotificationManager>()
                            ?.cancel(NotifIds.WAKE_CHECK + index)
                        c.logs.log(LogKind.WAKE_CHECK_OK, "check #${index + 1}", refId = alarmId)
                    }

                    ACTION_TIMEOUT -> {
                        val answered = prefs.getBoolean(key(sessionId, index), false)
                        context.getSystemService<NotificationManager>()
                            ?.cancel(NotifIds.WAKE_CHECK + index)
                        if (answered) return@launch

                        c.logs.log(LogKind.WAKE_CHECK_MISSED, "check #${index + 1}", refId = alarmId)

                        // No answer. Back to the missions — a short, sharp version.
                        val alarm = c.db.alarms().byId(alarmId)
                        val chain = alarm?.activeMissions()?.take(2)
                            ?.map { it.copy(delayBeforeMin = 0) }
                            ?.ifEmpty { null }
                            ?: listOf(MissionSpec(MissionType.MATH, reps = 2, timeLimitSec = 90))

                        val fresh = WakeSessionEntity(
                            alarmId = alarmId,
                            missionsJson = MissionCodec.encode(chain),
                        )
                        val newId = c.db.sessions().upsert(fresh)
                        AlarmService.start(context, alarmId, newId, 0)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyPrompt(
        context: Context,
        s: Strings,
        alarmId: Long,
        sessionId: Long,
        index: Int,
        total: Int,
        windowSec: Int,
    ) {
        val yes = PendingIntent.getBroadcast(
            context, 610_000 + index,
            Intent(context, WakeCheckReceiver::class.java).apply {
                action = ACTION_CONFIRM
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_INDEX, index)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, Channels.WAKE_CHECK)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setContentTitle(s.wcTitle)
            .setContentText(s.wcBody.fmt(windowSec))
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.wcBody.fmt(windowSec)))
            .setSubText(if (total > 0) "${index + 1}/$total" else null)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setTimeoutAfter(windowSec * 1000L)
            .addAction(R.drawable.ic_stat_eye, s.wcYes, yes)
            .setContentIntent(yes)
            .build()

        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(NotifIds.WAKE_CHECK + index, n)
        }
    }

    companion object {
        const val ACTION_PROMPT = "com.rizen.WAKE_CHECK"
        const val ACTION_CONFIRM = "com.rizen.WAKE_CHECK_OK"
        const val ACTION_TIMEOUT = "com.rizen.WAKE_CHECK_TIMEOUT"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TOTAL = "total"

        private const val PREFS = "wake_checks"
        private fun key(sessionId: Long, index: Int) = "answered_${sessionId}_$index"
    }
}
