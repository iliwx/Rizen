package com.rizen.app.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Bilingual copy deck for WAKE PROTOCOL.
 *
 * English is baked in as the default value of every field, so a missing Persian
 * translation degrades to English instead of breaking the build. Persian lives in
 * [StringsFa]. Tone rule for BOTH languages: talk like a friend who is dragging you
 * out of bed, never like a system dialog.
 *
 * Templates use {0}, {1}, ... placeholders — fill them with [fmt].
 */
open class Strings {

    // ── generic ─────────────────────────────────────────────────────────────
    open val appName: String = "RIZEN"
    open val ok: String = "OK"
    open val cancel: String = "Cancel"
    open val save: String = "Save"
    open val delete: String = "Delete"
    open val edit: String = "Edit"
    open val add: String = "Add"
    open val next: String = "Next"
    open val back: String = "Back"
    open val skip: String = "Skip"
    open val done: String = "Done"
    open val yes: String = "Yes"
    open val no: String = "Nope"
    open val start: String = "Start"
    open val stop: String = "Stop"
    open val pause: String = "Pause"
    open val resume: String = "Resume"
    open val reset: String = "Reset"
    open val close: String = "Close"
    open val retry: String = "Try again"
    open val on: String = "on"
    open val off: String = "off"
    open val minutesShort: String = "min"
    open val secondsShort: String = "s"
    open val hoursShort: String = "h"
    open val today: String = "Today"
    open val tomorrow: String = "Tomorrow"
    open val none: String = "None"
    open val everyDay: String = "Every day"
    open val weekdays: String = "Weekdays"
    open val weekends: String = "Weekends"
    open val onceOnly: String = "Once"

    // ── nav ─────────────────────────────────────────────────────────────────
    open val navHome: String = "home"
    open val navAlarms: String = "alarms"
    open val navTasks: String = "plan"
    open val navTimer: String = "timer"
    open val navStats: String = "stats"
    open val navSettings: String = "config"

    // ── onboarding ──────────────────────────────────────────────────────────
    open val obTitle1: String = "you can't snooze this one"
    open val obBody1: String = "Wake Protocol doesn't have a dismiss button. It has missions. " +
        "You finish them, the noise stops. That's the whole deal."

    open val obTitle2: String = "your eyes have to prove it"
    open val obBody2: String = "The camera opens, you look at it, and it checks your eyes are actually " +
        "open. Squinting through one eyelid won't fly."

    open val obTitle3: String = "then you get on your feet"
    open val obBody3: String = "Five minutes later it rings again and asks you to stand inside an " +
        "outline on the screen. Lying down does not look like standing."

    open val obTitle4: String = "and it keeps checking"
    open val obBody4: String = "Steps, math, shaking, a 13-character code, a lock you have to crack — " +
        "pick whichever ones you want, in whatever order."

    open val obTitle5: String = "morning actually starts here"
    open val obBody5: String = "Once you're up, your routine and your to-do list kick in with timers, " +
        "and the app asks you afterwards whether you really did it."

    open val obTitle6: String = "one last thing"
    open val obBody6: String = "It needs a few permissions to do any of this. Skip them and the app " +
        "turns into a very stubborn clock that can't see you."

    open val obGrant: String = "Grant permissions"
    open val obLetsGo: String = "Let's go"

    // ── home ────────────────────────────────────────────────────────────────
    open val greetNight: String = "still up?"
    open val greetMorning: String = "morning."
    open val greetDay: String = "hey."
    open val greetEvening: String = "evening."
    open val homeNextAlarm: String = "next alarm"
    open val homeNoAlarm: String = "nothing scheduled"
    open val homeRingsIn: String = "rings in {0}"
    open val homeTodayPlan: String = "today's plan"
    open val homeNothingPlanned: String = "nothing planned yet — add something"
    open val homeTasksLeft: String = "{0} left"
    open val homeAllDone: String = "all clear. nice."
    open val homeQuickTimer: String = "quick timer"
    open val homeRunRoutine: String = "run morning routine"
    open val homeAddAlarm: String = "new alarm"

    // ── alarms ──────────────────────────────────────────────────────────────
    open val alarmsTitle: String = "alarms"
    open val alarmsEmpty: String = "no alarms yet. that's brave."
    open val alarmNew: String = "new alarm"
    open val alarmEditTitle: String = "edit alarm"
    open val alarmLabel: String = "label"
    open val alarmLabelHint: String = "e.g. gym, work, do not be late"
    open val alarmRepeat: String = "repeat"
    open val alarmSound: String = "sound & feel"
    open val alarmRamp: String = "volume ramp-up"
    open val alarmRampHint: String = "starts quiet, gets loud over {0}s"
    open val alarmMaxVolume: String = "max volume"
    open val alarmVibrate: String = "vibrate"
    open val alarmVibrateEscalate: String = "escalating vibration"
    open val alarmSnooze: String = "allow snooze"
    open val alarmSnoozeHint: String = "off means off. there is no snooze."
    open val alarmSnoozeMinutes: String = "snooze length"
    open val alarmSnoozeLimit: String = "max snoozes"
    open val alarmEmergency: String = "emergency exit"
    open val alarmEmergencyHint: String = "type a 13-character code to kill the alarm. " +
        "logged in your stats, so you'll see yourself cheating."

    open val alarmWakeCheck: String = "keep checking after I'm up"
    open val alarmWakeCheckHint: String = "asks \"still awake?\" at {0} minutes. " +
        "no answer, the alarm comes back."

    open val alarmNuclear: String = "NUCLEAR mode"
    open val alarmNuclearHint: String = "every mission, back to back, hardest setting, no exit."
    open val alarmDeleteConfirm: String = "delete this alarm?"
    open val alarmSavedIn: String = "saved — rings in {0}"

    // ── mission editor ──────────────────────────────────────────────────────
    open val missionsTitle: String = "wake missions"
    open val missionsHint: String = "drag to reorder. these run top to bottom."
    open val missionsNoneWarn: String = "no missions = a normal boring alarm. really?"
    open val missionDelay: String = "wait before this one"
    open val missionDelayHint: String = "alarm goes quiet, then comes back after {0} min"
    open val missionDifficulty: String = "difficulty"
    open val missionReps: String = "repetitions"
    open val missionHoldSec: String = "hold for"
    open val missionStepGoal: String = "step goal"
    open val missionTimeLimit: String = "time limit"
    open val diffEasy: String = "easy"
    open val diffNormal: String = "normal"
    open val diffHard: String = "hard"
    open val diffBrutal: String = "brutal"

    // ── mission names (used in lists / stats) ───────────────────────────────
    open val mEyeName: String = "eye scan"
    open val mStandName: String = "stand up"
    open val mStepsName: String = "walk it off"
    open val mMathName: String = "math"
    open val mShakeName: String = "shake"
    open val mMemoryName: String = "memory"
    open val mTypeName: String = "type the code"
    open val mGuessName: String = "crack the lock"
    open val mQrName: String = "find the QR"

    // ── mission runtime copy ────────────────────────────────────────────────
    open val mEyeTitle: String = "OPEN THOSE EYES"
    open val mEyeBody: String = "look straight at the camera. squinting through one eyelid doesn't count."
    open val mStandTitle: String = "ON YOUR FEET"
    open val mStandBody: String = "stand up and fill the outline. yes, actually stand."
    open val mStepsTitle: String = "GO WALK IT OFF"
    open val mStepsBody: String = "{0} steps. I'm counting. shaking the phone won't work."
    open val mMathTitle: String = "BRAIN CHECK"
    open val mMathBody: String = "solve it before the noise comes back."
    open val mShakeTitle: String = "SHAKE IT"
    open val mShakeBody: String = "wake your arms up too. shake like you mean it."
    open val mMemoryTitle: String = "REMEMBER THIS"
    open val mMemoryBody: String = "watch the pattern, then play it back."
    open val mTypeTitle: String = "TYPE IT EXACTLY"
    open val mTypeBody: String = "{0} characters. every one turns green or red as you go."
    open val mGuessTitle: String = "CRACK THE LOCK"
    open val mGuessBody: String = "{0} slots, four options each. wrong is fine — just keep going."
    open val mQrTitle: String = "GO FIND THE CODE"
    open val mQrBody: String = "the one you taped to the bathroom mirror. off you go."
    open val missionStep: String = "mission {0} of {1}"
    open val missionPassed: String = "LOCKED IN"
    open val missionFailed: String = "not quite"
    open val missionHoldOn: String = "hold it… {0}"
    open val missionGrace: String = "alarm's quiet for {0}s"
    open val missionGraceWarn: String = "{0}s and the noise is back"
    open val missionAlarmReturning: String = "too slow. here it comes again."
    open val missionSleeping: String = "back to sleep in {0} — nice try"
    open val missionAllDoneTitle: String = "you're up."
    open val missionAllDoneBody: String = "took you {0}. here's what's next."
    open val missionSeeMyPlan: String = "show my plan"
    open val missionCameraNeeded: String = "camera permission, please"
    open val missionCameraNeededBody: String = "this mission is literally the camera looking at you."
    open val missionNoStepSensor: String = "no step sensor here — using motion instead"
    open val missionSkipToAlt: String = "use another mission"

    // ── eye scan ────────────────────────────────────────────────────────────
    open val eyeSearching: String = "looking for a face…"
    open val eyeFound: String = "got you"
    open val eyeClosed: String = "eyes closed. open them."
    open val eyeHalf: String = "more than that"
    open val eyeOpen: String = "eyes open — hold it"
    open val eyeTooDark: String = "too dark, move to some light"
    open val eyeLocked: String = "EYES CONFIRMED"

    // ── stand up ────────────────────────────────────────────────────────────
    open val standSearching: String = "step back so I can see all of you"
    open val standTooClose: String = "too close — back up"
    open val standTooFar: String = "come a bit closer"
    open val standNotStanding: String = "that's not standing and we both know it"
    open val standAlign: String = "line yourself up with the outline"
    open val standAligning: String = "almost — {0}%"
    open val standHolding: String = "hold still…"
    open val standLocked: String = "UPRIGHT CONFIRMED"
    open val standPropUp: String = "prop the phone up or hold it out at arm's length"

    // ── steps ───────────────────────────────────────────────────────────────
    open val stepsGo: String = "start walking"
    open val stepsCount: String = "{0} / {1} steps"
    open val stepsTimeLeft: String = "{0} left"
    open val stepsCheating: String = "that's a wrist, not a leg. walk."
    open val stepsLocked: String = "STEPS CONFIRMED"
    open val stepsRanOut: String = "time's up. alarm's back on."

    // ── math ────────────────────────────────────────────────────────────────
    open val mathSolve: String = "solve it"
    open val mathWrong: String = "nope"
    open val mathRight: String = "yep"
    open val mathQuestionN: String = "question {0} of {1}"

    // ── shake ───────────────────────────────────────────────────────────────
    open val shakeGo: String = "shake the phone"
    open val shakeHarder: String = "harder than that"
    open val shakeLocked: String = "SHAKE CONFIRMED"

    // ── memory ──────────────────────────────────────────────────────────────
    open val memoryWatch: String = "watch"
    open val memoryRepeat: String = "now repeat it"
    open val memoryWrong: String = "wrong one — from the top"
    open val memoryRound: String = "round {0} of {1}"

    // ── typing ──────────────────────────────────────────────────────────────
    open val typeCopyThis: String = "copy this, exactly"
    open val typeYourTurn: String = "your turn"
    open val typeMismatch: String = "{0} wrong"
    open val typePerfect: String = "perfect"
    open val typeNewCode: String = "different code"
    open val typeLocked: String = "CODE ACCEPTED"

    // ── guess / crack the lock ──────────────────────────────────────────────
    open val guessPick: String = "pick the right one for slot {0}"
    open val guessWrong: String = "not that one"
    open val guessRight: String = "slot {0} locked"
    open val guessAttempts: String = "{0} guesses"
    open val guessLocked: String = "LOCK OPENED"

    // ── qr ──────────────────────────────────────────────────────────────────
    open val qrLookingFor: String = "point the camera at your code"
    open val qrWrongCode: String = "wrong code — that's not yours"
    open val qrLocked: String = "CODE FOUND"
    open val qrNoCodeSet: String = "you haven't generated a code yet"

    // ── emergency exit ──────────────────────────────────────────────────────
    open val emgTitle: String = "emergency exit"
    open val emgBody: String = "kills the alarm right now. type the code below without a single mistake."
    open val emgConfirm: String = "kill the alarm"
    open val emgWrong: String = "not even close"
    open val emgLogged: String = "used the emergency exit. it's in your stats."
    open val emgDisabled: String = "you turned this off. no way out but forward."

    // ── still-awake checks ──────────────────────────────────────────────────
    open val wcTitle: String = "still awake?"
    open val wcBody: String = "tap yes within {0}s or the alarm comes back."
    open val wcYes: String = "yeah, I'm up"
    open val wcMissed: String = "no answer. alarm's back."
    open val wcNextIn: String = "next check in {0} min"
    open val wcAllClear: String = "checks done. you survived."

    // ── tasks ───────────────────────────────────────────────────────────────
    open val tasksTitle: String = "plan"
    open val tasksToday: String = "today"
    open val tasksTomorrow: String = "tomorrow"
    open val tasksEmpty: String = "nothing here. add what tomorrow-you has to do."
    open val taskNew: String = "new task"
    open val taskEditTitle: String = "edit task"
    open val taskName: String = "what"
    open val taskNameHint: String = "e.g. finish the report"
    open val taskNote: String = "note"
    open val taskTime: String = "when"
    open val taskDuration: String = "how long"
    open val taskAsk: String = "ask me if I did it"
    open val taskAskHint: String = "never marks itself done. it asks, you answer."
    open val taskDidYouDoIt: String = "did you do it?"
    open val taskDidYouDoItBody: String = "\"{0}\" was set for {1}."
    open val taskYesDid: String = "did it"
    open val taskNotYet: String = "not yet"
    open val taskReschedule: String = "new time"
    open val taskDrop: String = "drop it"
    open val taskStart: String = "start"
    open val taskRunning: String = "running — {0} left"
    open val taskDoneAt: String = "done at {0}"
    open val taskSkipped: String = "skipped"
    open val taskMissed: String = "missed"
    open val taskPending: String = "pending"
    open val taskPickNewTime: String = "when instead?"

    // ── routines ────────────────────────────────────────────────────────────
    open val routinesTitle: String = "morning routine"
    open val routinesHint: String = "the fixed stuff. runs automatically once you're up, " +
        "before your to-do list."

    open val routineEmpty: String = "no routine yet"
    open val routineNew: String = "new block"
    open val routineName: String = "block"
    open val routineDuration: String = "minutes"
    open val routineAutoStart: String = "auto-start after waking"
    open val routineNowRunning: String = "now: {0}"
    open val routineUpNext: String = "up next: {0}"
    open val routineFinishedAsk: String = "{0} — done?"
    open val routineChainDone: String = "routine finished. onto the real list."

    // ── timer ───────────────────────────────────────────────────────────────
    open val timerTitle: String = "timer"
    open val timerNone: String = "nothing running"
    open val timerSet: String = "set a timer"
    open val timerLabelHint: String = "what for?"
    open val timerFinished: String = "{0} — time's up"
    open val timerAdd1: String = "+1m"
    open val timerAdd5: String = "+5m"
    open val timerAdd10: String = "+10m"

    // ── stats ───────────────────────────────────────────────────────────────
    open val statsTitle: String = "stats"
    open val statsDay: String = "day"
    open val statsWeek: String = "week"
    open val statsMonth: String = "month"
    open val statsEmpty: String = "nothing logged yet. come back after a morning or two."
    open val statsWokeAt: String = "woke at"
    open val statsTookYou: String = "took you"
    open val statsAvgWake: String = "average wake-up"
    open val statsStreak: String = "streak"
    open val statsStreakDays: String = "{0} days"
    open val statsSnoozes: String = "escapes used"
    open val statsFailed: String = "missions failed"
    open val statsTasksDone: String = "tasks done"
    open val statsHardest: String = "your nemesis"
    open val statsTimeline: String = "24h timeline"
    open val statsLog: String = "log"
    open val statsNoNemesis: String = "none yet"

    // ── settings ────────────────────────────────────────────────────────────
    open val setTitle: String = "config"
    open val setLanguage: String = "language"
    open val setLangEn: String = "English"
    open val setLangFa: String = "فارسی"
    open val setGeneral: String = "general"
    open val setWaking: String = "waking up"
    open val setDefaultMissions: String = "default missions for new alarms"
    open val setGlobalDifficulty: String = "overall difficulty"
    open val setGraceSeconds: String = "puzzle silence window"
    open val setGraceHint: String = "while you're solving something the alarm shuts up for {0}s. " +
        "run out of time and it starts again."

    open val setWakeChecks: String = "still-awake checks"
    open val setWakeCheckSchedule: String = "check at (minutes after waking)"
    open val setWakeCheckAnswerWindow: String = "answer window"
    open val setEmergency: String = "emergency exit code"
    open val setEmergencyLen: String = "code length"
    open val setDefence: String = "anti-cheat"
    open val setVolumeLock: String = "lock the volume buttons"
    open val setVolumeLockHint: String = "turn it down and it turns itself right back up."
    open val setBlockBack: String = "block back & recents"
    open val setWatchdog: String = "restart if force-closed"
    open val setWatchdogHint: String = "kill the app mid-alarm and it comes back in a minute."
    open val setQrMission: String = "QR mission"
    open val setQrGenerate: String = "make a QR code"
    open val setRoutineAuto: String = "auto-start routine after waking"
    open val set24h: String = "24-hour clock"
    open val setPermissions: String = "permissions"
    open val setBattery: String = "ignore battery optimisation"
    open val setExactAlarm: String = "exact alarms"
    open val setNotifications: String = "notifications"
    open val setCamera: String = "camera"
    open val setActivity: String = "physical activity"
    open val setGranted: String = "granted"
    open val setMissing: String = "needed"
    open val setReplayTutorial: String = "show the intro again"
    open val setWipe: String = "wipe everything"
    open val setWipeConfirm: String = "delete every alarm, task and stat? no undo."
    open val setAbout: String = "about"
    open val setAboutBody: String = "Rizen — an alarm that refuses to be dismissed. " +
        "Everything runs on-device: no account, no cloud, no data leaving your phone."

    // ── QR generator ────────────────────────────────────────────────────────
    open val qrGenTitle: String = "your QR code"
    open val qrGenBody: String = "print it, tape it somewhere far from bed — bathroom mirror, kettle, " +
        "front door. In the morning you have to physically go scan it."

    open val qrGenNew: String = "generate a new one"
    open val qrGenSave: String = "save image"
    open val qrGenShare: String = "share / print"
    open val qrGenSaved: String = "saved to your gallery folder"
    open val qrGenWarning: String = "new code invalidates the old printout."

    // ── permission rationale ────────────────────────────────────────────────
    open val permCameraWhy: String = "to check your eyes are open and that you're standing up"
    open val permActivityWhy: String = "to count your steps"
    open val permNotifWhy: String = "to ring, and to ask if you did your tasks"
    open val permExactWhy: String = "to fire at the exact minute, not \"sometime around then\""
    open val permBatteryWhy: String = "so Android doesn't quietly kill the alarm overnight"
    open val permOpenSettings: String = "open settings"

    // ── notification channels ───────────────────────────────────────────────
    open val chanAlarmName: String = "Alarm"
    open val chanAlarmDesc: String = "The alarm itself. Do not mute this one."
    open val chanCheckName: String = "Still-awake checks"
    open val chanCheckDesc: String = "Follow-up nudges after you've woken up."
    open val chanTaskName: String = "Tasks & routine"
    open val chanTaskDesc: String = "Reminders and \"did you do it?\" prompts."
    open val chanTimerName: String = "Timers"
    open val chanTimerDesc: String = "Running countdowns."
    open val notifAlarmRunning: String = "Alarm running — finish your missions"
    open val notifTapToOpen: String = "Tap to open"
}

/** Replaces {0}, {1}, … placeholders. */
fun String.fmt(vararg args: Any?): String {
    var out = this
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}

val LocalStrings = staticCompositionLocalOf { Strings() }
