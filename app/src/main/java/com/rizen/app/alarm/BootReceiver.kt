package com.rizen.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rizen.app.WakeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms live in the OS clock, and the OS clock is wiped by a reboot, a clock change, or
 * an app update. Every one of those events rebuilds the whole schedule from the database.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = WakeApp.container(context)
                c.scheduler.rescheduleAll(c.db.alarms())
                c.plan.rescheduleAllReminders()

                // If the phone rebooted mid-fight, pick the fight straight back up.
                val session = c.db.sessions().activeSession()
                if (session != null && session.completedAt == null) {
                    AlarmService.start(context, session.alarmId, session.id, session.missionIndex)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
