package com.rizen.app.missions

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.core.util.TimeFmt
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════════════════════
// STEP COUNTING
// ══════════════════════════════════════════════════════════════════════════════

private class StepTracker(context: Context, private val onStep: (Int, Boolean) -> Unit) {

    private val sm = context.getSystemService<SensorManager>()
    private val hardware = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val detector = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var baseline = -1f
    private var count = 0

    // Fallback peak detection state
    private var lastPeakAt = 0L
    private var smoothed = 9.81f
    private var wasAbove = false

    val usingFallback: Boolean = hardware == null && detector == null

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    val total = e.values.firstOrNull() ?: return
                    if (baseline < 0) baseline = total
                    count = (total - baseline).toInt().coerceAtLeast(0)
                    onStep(count, false)
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    count += 1
                    onStep(count, false)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                    val mag = sqrt(x * x + y * y + z * z)
                    // Low-pass, then count downward zero-crossings of the gait bounce.
                    smoothed = smoothed * 0.82f + mag * 0.18f
                    val delta = mag - smoothed
                    val now = System.currentTimeMillis()
                    if (delta > 1.6f && !wasAbove) {
                        wasAbove = true
                        // Real walking is 0.3–1s per step; anything faster is wrist-flicking.
                        if (now - lastPeakAt in 260..2000) {
                            count += 1
                            onStep(count, false)
                        } else if (now - lastPeakAt < 260) {
                            onStep(count, true)   // flagged as suspicious shaking
                        }
                        lastPeakAt = now
                    } else if (delta < 0.5f) {
                        wasAbove = false
                    }
                }
            }
        }
    }

    fun start() {
        when {
            hardware != null -> sm?.registerListener(
                listener, hardware, SensorManager.SENSOR_DELAY_FASTEST
            )
            detector != null -> sm?.registerListener(
                listener, detector, SensorManager.SENSOR_DELAY_FASTEST
            )
            accel != null -> sm?.registerListener(
                listener, accel, SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() = sm?.unregisterListener(listener)
}

@Composable
fun StepsMission(spec: MissionSpec, onPass: () -> Unit, onFail: (String) -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = true }   // proceed either way: the accelerometer fallback needs no grant

    LaunchedEffect(Unit) {
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    var steps by remember { mutableIntStateOf(0) }
    var cheating by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(spec.timeLimitSec.coerceAtLeast(30)) }
    var passed by remember { mutableStateOf(false) }

    val goal = spec.stepGoal.coerceAtLeast(1)
    val tracker = remember {
        StepTracker(context) { n, suspicious ->
            steps = n
            if (suspicious) cheating = true
        }
    }

    DisposableEffect(granted) {
        tracker.start()
        onDispose { tracker.stop() }
    }

    LaunchedEffect(steps, passed) {
        if (!passed && steps >= goal) {
            passed = true
            delay(600)
            onPass()
        }
    }

    LaunchedEffect(Unit) {
        while (!passed && remaining > 0) {
            delay(1000)
            remaining--
            if (cheating) { delay(1500); cheating = false }
        }
        if (!passed) {
            onFail("steps_timeout")
            remaining = spec.timeLimitSec.coerceAtLeast(30)
            steps = 0
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$steps",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (passed) c.accent else c.text,
                )
                Text(
                    s.stepsCount.fmt(steps, goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textDim,
                )
                Spacer(Modifier.height(24.dp))
                FootprintTrack(progress = steps.toFloat() / goal, goal = goal)
            }
        }

        SegmentBar((steps.toFloat() / goal).coerceIn(0f, 1f), segments = 20, color = c.accent)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                s.stepsTimeLeft.fmt(TimeFmt.mmss(remaining * 1000L)),
                style = MaterialTheme.typography.labelMedium,
                color = if (remaining <= 10) c.warn else c.textFaint,
            )
            if (tracker.usingFallback) {
                Text(
                    s.missionNoStepSensor,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textFaint,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MissionStatusLine(
            when {
                passed -> s.stepsLocked
                cheating -> s.stepsCheating
                steps == 0 -> s.stepsGo
                else -> s.stepsCount.fmt(steps, goal)
            },
            good = passed,
            bad = cheating,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** A little trail of footprints that lights up as you walk. Pure motivation sugar. */
@Composable
private fun FootprintTrack(progress: Float, goal: Int) {
    val c = LocalWake.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "steps")
    val dots = goal.coerceIn(6, 24)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val lit = (p * dots)
        for (i in 0 until dots) {
            val t = i / (dots - 1f)
            val x = size.width * (0.08f + 0.84f * t)
            val y = size.height * (0.5f + 0.32f * kotlin.math.sin(t * 10f))
            val on = i < lit
            drawCircle(
                color = if (on) c.accent else c.outline,
                radius = if (on) 7f else 5f,
                center = Offset(x, y),
            )
            if (on && i > 0) {
                val pt = (i - 1) / (dots - 1f)
                drawLine(
                    color = c.accent.copy(alpha = 0.35f),
                    start = Offset(size.width * (0.08f + 0.84f * pt),
                        size.height * (0.5f + 0.32f * kotlin.math.sin(pt * 10f))),
                    end = Offset(x, y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SHAKE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ShakeMission(spec: MissionSpec, onPass: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current

    val target = when (spec.difficulty) {
        Difficulty.EASY -> spec.reps.coerceAtLeast(10)
        Difficulty.NORMAL -> spec.reps.coerceAtLeast(25)
        Difficulty.HARD -> spec.reps.coerceAtLeast(45)
        Difficulty.BRUTAL -> spec.reps.coerceAtLeast(70)
    }

    var shakes by remember { mutableIntStateOf(0) }
    var intensity by remember { mutableFloatStateOf(0f) }
    var passed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService<SensorManager>()
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val l = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(
                    e.values[0] * e.values[0] +
                        e.values[1] * e.values[1] +
                        e.values[2] * e.values[2]
                ) / SensorManager.GRAVITY_EARTH
                intensity = (abs(g - 1f) / 1.8f).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                if (abs(g - 1f) > 1.1f && now - last > 120) {
                    last = now
                    shakes += 1
                }
            }
        }
        sensor?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm?.unregisterListener(l) }
    }

    LaunchedEffect(shakes) {
        if (!passed && shakes >= target) {
            passed = true
            delay(500)
            onPass()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$shakes / $target",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (passed) c.accent else c.text,
                )
                Spacer(Modifier.height(20.dp))
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val rings = 5
                    repeat(rings) { i ->
                        val f = (i + 1) / rings.toFloat()
                        drawCircle(
                            color = c.accent.copy(alpha = (intensity - f + 0.35f).coerceIn(0f, 0.5f)),
                            radius = size.minDimension * 0.16f * (i + 1),
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3f),
                        )
                    }
                }
            }
        }
        SegmentBar((shakes.toFloat() / target).coerceIn(0f, 1f), segments = 20)
        Spacer(Modifier.height(12.dp))
        MissionStatusLine(
            when {
                passed -> s.shakeLocked
                intensity < 0.25f -> s.shakeHarder
                else -> s.shakeGo
            },
            good = passed,
        )
        Spacer(Modifier.height(10.dp))
    }
}
