package com.rizen.app.missions

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay

private data class EyeReading(
    val found: Boolean = false,
    val left: Float = 0f,
    val right: Float = 0f,
    val faceFraction: Float = 0f,
) {
    val open: Float get() = minOf(left, right)
}

/**
 * ML Kit face detection with classification on, which is what gives us
 * `leftEyeOpenProbability`. We take the **minimum** of the two eyes deliberately: one
 * eye cracked open while the other stays shut is exactly the cheat this mission exists
 * to catch.
 */
private class EyeAnalyzer(private val onReading: (EyeReading) -> Unit) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) {
                    onReading(EyeReading())
                } else {
                    val frameArea = (input.width * input.height).toFloat().coerceAtLeast(1f)
                    val faceArea = (face.boundingBox.width() * face.boundingBox.height()).toFloat()
                    onReading(
                        EyeReading(
                            found = true,
                            left = face.leftEyeOpenProbability ?: -1f,
                            right = face.rightEyeOpenProbability ?: -1f,
                            faceFraction = faceArea / frameArea,
                        )
                    )
                }
            }
            .addOnFailureListener { onReading(EyeReading()) }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = runCatching { detector.close() }
}

private fun Difficulty.eyeThreshold() = when (this) {
    Difficulty.EASY -> 0.55f
    Difficulty.NORMAL -> 0.70f
    Difficulty.HARD -> 0.80f
    Difficulty.BRUTAL -> 0.88f
}

@Composable
fun EyeScanMission(
    spec: MissionSpec,
    onPass: () -> Unit,
    onFail: (String) -> Unit,
    onSubstitute: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val threshold = spec.difficulty.eyeThreshold()

    CameraGate(
        denied = { request ->
            CameraDeniedPanel(
                onRequest = request,
                onSubstitute = onSubstitute,
            )
        },
    ) {
        var reading by remember { mutableStateOf(EyeReading()) }
        var heldMs by remember { mutableFloatStateOf(0f) }
        var passed by remember { mutableStateOf(false) }
        var elapsed by remember { mutableFloatStateOf(0f) }

        val analyzer = remember { EyeAnalyzer { reading = it } }
        DisposableEffect(Unit) { onDispose { analyzer.close() } }

        val holdTargetMs = spec.holdSeconds.coerceAtLeast(1) * 1000f

        // Hold loop: the eyes have to STAY open, not flash open for one frame.
        LaunchedEffect(Unit) {
            while (!passed) {
                delay(100)
                elapsed += 100f
                val ok = reading.found && reading.open >= threshold
                heldMs = if (ok) (heldMs + 100f) else (heldMs - 220f).coerceAtLeast(0f)
                if (heldMs >= holdTargetMs) {
                    passed = true
                    delay(650)   // let the "LOCKED" state land before we move on
                    onPass()
                }
                if (spec.timeLimitSec > 0 && elapsed > spec.timeLimitSec * 1000f) {
                    elapsed = 0f
                    onFail("eye_timeout")
                }
            }
        }

        val status = when {
            passed -> s.eyeLocked
            !reading.found -> s.eyeSearching
            reading.open < 0f -> s.eyeTooDark
            reading.open < threshold * 0.45f -> s.eyeClosed
            reading.open < threshold -> s.eyeHalf
            else -> s.eyeOpen
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(0.82f)
                        .clip(RoundedCornerShape(28.dp)),
                ) {
                    CameraPreview(analyzer = analyzer, modifier = Modifier.fillMaxSize())
                    ScanReticle(
                        progress = (heldMs / holdTargetMs).coerceIn(0f, 1f),
                        locked = passed,
                        tracking = reading.found,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Two gauges, one per eye — makes the "you're squinting" feedback obvious.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EyeGauge("L", reading.left, threshold, Modifier.weight(1f))
                EyeGauge("R", reading.right, threshold, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            MissionStatusLine(status, good = passed || reading.open >= threshold,
                bad = reading.found && reading.open < threshold)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun EyeGauge(label: String, value: Float, threshold: Float, modifier: Modifier = Modifier) {
    val c = LocalWake.current
    val v = value.coerceAtLeast(0f)
    val good = v >= threshold
    Column(modifier) {
        Row {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            Spacer(Modifier.weight(1f))
            Text(
                "${(v * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (good) c.accent else c.textFaint,
            )
        }
        Spacer(Modifier.height(4.dp))
        SegmentBar(v, segments = 14, color = if (good) c.accent else c.warn)
    }
}

/**
 * The bit that makes it feel like equipment rather than a form: corner brackets, a
 * sweeping scan line, and a ring that fills while your eyes stay open.
 */
@Composable
private fun ScanReticle(progress: Float, locked: Boolean, tracking: Boolean) {
    val c = LocalWake.current
    val t = rememberInfiniteTransition(label = "scan")
    val sweep by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        "sweep",
    )
    val p by animateFloatAsState(progress, label = "hold")
    val ringColor = if (locked) c.accent else if (tracking) c.accent.copy(alpha = 0.7f) else c.textFaint

    Canvas(Modifier.fillMaxSize()) {
        val inset = size.minDimension * 0.06f
        val w = size.width
        val h = size.height

        // dim vignette so the UI reads over any bedroom lighting
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.55f))
            )
        )

        // corner brackets
        val armLen = size.minDimension * 0.13f
        val stroke = 2.5f
        listOf(
            Offset(inset, inset) to listOf(Offset(armLen, 0f), Offset(0f, armLen)),
            Offset(w - inset, inset) to listOf(Offset(-armLen, 0f), Offset(0f, armLen)),
            Offset(inset, h - inset) to listOf(Offset(armLen, 0f), Offset(0f, -armLen)),
            Offset(w - inset, h - inset) to listOf(Offset(-armLen, 0f), Offset(0f, -armLen)),
        ).forEach { (corner, arms) ->
            arms.forEach { arm ->
                drawLine(
                    color = ringColor,
                    start = corner,
                    end = Offset(corner.x + arm.x, corner.y + arm.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        // sweeping scan line
        if (!locked) {
            val y = inset + (h - inset * 2) * sweep
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, c.accent.copy(alpha = 0.85f), Color.Transparent)
                ),
                start = Offset(inset, y),
                end = Offset(w - inset, y),
                strokeWidth = 2f,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, c.accent.copy(alpha = 0.10f), Color.Transparent),
                ),
                topLeft = Offset(inset, y - 40f),
                size = Size(w - inset * 2, 80f),
            )
        }

        // hold ring
        val ringInset = size.minDimension * 0.14f
        val d = size.minDimension - ringInset * 2
        drawArc(
            color = c.outline,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset((w - d) / 2f, (h - d) / 2f),
            size = Size(d, d),
            style = Stroke(width = 4f),
        )
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * p,
            useCenter = false,
            topLeft = Offset((w - d) / 2f, (h - d) / 2f),
            size = Size(d, d),
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun CameraDeniedPanel(onRequest: () -> Unit, onSubstitute: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s.missionCameraNeeded, style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(
            s.missionCameraNeededBody,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textDim,
        )
        Spacer(Modifier.height(24.dp))
        WPButton(s.obGrant, onRequest, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        WPButton(s.missionSkipToAlt, onSubstitute, Modifier.fillMaxWidth(), ghost = true)
    }
}
