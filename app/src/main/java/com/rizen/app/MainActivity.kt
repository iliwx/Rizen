package com.rizen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.TerminalBackground
import com.rizen.app.ui.screens.AlarmEditScreen
import com.rizen.app.ui.screens.AlarmsScreen
import com.rizen.app.ui.screens.HomeScreen
import com.rizen.app.ui.screens.OnboardingScreen
import com.rizen.app.ui.screens.PlanScreen
import com.rizen.app.ui.screens.QrScreen
import com.rizen.app.ui.screens.SettingsScreen
import com.rizen.app.ui.screens.StatsScreen
import com.rizen.app.ui.screens.TimerScreen
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeTheme

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ALARMS = "alarms"
    const val ALARM_EDIT = "alarm/{id}"
    const val PLAN = "plan"
    const val TIMER = "timer"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val QR = "qr"

    fun alarmEdit(id: Long) = "alarm/$id"
}

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openTaskId = intent.getLongExtra(EXTRA_OPEN_TASK_ID, -1)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            WakeTheme(settings) {
                val nav = rememberNavController()

                LaunchedEffect(openTaskId) {
                    if (openTaskId > 0) nav.navigate(Routes.PLAN)
                }

                TerminalBackground {
                    Box(Modifier.fillMaxSize()) {
                        NavHost(
                            navController = nav,
                            startDestination =
                                if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable(Routes.ONBOARDING) {
                                OnboardingScreen(vm) {
                                    nav.navigate(Routes.HOME) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                    }
                                }
                            }
                            composable(Routes.HOME) { HomeScreen(vm, nav) }
                            composable(Routes.ALARMS) { AlarmsScreen(vm, nav) }
                            composable(
                                Routes.ALARM_EDIT,
                                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            ) { entry ->
                                AlarmEditScreen(
                                    vm = vm,
                                    alarmId = entry.arguments?.getLong("id") ?: 0L,
                                    onDone = { nav.popBackStack() },
                                )
                            }
                            composable(Routes.PLAN) { PlanScreen(vm) }
                            composable(Routes.TIMER) { TimerScreen(vm) }
                            composable(Routes.STATS) { StatsScreen(vm) }
                            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                            composable(Routes.QR) { QrScreen(vm, onBack = { nav.popBackStack() }) }
                        }

                        BottomBar(nav, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TASK_ID = "open_task_id"
    }
}

@Composable
private fun BottomBar(nav: NavHostController, modifier: Modifier = Modifier) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs = listOf(
        Routes.HOME to s.navHome,
        Routes.ALARMS to s.navAlarms,
        Routes.PLAN to s.navTasks,
        Routes.TIMER to s.navTimer,
        Routes.STATS to s.navStats,
    )

    AnimatedVisibility(visible = route in tabs.map { it.first }, modifier = modifier) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface.copy(alpha = 0.97f))
                    .border(1.dp, c.outlineSoft, RoundedCornerShape(16.dp))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEach { (dest, label) ->
                    val active = route == dest
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                if (!active) nav.navigate(dest) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) c.accent else c.textFaint,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
