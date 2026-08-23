package com.rizen.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rizen.app.WakeApp
import com.rizen.app.core.util.DayMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        val missionIndex = intent.getIntExtra(EXTRA_MISSION_INDEX, 0)
        if (alarmId <= 0) return

        when (intent.action) {
            ACTION_FIRE -> {
                // Start the noise first, ask questions later — the user is asleep and the
                // OS gives a background receiver only a few seconds of grace.
                AlarmService.start(context, alarmId, sessionId, missionIndex)
                rearmRepeating(context, alarmId, isResume = sessionId > 0)
            }

            ACTION_WATCHDOG -> {
                if (!AlarmSessionState.isRinging) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val c = WakeApp.container(context)
                            val session = if (sessionId > 0) c.db.sessions().byId(sessionId)
                            else c.db.sessions().activeSession()
                            if (session != null && session.active && session.completedAt == null) {
                                AlarmService.start(
                                    context, alarmId, session.id, session.missionIndex
                                )
                            }
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    /**
     * Puts the *next* occurrence on the clock immediately, so a crash later in the
     * morning can never cost tomorrow's alarm. One-shot alarms disable themselves.
     */
    private fun rearmRepeating(context: Context, alarmId: Long, isResume: Boolean) {
        if (isResume) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = WakeApp.container(context)
                val alarm = c.db.alarms().byId(alarmId) ?: return@launch
                if (alarm.daysMask == DayMask.NONE) {
                    c.db.alarms().setEnabled(alarmId, false)
                    c.scheduler.cancel(alarmId)
                } else {
                    c.scheduler.schedule(alarm)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rizen.FIRE"
        const val ACTION_WATCHDOG = "com.rizen.WATCHDOG"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MISSION_INDEX = "mission_index"
    }
}
