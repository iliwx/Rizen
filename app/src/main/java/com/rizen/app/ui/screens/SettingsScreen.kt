package com.rizen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rizen.app.Routes
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.model.displayName
import com.rizen.app.core.util.PermKey
import com.rizen.app.core.util.Perms
import com.rizen.app.data.prefs.AccentTheme
import com.rizen.app.data.prefs.AppLanguage
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.Divider
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.components.WPSegmented
import com.rizen.app.ui.components.WPStepper
import com.rizen.app.ui.components.WPSwitchRow
import com.rizen.app.ui.theme.LocalWake

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()

    var confirmWipe by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }
    var newCheckMinute by remember { mutableIntStateOf(45) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.setTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[ ${s.back} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                    modifier = Modifier.clickable { nav.popBackStack() }.padding(8.dp),
                )
            }
        }

        // ══ GENERAL ══
        item {
            WPCard {
                SectionLabel(s.setGeneral, accent = true)
                Text(s.setLanguage, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                Spacer(Modifier.height(6.dp))
                WPSegmented(
                    options = listOf(
                        AppLanguage.EN to s.setLangEn,
                        AppLanguage.FA to s.setLangFa,
                    ),
                    selected = st.language,
                    onSelect = { vm.updateSettings { setLanguage(it) } },
                )
                Spacer(Modifier.height(12.dp))
                Text("accent", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentTheme.entries.forEach { theme ->
                        AccentSwatch(
                            theme = theme,
                            selected = st.accent == theme,
                        ) { vm.updateSettings { setAccent(theme) } }
                    }
                }
                Divider(Modifier.padding(vertical = 10.dp))
                WPSwitchRow(
                    s.set24h, st.use24h,
                    onCheckedChange = { v -> vm.updateSettings { set24h(v) } },
                )
            }
        }

        // ══ WAKING UP ══
        item {
            WPCard {
                SectionLabel(s.setWaking, accent = true)
                Text(
                    s.setGlobalDifficulty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textDim,
                )
                Spacer(Modifier.height(6.dp))
                WPSegmented(
                    options = listOf(
                        Difficulty.EASY to s.diffEasy,
                        Difficulty.NORMAL to s.diffNormal,
                        Difficulty.HARD to s.diffHard,
                        Difficulty.BRUTAL to s.diffBrutal,
                    ),
                    selected = st.globalDifficulty,
                    onSelect = { d ->
                        vm.updateSettings { setDifficulty(d) }
                        vm.setDefaultMissions(st.defaultMissions.map { it.copy(difficulty = d) })
                    },
                )
                Divider(Modifier.padding(vertical = 10.dp))
                WPStepper(
                    s.setGraceSeconds, st.puzzleGraceSeconds,
                    { v -> vm.updateSettings { setGrace(v) } },
                    range = 5..180, step = 5, suffix = s.secondsShort,
                )
                Text(
                    s.setGraceHint.fmt(st.puzzleGraceSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                )
            }
        }

        // ══ DEFAULT MISSIONS ══
        item {
            WPCard {
                SectionLabel(s.setDefaultMissions, accent = true)
                Text(
                    s.missionsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                )
                Spacer(Modifier.height(10.dp))
                val defaults = remember(st.defaultMissionsJson) {
                    MissionSpec.ensureAllTypes(st.defaultMissions)
                }
                defaults.forEachIndexed { i, spec ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.setDefaultMissions(
                                    defaults.toMutableList().also {
                                        it[i] = spec.copy(enabled = !spec.enabled)
                                    }
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (spec.enabled) "[✓]" else "[ ]",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (spec.enabled) c.accent else c.textFaint,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            spec.type.displayName(s),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (spec.enabled) c.text else c.textFaint,
                            modifier = Modifier.weight(1f),
                        )
                        if (spec.delayBeforeMin > 0) {
                            Text(
                                "+${spec.delayBeforeMin}${s.minutesShort}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textFaint,
                            )
                        }
                    }
                }
            }
        }

        // ══ STILL-AWAKE CHECKS ══
        item {
            WPCard {
                SectionLabel(s.setWakeChecks, accent = true)
                WPSwitchRow(
                    s.alarmWakeCheck, st.wakeChecksEnabled,
                    { v -> vm.updateSettings { setWakeChecks(v) } },
                    subtitle = s.alarmWakeCheckHint.fmt(st.wakeCheckMinutes.joinToString(" / ")),
                )
                AnimatedVisibility(st.wakeChecksEnabled) {
                    Column {
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text(
                            s.setWakeCheckSchedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textFaint,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            st.wakeCheckMinutes.forEach { m ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c.accentSoft)
                                        .clickable {
                                            vm.updateSettings {
                                                setWakeCheckMinutes(st.wakeCheckMinutes - m)
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        "$m ×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.accent,
                                    )
                                }
                            }
                        }
                        WPStepper("+ ${s.minutesShort}", newCheckMinute, { newCheckMinute = it },
                            range = 1..240, step = 5)
                        WPButton(
                            s.add,
                            {
                                vm.updateSettings {
                                    setWakeCheckMinutes(
                                        (st.wakeCheckMinutes + newCheckMinute).distinct()
                                    )
                                }
                            },
                            Modifier.fillMaxWidth(),
                            ghost = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        WPStepper(
                            s.setWakeCheckAnswerWindow, st.wakeCheckAnswerWindowSec,
                            { v -> vm.updateSettings { setWakeCheckWindow(v) } },
                            range = 15..600, step = 15, suffix = s.secondsShort,
                        )
                    }
                }
            }
        }

        // ══ ESCAPE + ANTI-CHEAT ══
        item {
            WPCard {
                SectionLabel(s.setDefence, accent = true)
                WPSwitchRow(
                    s.setEmergency, st.emergencyEnabled,
                    { v -> vm.updateSettings { setEmergencyEnabled(v) } },
                    subtitle = s.alarmEmergencyHint,
                )
                AnimatedVisibility(st.emergencyEnabled) {
                    WPStepper(
                        s.setEmergencyLen, st.emergencyCodeLength,
                        { v -> vm.updateSettings { setEmergencyLength(v) } },
                        range = 4..30,
                    )
                }
                Divider(Modifier.padding(vertical = 8.dp))
                WPSwitchRow(
                    s.setVolumeLock, st.volumeLock,
                    { v -> vm.updateSettings { setVolumeLock(v) } },
                    subtitle = s.setVolumeLockHint,
                )
                WPSwitchRow(
                    s.setBlockBack, st.blockBack,
                    onCheckedChange = { v -> vm.updateSettings { setBlockBack(v) } },
                )
                WPSwitchRow(
                    s.setWatchdog, st.watchdogEnabled,
                    { v -> vm.updateSettings { setWatchdog(v) } },
                    subtitle = s.setWatchdogHint,
                )
            }
        }

        // ══ QR ══
        item {
            WPCard {
                SectionLabel(s.setQrMission, accent = true)
                WPSwitchRow(
                    s.setQrMission, st.qrMissionEnabled,
                    { v -> vm.updateSettings { setQrMission(v) } },
                    subtitle = s.qrGenBody,
                )
                Spacer(Modifier.height(8.dp))
                WPButton(s.setQrGenerate, { nav.navigate(Routes.QR) }, Modifier.fillMaxWidth(),
                    ghost = true)
            }
        }

        // ══ ROUTINE ══
        item {
            WPCard {
                SectionLabel(s.routinesTitle, accent = true)
                WPSwitchRow(
                    s.setRoutineAuto, st.routineAutoStart,
                    { v -> vm.updateSettings { setRoutineAutoStart(v) } },
                    subtitle = s.routinesHint,
                )
            }
        }

        // ══ PERMISSIONS ══
        item {
            WPCard {
                SectionLabel(s.setPermissions, accent = true)
                listOf(
                    PermKey.CAMERA to s.setCamera,
                    PermKey.ACTIVITY to s.setActivity,
                    PermKey.NOTIFICATIONS to s.setNotifications,
                    PermKey.EXACT_ALARM to s.setExactAlarm,
                    PermKey.BATTERY to s.setBattery,
                ).forEach { (key, label) ->
                    @Suppress("UNUSED_EXPRESSION") permTick
                    val granted = Perms.isGranted(context, key)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(Perms.settingsIntent(context, key))
                                }
                                permTick++
                            }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text,
                            modifier = Modifier.weight(1f))
                        Text(
                            if (granted) s.setGranted else s.setMissing,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (granted) c.accent else c.warn,
                        )
                    }
                }
            }
        }

        // ══ DANGER + ABOUT ══
        item {
            WPCard {
                SectionLabel(s.setAbout, accent = true)
                Text(s.setAboutBody, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                Spacer(Modifier.height(14.dp))
                WPButton(
                    s.setReplayTutorial,
                    {
                        vm.updateSettings { setOnboardingDone(false) }
                        nav.navigate(Routes.ONBOARDING)
                    },
                    Modifier.fillMaxWidth(),
                    ghost = true,
                )
                Spacer(Modifier.height(10.dp))
                if (confirmWipe) {
                    WPButton(
                        s.setWipeConfirm,
                        { vm.wipeEverything(); confirmWipe = false },
                        Modifier.fillMaxWidth(),
                        danger = true,
                    )
                } else {
                    WPButton(s.setWipe, { confirmWipe = true }, Modifier.fillMaxWidth(),
                        danger = true, ghost = true)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AccentSwatch(theme: AccentTheme, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWake.current
    val color = when (theme) {
        AccentTheme.PHOSPHOR -> Color(0xFF5BE49B)
        AccentTheme.AMBER -> Color(0xFFF5B94A)
        AccentTheme.ICE -> Color(0xFF5AD1E8)
        AccentTheme.MAGENTA -> Color(0xFFF06BB0)
    }
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) color else c.outline,
                CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
    }
}
