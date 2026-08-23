package com.rizen.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rizen.app.Routes
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.model.displayName
import com.rizen.app.core.util.DayMask
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.data.db.AlarmEntity
import com.rizen.app.data.prefs.AppLanguage
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.theme.LocalWake

@Composable
fun AlarmsScreen(vm: AppViewModel, nav: NavHostController) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(s.alarmsTitle, style = MaterialTheme.typography.headlineMedium, color = c.text)
                Spacer(Modifier.height(6.dp))
            }

            if (alarms.isEmpty()) {
                item {
                    WPCard {
                        Text(
                            s.alarmsEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    use24h = settings.use24h,
                    persian = settings.language == AppLanguage.FA,
                    onToggle = { vm.toggleAlarm(alarm, it) },
                    onClick = { nav.navigate(Routes.alarmEdit(alarm.id)) },
                )
            }
        }

        // Floating add button
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 20.dp, bottom = 92.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable { nav.navigate(Routes.alarmEdit(0L)) },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium, color = c.bg)
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmEntity,
    use24h: Boolean,
    persian: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val labels = if (persian) DayMask.labelsFa else DayMask.labelsEn

    WPCard(highlighted = alarm.enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeFmt.clock(alarm.hour, alarm.minute, use24h),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (alarm.enabled) c.text else c.textFaint,
                    )
                    if (!use24h) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            TimeFmt.meridiem(alarm.hour),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textFaint,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (alarm.nuclear) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.danger.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "NUCLEAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.danger,
                            )
                        }
                    }
                }
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.bg,
                    checkedTrackColor = c.accent,
                    checkedBorderColor = c.accent,
                    uncheckedThumbColor = c.textFaint,
                    uncheckedTrackColor = c.surfaceHigh,
                    uncheckedBorderColor = c.outline,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { i, d ->
                val on = DayMask.has(alarm.daysMask, i)
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (on) c.accentSoft else c.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) c.accent else c.textFaint,
                    )
                }
            }
            if (alarm.daysMask == DayMask.NONE) {
                Spacer(Modifier.width(6.dp))
                Text(
                    s.onceOnly,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            alarm.activeMissions().take(6).forEach { m ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.surfaceHigh)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        m.type.displayName(s),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textFaint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
