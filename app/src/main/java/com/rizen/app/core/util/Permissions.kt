package com.rizen.app.core.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

enum class PermKey { CAMERA, ACTIVITY, NOTIFICATIONS, EXACT_ALARM, BATTERY }

object Perms {

    fun runtimePermission(key: PermKey): String? = when (key) {
        PermKey.CAMERA -> Manifest.permission.CAMERA
        PermKey.ACTIVITY ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                Manifest.permission.ACTIVITY_RECOGNITION else null
        PermKey.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
        else -> null
    }

    fun isGranted(context: Context, key: PermKey): Boolean = when (key) {
        PermKey.EXACT_ALARM -> canScheduleExact(context)
        PermKey.BATTERY -> ignoresBatteryOptimisation(context)
        else -> {
            val p = runtimePermission(key)
            p == null || ContextCompat.checkSelfPermission(context, p) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    }

    fun ignoresBatteryOptimisation(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Settings screen to send the user to when a permission can't be requested inline. */
    fun settingsIntent(context: Context, key: PermKey): Intent = when (key) {
        PermKey.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            else appDetails(context)

        PermKey.BATTERY ->
            @Suppress("BatteryLife")
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))

        PermKey.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else appDetails(context)

        else -> appDetails(context)
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    fun hasStepCounter(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
}
