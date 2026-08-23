package com.rizen.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionCodec
import com.rizen.app.core.model.MissionSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("wake_settings")

enum class AppLanguage { EN, FA }

enum class AccentTheme(val key: String) {
    PHOSPHOR("phosphor"),   // soft terminal green
    AMBER("amber"),         // old CRT amber
    ICE("ice"),             // cyan
    MAGENTA("magenta");     // synthwave

    companion object {
        fun from(k: String?) = entries.firstOrNull { it.key == k } ?: PHOSPHOR
    }
}

/**
 * Everything the user is allowed to bend. Nothing about how hard the app fights you is
 * hard-coded — it all lands here.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.EN,
    val use24h: Boolean = true,
    val accent: AccentTheme = AccentTheme.PHOSPHOR,
    val onboardingDone: Boolean = false,

    val globalDifficulty: Difficulty = Difficulty.NORMAL,
    val defaultMissionsJson: String = MissionCodec.encode(MissionSpec.defaultChain()),

    /** While a puzzle mission is on screen the alarm shuts up for this long. */
    val puzzleGraceSeconds: Int = 30,

    val wakeChecksEnabled: Boolean = true,
    /** Minutes AFTER waking at which "still awake?" fires. */
    val wakeCheckMinutes: List<Int> = listOf(5, 10, 20, 30, 60),
    val wakeCheckAnswerWindowSec: Int = 90,

    val emergencyEnabled: Boolean = true,
    val emergencyCodeLength: Int = 13,

    val volumeLock: Boolean = true,
    val blockBack: Boolean = true,
    val watchdogEnabled: Boolean = true,

    val qrMissionEnabled: Boolean = false,
    val qrPayload: String = "",

    val routineAutoStart: Boolean = true,
) {
    val defaultMissions: List<MissionSpec> get() = MissionCodec.decode(defaultMissionsJson)
}

class SettingsRepository(private val context: Context) {

    private object K {
        val LANG = stringPreferencesKey("lang")
        val H24 = booleanPreferencesKey("h24")
        val ACCENT = stringPreferencesKey("accent")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DIFF = stringPreferencesKey("difficulty")
        val DEFAULT_MISSIONS = stringPreferencesKey("default_missions")
        val GRACE = intPreferencesKey("grace_seconds")
        val WC_ON = booleanPreferencesKey("wake_checks")
        val WC_MINUTES = stringPreferencesKey("wake_check_minutes")
        val WC_WINDOW = intPreferencesKey("wake_check_window")
        val EMG_ON = booleanPreferencesKey("emergency_on")
        val EMG_LEN = intPreferencesKey("emergency_len")
        val VOL_LOCK = booleanPreferencesKey("volume_lock")
        val BLOCK_BACK = booleanPreferencesKey("block_back")
        val WATCHDOG = booleanPreferencesKey("watchdog")
        val QR_ON = booleanPreferencesKey("qr_on")
        val QR_PAYLOAD = stringPreferencesKey("qr_payload")
        val ROUTINE_AUTO = booleanPreferencesKey("routine_auto")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            language = runCatching { AppLanguage.valueOf(p[K.LANG] ?: "") }
                .getOrDefault(d.language),
            use24h = p[K.H24] ?: d.use24h,
            accent = AccentTheme.from(p[K.ACCENT]),
            onboardingDone = p[K.ONBOARDED] ?: d.onboardingDone,
            globalDifficulty = runCatching { Difficulty.valueOf(p[K.DIFF] ?: "") }
                .getOrDefault(d.globalDifficulty),
            defaultMissionsJson = p[K.DEFAULT_MISSIONS] ?: d.defaultMissionsJson,
            puzzleGraceSeconds = p[K.GRACE] ?: d.puzzleGraceSeconds,
            wakeChecksEnabled = p[K.WC_ON] ?: d.wakeChecksEnabled,
            wakeCheckMinutes = p[K.WC_MINUTES]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: d.wakeCheckMinutes,
            wakeCheckAnswerWindowSec = p[K.WC_WINDOW] ?: d.wakeCheckAnswerWindowSec,
            emergencyEnabled = p[K.EMG_ON] ?: d.emergencyEnabled,
            emergencyCodeLength = p[K.EMG_LEN] ?: d.emergencyCodeLength,
            volumeLock = p[K.VOL_LOCK] ?: d.volumeLock,
            blockBack = p[K.BLOCK_BACK] ?: d.blockBack,
            watchdogEnabled = p[K.WATCHDOG] ?: d.watchdogEnabled,
            qrMissionEnabled = p[K.QR_ON] ?: d.qrMissionEnabled,
            qrPayload = p[K.QR_PAYLOAD] ?: d.qrPayload,
            routineAutoStart = p[K.ROUTINE_AUTO] ?: d.routineAutoStart,
        )
    }

    suspend fun setLanguage(v: AppLanguage) = put { it[K.LANG] = v.name }
    suspend fun set24h(v: Boolean) = put { it[K.H24] = v }
    suspend fun setAccent(v: AccentTheme) = put { it[K.ACCENT] = v.key }
    suspend fun setOnboardingDone(v: Boolean) = put { it[K.ONBOARDED] = v }
    suspend fun setDifficulty(v: Difficulty) = put { it[K.DIFF] = v.name }
    suspend fun setDefaultMissions(v: List<MissionSpec>) =
        put { it[K.DEFAULT_MISSIONS] = MissionCodec.encode(v) }
    suspend fun setGrace(v: Int) = put { it[K.GRACE] = v.coerceIn(5, 300) }
    suspend fun setWakeChecks(v: Boolean) = put { it[K.WC_ON] = v }
    suspend fun setWakeCheckMinutes(v: List<Int>) =
        put { it[K.WC_MINUTES] = v.filter { m -> m > 0 }.sorted().joinToString(",") }
    suspend fun setWakeCheckWindow(v: Int) = put { it[K.WC_WINDOW] = v.coerceIn(15, 600) }
    suspend fun setEmergencyEnabled(v: Boolean) = put { it[K.EMG_ON] = v }
    suspend fun setEmergencyLength(v: Int) = put { it[K.EMG_LEN] = v.coerceIn(4, 40) }
    suspend fun setVolumeLock(v: Boolean) = put { it[K.VOL_LOCK] = v }
    suspend fun setBlockBack(v: Boolean) = put { it[K.BLOCK_BACK] = v }
    suspend fun setWatchdog(v: Boolean) = put { it[K.WATCHDOG] = v }
    suspend fun setQrMission(v: Boolean) = put { it[K.QR_ON] = v }
    suspend fun setQrPayload(v: String) = put { it[K.QR_PAYLOAD] = v }
    suspend fun setRoutineAutoStart(v: Boolean) = put { it[K.ROUTINE_AUTO] = v }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
