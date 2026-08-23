package com.rizen.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.util.PermKey
import com.rizen.app.core.util.Perms
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.Divider
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.TypewriterText
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.launch

/**
 * Six cards, skippable at any point. The last one is the permissions page — deliberately
 * last, so the user already knows *why* the app wants a camera before it asks.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onFinish: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    val pages = listOf(
        s.obTitle1 to s.obBody1,
        s.obTitle2 to s.obBody2,
        s.obTitle3 to s.obBody3,
        s.obTitle4 to s.obBody4,
        s.obTitle5 to s.obBody5,
        s.obTitle6 to s.obBody6,
    )
    val pager = rememberPagerState { pages.size }

    fun finish() {
        vm.updateSettings { setOnboardingDone(true) }
        onFinish()
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("RIZEN", style = MaterialTheme.typography.labelMedium, color = c.accent)
            Spacer(Modifier.weight(1f))
            Text(
                s.skip,
                style = MaterialTheme.typography.labelMedium,
                color = c.textFaint,
                modifier = Modifier
                    .clickable { finish() }
                    .padding(8.dp),
            )
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "0${page + 1}",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.accent.copy(alpha = 0.35f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    pages[page].first,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.text,
                )
                Spacer(Modifier.height(14.dp))
                TypewriterText(
                    pages[page].second,
                    style = MaterialTheme.typography.bodyLarge,
                    speedMs = 10,
                )

                if (page == pages.lastIndex) {
                    Spacer(Modifier.height(24.dp))
                    PermissionChecklist()
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 9.dp else 6.dp)
                        .background(
                            if (i == pager.currentPage) c.accent else c.outline,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(6.dp))
            }
        }

        WPButton(
            text = if (pager.currentPage == pages.lastIndex) s.obLetsGo else s.next,
            onClick = {
                if (pager.currentPage == pages.lastIndex) finish()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionChecklist() {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    val runtime = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val rows = listOf(
        PermKey.CAMERA to s.permCameraWhy,
        PermKey.ACTIVITY to s.permActivityWhy,
        PermKey.NOTIFICATIONS to s.permNotifWhy,
        PermKey.EXACT_ALARM to s.permExactWhy,
        PermKey.BATTERY to s.permBatteryWhy,
    )

    WPCard {
        SectionLabel(s.setPermissions, accent = true)
        rows.forEachIndexed { i, (key, why) ->
            @Suppress("UNUSED_EXPRESSION") tick
            val granted = Perms.isGranted(context, key)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (key) {
                            PermKey.EXACT_ALARM, PermKey.BATTERY ->
                                runCatching { context.startActivity(Perms.settingsIntent(context, key)) }
                            else -> launcher.launch(runtime.toTypedArray())
                        }
                        tick++
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (granted) "[✓]" else "[ ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) c.accent else c.warn,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (key) {
                            PermKey.CAMERA -> s.setCamera
                            PermKey.ACTIVITY -> s.setActivity
                            PermKey.NOTIFICATIONS -> s.setNotifications
                            PermKey.EXACT_ALARM -> s.setExactAlarm
                            PermKey.BATTERY -> s.setBattery
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                    )
                    Text(why, style = MaterialTheme.typography.bodySmall, color = c.textFaint)
                }
            }
            if (i != rows.lastIndex) Divider()
        }
    }
}


