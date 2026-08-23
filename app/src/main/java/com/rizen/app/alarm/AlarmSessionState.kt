package com.rizen.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one piece of shared mutable state between [AlarmService] (owns the noise) and
 * [AlarmActivity] (owns the missions). Kept as a process singleton rather than a bound
 * service because the activity can be recreated by the OS at any moment during a mission
 * and rebinding mid-scan would be a visible stutter.
 */
object AlarmSessionState {

    data class Ringing(
        val alarmId: Long,
        val sessionId: Long,
        val missionIndex: Int,
        val startedAt: Long,
        val soundOn: Boolean,
    )

    private val _state = MutableStateFlow<Ringing?>(null)
    val state: StateFlow<Ringing?> = _state.asStateFlow()

    val isRinging: Boolean get() = _state.value != null

    fun begin(alarmId: Long, sessionId: Long, missionIndex: Int) {
        _state.value = Ringing(
            alarmId = alarmId,
            sessionId = sessionId,
            missionIndex = missionIndex,
            startedAt = System.currentTimeMillis(),
            soundOn = true,
        )
    }

    fun setSound(on: Boolean) {
        _state.value = _state.value?.copy(soundOn = on)
    }

    fun setMissionIndex(index: Int) {
        _state.value = _state.value?.copy(missionIndex = index)
    }

    fun end() {
        _state.value = null
    }
}
