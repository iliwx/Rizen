package com.rizen.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.rizen.app.R
import com.rizen.app.WakeApp
import com.rizen.app.core.i18n.StringsEn
import com.rizen.app.core.i18n.StringsFa
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.util.Channels
import com.rizen.app.core.util.NotifIds
import com.rizen.app.data.db.LogKind
import com.rizen.app.data.db.WakeSessionEntity
import com.rizen.app.data.prefs.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The thing that will not shut up.
 *
 * Runs as a foreground service with `stopWithTask="false"` so swiping the app out of
 * recents does not kill it, holds a wake lock so the CPU stays alive with the screen off,
 * and posts a full-screen-intent notification so [AlarmActivity] launches over the lock
 * screen even from a cold, dozing process.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: AlarmSoundPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: Long = -1
    private var currentSessionId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_MUTE -> {
                player?.mute()
                AlarmSessionState.setSound(false)
            }
            ACTION_UNMUTE -> {
                player?.unmute()
                AlarmSessionState.setSound(true)
            }
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> if (!AlarmSessionState.isRinging) { stopSelf(); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val sessionIdIn = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        val missionIndex = intent.getIntExtra(EXTRA_MISSION_INDEX, 0)
        if (alarmId <= 0) { stopSelf(); return }

        currentAlarmId = alarmId
        startForegroundNow()
        acquireWakeLock()

        scope.launch {
            val c = WakeApp.container(this@AlarmService)
            val alarm = c.db.alarms().byId(alarmId) ?: run { stopSelf(); return@launch }
            val settings = c.settings.settings.first()

            // Resume an in-flight session, or open a new one and freeze the mission list.
            val session = if (sessionIdIn > 0) {
                c.db.sessions().byId(sessionIdIn)
            } else {
                c.db.sessions().activeSession()
            } ?: WakeSessionEntity(
                alarmId = alarmId,
                missionsJson = MissionCodec.encode(alarm.activeMissions()),
            ).let { fresh -> fresh.copy(id = c.db.sessions().upsert(fresh)) }

            currentSessionId = session.id
            c.db.sessions().upsert(session.copy(missionIndex = missionIndex, active = true))
            c.logs.log(LogKind.ALARM_FIRED, alarm.label.ifBlank { "alarm" }, refId = alarmId)

            AlarmSessionState.begin(alarmId, session.id, missionIndex)

            player = AlarmSoundPlayer(this@AlarmService).also {
                it.start(
                    soundUri = alarm.soundUri,
                    rampSeconds = alarm.rampSeconds,
                    maxVolumePercent = alarm.maxVolumePercent,
                    vibrate = alarm.vibrate,
                    escalateVibration = alarm.escalateVibration,
                    volumeLock = settings.volumeLock,
                )
            }

            if (settings.watchdogEnabled) armWatchdog(this@AlarmService, alarmId, session.id)

            // Belt and braces: the full-screen intent normally does this, but on some OEM
            // skins a direct start from a foreground service is what actually works.
            launchMissionUi(this@AlarmService, alarmId, session.id, missionIndex)
        }
    }

    private fun startForegroundNow() {
        val s = StringsEn
        Channels.ensure(this, s)
        val full = PendingIntent.getActivity(
            this, 42,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, Channels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(s.appName)
            .setContentText(s.notifAlarmRunning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()

        ServiceCompat.startForeground(
            this, NotifIds.ALARM_FOREGROUND, n,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rizen:alarm")
            ?.apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away mid-alarm. Come straight back.
        if (AlarmSessionState.isRinging && currentAlarmId > 0) {
            val restart = Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_WATCHDOG
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, currentAlarmId)
                putExtra(AlarmReceiver.EXTRA_SESSION_ID, currentSessionId)
            }
            val pi = PendingIntent.getBroadcast(
                this, 77, restart,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            getSystemService<android.app.AlarmManager>()?.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3_000L, pi,
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun teardown() {
        player?.stop()
        player = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        AlarmSessionState.end()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.rizen.START"
        const val ACTION_MUTE = "com.rizen.MUTE"
        const val ACTION_UNMUTE = "com.rizen.UNMUTE"
        const val ACTION_STOP = "com.rizen.STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MISSION_INDEX = "mission_index"

        fun start(context: Context, alarmId: Long, sessionId: Long = -1, missionIndex: Int = 0) {
            val i = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MISSION_INDEX, missionIndex)
            }
            context.startForegroundService(i)
        }

        fun mute(context: Context) = send(context, ACTION_MUTE)
        fun unmute(context: Context) = send(context, ACTION_UNMUTE)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            runCatching {
                context.startService(Intent(context, AlarmService::class.java).setAction(action))
            }
        }

        fun launchMissionUi(context: Context, alarmId: Long, sessionId: Long, missionIndex: Int) {
            runCatching {
                context.startActivity(
                    Intent(context, AlarmActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        )
                        .putExtra(EXTRA_ALARM_ID, alarmId)
                        .putExtra(EXTRA_SESSION_ID, sessionId)
                        .putExtra(EXTRA_MISSION_INDEX, missionIndex)
                )
            }
        }

        /** Re-fires the alarm in a minute if the process gets killed mid-session. */
        fun armWatchdog(context: Context, alarmId: Long, sessionId: Long) {
            val pi = PendingIntent.getBroadcast(
                context, 78,
                Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_WATCHDOG
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmReceiver.EXTRA_SESSION_ID, sessionId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService<android.app.AlarmManager>()?.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000L, pi,
            )
        }

        fun disarmWatchdog(context: Context) {
            val pi = PendingIntent.getBroadcast(
                context, 78,
                Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WATCHDOG),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService<android.app.AlarmManager>()?.cancel(pi)
        }

        @Suppress("unused")
        fun stringsFor(lang: AppLanguage) = if (lang == AppLanguage.FA) StringsFa else StringsEn
    }
}
