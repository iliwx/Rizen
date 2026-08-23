# WAKE PROTOCOL — آلارمی که نمی‌ذاره بخوابی

An Android alarm app with no dismiss button. To silence it you have to prove you're awake:
open your eyes at the camera, stand up inside a silhouette, walk 20 steps, solve maths,
crack a lock, or type a 13-character code. Then it keeps checking on you for an hour.

Everything runs on-device. No account, no network, no data leaves the phone.

---

## Build it

1. Open the folder in **Android Studio** (Ladybug / 2024.2 or newer).
2. Let it sync — Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35.
3. `Run ▶` on a physical device (the camera, step counter and alarm behaviour need real hardware).

Or from the terminal:

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # straight onto a connected device
```

Requires JDK 17 and an Android SDK with `platforms;android-35` + `build-tools;35.0.0`.

## Permissions it will ask for, and why

| Permission | Why |
|---|---|
| Camera | eye-open scan, standing pose, QR mission |
| Physical activity | counting your steps |
| Notifications | ringing, and the "did you do it?" prompts |
| Exact alarms | firing at the minute, not "around then" |
| Ignore battery optimisation | so Android doesn't quietly kill the alarm overnight |

Grant the last two from **config → permissions** — Android will not let an app request
them inline.

## How it's laid out

```
core/i18n      Strings.kt (English) + StringsFa.kt (Persian). Every string, one place.
core/model     MissionSpec / MissionType / Difficulty + JSON codec
core/util      time formatting, day bitmasks, notification channels, permissions, QR
data/db        Room: alarms, tasks, routines, activity log, wake sessions
data/prefs     DataStore settings — every tunable knob lives here
data/repo      LogRepository, PlanRepository (tasks + routines + reminders)
alarm/         AlarmScheduler, AlarmService, AlarmSoundPlayer, receivers,
               MissionEngine (the state machine), AlarmActivity, CountdownService
missions/      one file per mission: camera, sensors, puzzles, codes, QR
ui/            theme, reusable components, and one file per screen
```

## The bits worth reading first

- `alarm/MissionEngine.kt` — the ladder. Clearing a mission that has `delayBeforeMin > 0`
  genuinely stops the alarm and reschedules, so the next one ambushes you after you've
  drifted back off. That gap is the whole idea.
- `alarm/WakeCheckReceiver.kt` — the "you beat the alarm then fell asleep again" fix.
- `alarm/AlarmSoundPlayer.kt` — volume ramp plus the observer that undoes volume-down.
- `missions/StandUpMission.kt` — `judgeStanding()` combines four independent signals so
  lying in bed holding the phone overhead can't pass.
