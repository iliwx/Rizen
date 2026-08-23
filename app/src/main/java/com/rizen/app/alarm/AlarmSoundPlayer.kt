package com.rizen.app.alarm

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * Owns the noise.
 *
 * Three jobs, all of which exist to beat a half-asleep person:
 *  1. ramp the volume so it starts survivable and ends unbearable,
 *  2. escalate the vibration alongside it,
 *  3. slam the volume back up the instant someone reaches for the down button.
 */
class AlarmSoundPlayer(private val context: Context) {

    private val audio: AudioManager? = context.getSystemService()
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volumeObserver: ContentObserver? = null

    private var originalVolume: Int = -1
    private var targetVolume: Int = 0
    private var rampSeconds: Int = 30
    private var rampStartedAt: Long = 0
    private var volumeLock: Boolean = true
    private var escalateVibration: Boolean = true
    private var vibrateEnabled: Boolean = true

    /** True while the alarm is audible. Puzzles flip this off temporarily. */
    var isPlaying: Boolean = false
        private set

    fun start(
        soundUri: String?,
        rampSeconds: Int,
        maxVolumePercent: Int,
        vibrate: Boolean,
        escalateVibration: Boolean,
        volumeLock: Boolean,
    ) {
        if (isPlaying) return
        this.rampSeconds = rampSeconds.coerceIn(0, 300)
        this.volumeLock = volumeLock
        this.escalateVibration = escalateVibration
        this.vibrateEnabled = vibrate

        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
        targetVolume = (max * maxVolumePercent.coerceIn(10, 100) / 100).coerceAtLeast(1)
        if (originalVolume < 0) originalVolume = audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: max

        startPlayer(soundUri)
        beginRamp()
        if (vibrate) startVibration()
        if (volumeLock) installVolumeLock()
        isPlaying = true
    }

    /** Silence without tearing anything down — used while a puzzle is on screen. */
    fun mute() {
        if (!isPlaying) return
        runCatching { player?.pause() }
        vibrator?.cancel()
        isPlaying = false
    }

    /** Comes back at full target volume: no gentle re-entry after a failed puzzle. */
    fun unmute() {
        if (isPlaying) return
        runCatching { player?.start() }
        setVolume(targetVolume)
        if (vibrateEnabled) startVibration()
        isPlaying = true
    }

    fun stop() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        removeVolumeLock()
        if (originalVolume >= 0) {
            setVolume(originalVolume)
            originalVolume = -1
        }
    }

    // ── audio ───────────────────────────────────────────────────────────────

    private fun startPlayer(soundUri: String?) {
        val uri: Uri = soundUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun beginRamp() {
        rampStartedAt = System.currentTimeMillis()
        if (rampSeconds <= 0) { setVolume(targetVolume); return }
        // Start at ~15% of target so it registers without being a jump-scare.
        val startVol = (targetVolume * 0.15f).toInt().coerceAtLeast(1)
        setVolume(startVol)
        val steps = (targetVolume - startVol).coerceAtLeast(1)
        val stepMs = (rampSeconds * 1000L / steps).coerceAtLeast(200L)
        for (i in 1..steps) {
            handler.postDelayed({
                if (isPlaying) setVolume(startVol + i)
            }, stepMs * i)
        }
    }

    private fun setVolume(v: Int) {
        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: return
        runCatching {
            audio?.setStreamVolume(AudioManager.STREAM_ALARM, v.coerceIn(0, max), 0)
        }
    }

    // ── vibration ───────────────────────────────────────────────────────────

    private fun startVibration() {
        val v = obtainVibrator() ?: return
        if (v.hasVibrator().not()) return
        vibrator = v
        val pattern = if (escalateVibration)
            longArrayOf(0, 200, 800, 350, 600, 500, 450, 700, 300, 900, 200)
        else
            longArrayOf(0, 500, 500)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun obtainVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }

    // ── volume-button lock ──────────────────────────────────────────────────

    private fun installVolumeLock() {
        if (volumeObserver != null) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (!isPlaying || !volumeLock) return
                val current = audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: return
                // Only push back down-presses; let the ramp climb in peace.
                val floor = expectedFloor()
                if (current < floor) setVolume(floor)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true, obs
            )
            volumeObserver = obs
        }
    }

    private fun expectedFloor(): Int {
        if (rampSeconds <= 0) return targetVolume
        val elapsed = (System.currentTimeMillis() - rampStartedAt) / 1000f
        val progress = (elapsed / rampSeconds).coerceIn(0f, 1f)
        val startVol = (targetVolume * 0.15f).coerceAtLeast(1f)
        return (startVol + (targetVolume - startVol) * progress).toInt().coerceAtLeast(1)
    }

    private fun removeVolumeLock() {
        volumeObserver?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        volumeObserver = null
    }
}
