package com.rizen.app.core.model

import com.rizen.app.core.i18n.Strings
import org.json.JSONArray
import org.json.JSONObject

enum class MissionType {
    EYE_SCAN, STAND_UP, STEPS, MATH, SHAKE, MEMORY, TYPE_CODE, CRACK_LOCK, QR_SCAN;

    companion object {
        fun fromName(n: String?): MissionType? = entries.firstOrNull { it.name == n }
    }
}

enum class Difficulty { EASY, NORMAL, HARD, BRUTAL }

fun MissionType.displayName(s: Strings): String = when (this) {
    MissionType.EYE_SCAN -> s.mEyeName
    MissionType.STAND_UP -> s.mStandName
    MissionType.STEPS -> s.mStepsName
    MissionType.MATH -> s.mMathName
    MissionType.SHAKE -> s.mShakeName
    MissionType.MEMORY -> s.mMemoryName
    MissionType.TYPE_CODE -> s.mTypeName
    MissionType.CRACK_LOCK -> s.mGuessName
    MissionType.QR_SCAN -> s.mQrName
}

/** Missions that pause the alarm sound while the user is thinking. */
val MissionType.isPuzzle: Boolean
    get() = this in setOf(
        MissionType.MATH, MissionType.MEMORY, MissionType.TYPE_CODE, MissionType.CRACK_LOCK
    )

val MissionType.needsCamera: Boolean
    get() = this in setOf(MissionType.EYE_SCAN, MissionType.STAND_UP, MissionType.QR_SCAN)

/**
 * One rung of the wake-up ladder.
 *
 * [delayBeforeMin] is the trick that makes this app work: after the previous mission is
 * cleared the alarm actually goes silent, the phone is rescheduled, and the next mission
 * ambushes the user N minutes later — long enough to have drifted back off.
 */
data class MissionSpec(
    val type: MissionType,
    val enabled: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val delayBeforeMin: Int = 0,
    val holdSeconds: Int = 2,
    val reps: Int = 1,
    val stepGoal: Int = 20,
    val timeLimitSec: Int = 60,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("enabled", enabled)
        put("difficulty", difficulty.name)
        put("delay", delayBeforeMin)
        put("hold", holdSeconds)
        put("reps", reps)
        put("steps", stepGoal)
        put("limit", timeLimitSec)
    }

    /** Bumps every knob to its hardest value — used by NUCLEAR mode. */
    fun hardened(): MissionSpec = copy(
        enabled = true,
        difficulty = Difficulty.BRUTAL,
        holdSeconds = maxOf(holdSeconds, 4),
        reps = maxOf(reps, 3),
        stepGoal = maxOf(stepGoal, 40),
        timeLimitSec = maxOf(timeLimitSec, 90),
    )

    companion object {
        fun fromJson(o: JSONObject): MissionSpec? {
            val t = MissionType.fromName(o.optString("type")) ?: return null
            return MissionSpec(
                type = t,
                enabled = o.optBoolean("enabled", true),
                difficulty = runCatching { Difficulty.valueOf(o.optString("difficulty")) }
                    .getOrDefault(Difficulty.NORMAL),
                delayBeforeMin = o.optInt("delay", 0),
                holdSeconds = o.optInt("hold", 2),
                reps = o.optInt("reps", 1),
                stepGoal = o.optInt("steps", 20),
                timeLimitSec = o.optInt("limit", 60),
            )
        }

        /** Sensible starting point for a fresh alarm — the ladder from the spec. */
        fun defaultChain(): List<MissionSpec> = listOf(
            MissionSpec(MissionType.EYE_SCAN, holdSeconds = 2, timeLimitSec = 120),
            MissionSpec(MissionType.STAND_UP, delayBeforeMin = 5, holdSeconds = 3, timeLimitSec = 150),
            MissionSpec(MissionType.MATH, delayBeforeMin = 2, reps = 2, timeLimitSec = 90),
            MissionSpec(MissionType.STEPS, stepGoal = 20, timeLimitSec = 60),
            MissionSpec(MissionType.SHAKE, enabled = false, reps = 30),
            MissionSpec(MissionType.MEMORY, enabled = false, reps = 3),
            MissionSpec(MissionType.TYPE_CODE, enabled = false, timeLimitSec = 120),
            MissionSpec(MissionType.CRACK_LOCK, enabled = false, reps = 4),
            MissionSpec(MissionType.QR_SCAN, enabled = false, timeLimitSec = 300),
        )

        /** Every known mission, so the editor can always show the full menu. */
        fun ensureAllTypes(current: List<MissionSpec>): List<MissionSpec> {
            val known = current.associateBy { it.type }
            val defaults = defaultChain().associateBy { it.type }
            val ordered = current.toMutableList()
            MissionType.entries.forEach { t ->
                if (!known.containsKey(t)) {
                    ordered += (defaults[t] ?: MissionSpec(t)).copy(enabled = false)
                }
            }
            return ordered
        }
    }
}

object MissionCodec {
    fun encode(list: List<MissionSpec>): String =
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

    fun decode(json: String?): List<MissionSpec> {
        if (json.isNullOrBlank()) return MissionSpec.defaultChain()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { MissionSpec.fromJson(arr.getJSONObject(it)) }
        }.getOrElse { MissionSpec.defaultChain() }
            .ifEmpty { MissionSpec.defaultChain() }
    }
}
