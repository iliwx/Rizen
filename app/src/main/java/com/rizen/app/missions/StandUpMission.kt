package com.rizen.app.missions

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Normalised (0..1, y down) landmarks in upright screen space. */
private data class PoseReading(
    val found: Boolean = false,
    val points: Map<Int, Offset> = emptyMap(),
    val confidence: Map<Int, Float> = emptyMap(),
) {
    fun p(id: Int): Offset? = points[id]?.takeIf { (confidence[id] ?: 0f) > 0.4f }
}

private object Lm {
    const val NOSE = PoseLandmark.NOSE
    const val L_SHOULDER = PoseLandmark.LEFT_SHOULDER
    const val R_SHOULDER = PoseLandmark.RIGHT_SHOULDER
    const val L_HIP = PoseLandmark.LEFT_HIP
    const val R_HIP = PoseLandmark.RIGHT_HIP
    const val L_KNEE = PoseLandmark.LEFT_KNEE
    const val R_KNEE = PoseLandmark.RIGHT_KNEE
    const val L_ANKLE = PoseLandmark.LEFT_ANKLE
    const val R_ANKLE = PoseLandmark.RIGHT_ANKLE
    const val L_ELBOW = PoseLandmark.LEFT_ELBOW
    const val R_ELBOW = PoseLandmark.RIGHT_ELBOW
    const val L_WRIST = PoseLandmark.LEFT_WRIST
    const val R_WRIST = PoseLandmark.RIGHT_WRIST

    val skeleton = listOf(
        L_SHOULDER to R_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST,
        R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST,
        L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP,
        L_HIP to L_KNEE, L_KNEE to L_ANKLE,
        R_HIP to R_KNEE, R_KNEE to R_ANKLE,
    )
}

private class PoseAnalyzer(private val onReading: (PoseReading) -> Unit) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit reports landmarks in the *rotated* frame, so the normalising extents
        // have to be swapped for 90/270 too — otherwise everything is squashed sideways.
        val w = if (rotation == 90 || rotation == 270) media.height.toFloat() else media.width.toFloat()
        val h = if (rotation == 90 || rotation == 270) media.width.toFloat() else media.height.toFloat()

        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { pose ->
                val marks = pose.allPoseLandmarks
                if (marks.isEmpty()) {
                    onReading(PoseReading())
                } else {
                    val pts = HashMap<Int, Offset>(marks.size)
                    val conf = HashMap<Int, Float>(marks.size)
                    marks.forEach { m ->
                        pts[m.landmarkType] = Offset(
                            (m.position.x / w).coerceIn(-0.2f, 1.2f),
                            (m.position.y / h).coerceIn(-0.2f, 1.2f),
                        )
                        conf[m.landmarkType] = m.inFrameLikelihood
                    }
                    onReading(PoseReading(true, pts, conf))
                }
            }
            .addOnFailureListener { onReading(PoseReading()) }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = runCatching { detector.close() }
}

private enum class StandFault { NONE, NO_BODY, TOO_CLOSE, TOO_FAR, NOT_UPRIGHT, PARTIAL }

private data class StandVerdict(val score: Float, val fault: StandFault)

/**
 * Decides whether a human is genuinely standing up.
 *
 * Four independent signals, all of which a person lying in bed holding their phone
 * overhead fails: full body in frame, torso vertical, legs extended, and joints
 * stacked head-over-hips-over-ankles.
 */
private fun judgeStanding(r: PoseReading, difficulty: Difficulty): StandVerdict {
    if (!r.found) return StandVerdict(0f, StandFault.NO_BODY)

    val ls = r.p(Lm.L_SHOULDER); val rs = r.p(Lm.R_SHOULDER)
    val lh = r.p(Lm.L_HIP); val rh = r.p(Lm.R_HIP)
    val lk = r.p(Lm.L_KNEE); val rk = r.p(Lm.R_KNEE)
    val la = r.p(Lm.L_ANKLE); val ra = r.p(Lm.R_ANKLE)

    if (ls == null || rs == null || lh == null || rh == null) {
        return StandVerdict(0.1f, StandFault.PARTIAL)
    }
    if ((lk == null && rk == null) || (la == null && ra == null)) {
        return StandVerdict(0.25f, StandFault.PARTIAL)
    }

    val shoulder = Offset((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
    val hip = Offset((lh.x + rh.x) / 2f, (lh.y + rh.y) / 2f)
    val ankleY = listOfNotNull(la, ra).map { it.y }.average().toFloat()
    val kneeY = listOfNotNull(lk, rk).map { it.y }.average().toFloat()

    // 1. body fills a sensible slice of the frame
    val bodyHeight = abs(ankleY - shoulder.y)
    val framing = when {
        bodyHeight < 0.35f -> return StandVerdict(0.3f, StandFault.TOO_FAR)
        bodyHeight > 1.05f -> return StandVerdict(0.3f, StandFault.TOO_CLOSE)
        else -> 1f - abs(bodyHeight - 0.72f) / 0.5f
    }.coerceIn(0f, 1f)

    // 2. torso vertical — the single strongest "not lying down" signal
    val torsoAngle = Math.toDegrees(
        atan2((hip.x - shoulder.x).toDouble(), (hip.y - shoulder.y).toDouble())
    ).toFloat()
    val verticality = (1f - abs(torsoAngle) / 40f).coerceIn(0f, 1f)

    // 3. joints stacked in the right order down the frame
    val stacked = if (shoulder.y < hip.y && hip.y < kneeY && kneeY < ankleY) 1f else 0f

    // 4. legs extended rather than folded
    val legStraight = listOfNotNull(
        triple(lh, lk, la), triple(rh, rk, ra)
    ).map { angle -> ((angle - 120f) / 55f).coerceIn(0f, 1f) }
        .ifEmpty { listOf(0.5f) }
        .average().toFloat()

    val score = (framing * 0.2f + verticality * 0.35f + stacked * 0.25f + legStraight * 0.2f)
    val need = when (difficulty) {
        Difficulty.EASY -> 0.62f
        Difficulty.NORMAL -> 0.72f
        Difficulty.HARD -> 0.80f
        Difficulty.BRUTAL -> 0.86f
    }

    val fault = when {
        score >= need -> StandFault.NONE
        stacked < 0.5f || verticality < 0.5f -> StandFault.NOT_UPRIGHT
        else -> StandFault.NONE
    }
    return StandVerdict((score / need).coerceIn(0f, 1f), fault)
}

/** Interior angle at [b] in degrees, or null if any joint is missing. */
private fun triple(a: Offset?, b: Offset?, c: Offset?): Float? {
    if (a == null || b == null || c == null) return null
    val v1 = Offset(a.x - b.x, a.y - b.y)
    val v2 = Offset(c.x - b.x, c.y - b.y)
    val dot = v1.x * v2.x + v1.y * v2.y
    val mag = hypot(v1.x, v1.y) * hypot(v2.x, v2.y)
    if (mag == 0f) return null
    return Math.toDegrees(kotlin.math.acos((dot / mag).coerceIn(-1f, 1f)).toDouble()).toFloat()
}

@Composable
fun StandUpMission(
    spec: MissionSpec,
    onPass: () -> Unit,
    onFail: (String) -> Unit,
    onSubstitute: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current

    CameraGate(denied = { req -> CameraDeniedPanel(req, onSubstitute) }) {
        var reading by remember { mutableStateOf(PoseReading()) }
        var heldMs by remember { mutableFloatStateOf(0f) }
        var elapsed by remember { mutableFloatStateOf(0f) }
        var passed by remember { mutableStateOf(false) }

        val analyzer = remember { PoseAnalyzer { reading = it } }
        DisposableEffect(Unit) { onDispose { analyzer.close() } }

        val verdict = judgeStanding(reading, spec.difficulty)
        val holdTarget = spec.holdSeconds.coerceAtLeast(1) * 1000f

        // The hold loop must read the *latest* score without restarting on every frame,
        // so the score is mirrored into a stable holder the coroutine can poll.
        val liveScore = remember { mutableFloatStateOf(0f) }
        liveScore.floatValue = verdict.score

        LaunchedEffect(Unit) {
            while (!passed) {
                delay(100)
                elapsed += 100f
                heldMs = if (liveScore.floatValue >= 1f) heldMs + 100f
                else (heldMs - 200f).coerceAtLeast(0f)
                if (heldMs >= holdTarget) {
                    passed = true
                    delay(700)
                    onPass()
                }
                if (spec.timeLimitSec > 0 && elapsed > spec.timeLimitSec * 1000f) {
                    elapsed = 0f
                    onFail("stand_timeout")
                }
            }
        }

        val status = when {
            passed -> s.standLocked
            verdict.fault == StandFault.NO_BODY -> s.standSearching
            verdict.fault == StandFault.PARTIAL -> s.standPropUp
            verdict.fault == StandFault.TOO_CLOSE -> s.standTooClose
            verdict.fault == StandFault.TOO_FAR -> s.standTooFar
            verdict.fault == StandFault.NOT_UPRIGHT -> s.standNotStanding
            verdict.score >= 1f -> s.standHolding
            else -> s.standAligning.fmt((verdict.score * 100).toInt())
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                CameraPreview(analyzer = analyzer, modifier = Modifier.fillMaxSize())
                SilhouetteOverlay(
                    reading = reading,
                    fit = verdict.score,
                    locked = passed,
                )
            }
            Spacer(Modifier.height(12.dp))
            SegmentBar(
                progress = if (passed) 1f else (heldMs / holdTarget).coerceIn(0f, 1f),
                segments = 20,
                color = if (verdict.score >= 1f) c.accent else c.warn,
            )
            Spacer(Modifier.height(10.dp))
            MissionStatusLine(status, good = passed || verdict.score >= 1f,
                bad = verdict.fault == StandFault.NOT_UPRIGHT)
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * The empty human outline the user has to step into, plus their live skeleton drawn on
 * top of it. Turns "stand up" from an instruction into a target you can see yourself
 * snapping into.
 */
@Composable
private fun SilhouetteOverlay(reading: PoseReading, fit: Float, locked: Boolean) {
    val c = LocalWake.current
    val glow by animateFloatAsState(fit.coerceIn(0f, 1f), label = "fit")
    val outline = androidx.compose.ui.graphics.lerp(c.textFaint, c.accent, glow)

    Canvas(Modifier.fillMaxSize()) {
        drawSilhouette(outline, if (locked) 0.22f else 0.06f + glow * 0.14f)

        if (reading.found) {
            val joint = if (locked) c.accent else androidx.compose.ui.graphics.lerp(
                c.warn, c.accent, glow
            )
            Lm.skeleton.forEach { (a, b) ->
                val pa = reading.p(a); val pb = reading.p(b)
                if (pa != null && pb != null) {
                    drawLine(
                        color = joint.copy(alpha = 0.9f),
                        start = mirror(pa),
                        end = mirror(pb),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            reading.points.keys.forEach { id ->
                reading.p(id)?.let { drawCircle(joint, radius = 5f, center = mirror(it)) }
            }
        }
    }
}

/** Front camera preview is mirrored; landmark coordinates are not. */
private fun DrawScope.mirror(p: Offset) = Offset((1f - p.x) * size.width, p.y * size.height)

private fun DrawScope.drawSilhouette(color: Color, fillAlpha: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val top = h * 0.10f
    val bottom = h * 0.94f
    val bodyH = bottom - top
    val headR = bodyH * 0.075f
    val shoulderY = top + headR * 2.35f
    val hipY = top + bodyH * 0.52f
    val shoulderW = bodyH * 0.135f
    val hipW = bodyH * 0.095f
    val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 14f), 0f)
    val stroke = Stroke(width = 3.5f, pathEffect = dash, cap = StrokeCap.Round)

    // head
    drawCircle(color, headR, Offset(cx, top + headR), style = Stroke(3.5f, pathEffect = dash))
    drawCircle(color.copy(alpha = fillAlpha), headR, Offset(cx, top + headR))

    // torso
    val torso = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - shoulderW, shoulderY)
        lineTo(cx + shoulderW, shoulderY)
        lineTo(cx + hipW, hipY)
        lineTo(cx - hipW, hipY)
        close()
    }
    drawPath(torso, color, style = stroke)
    drawPath(torso, color.copy(alpha = fillAlpha))

    fun limb(from: Offset, to: Offset) = drawLine(
        color = color, start = from, end = to,
        strokeWidth = 3.5f, pathEffect = dash, cap = StrokeCap.Round,
    )

    // neck
    limb(Offset(cx, top + headR * 2), Offset(cx, shoulderY))

    // arms
    limb(Offset(cx - shoulderW, shoulderY), Offset(cx - shoulderW * 1.45f, hipY + bodyH * 0.05f))
    limb(Offset(cx + shoulderW, shoulderY), Offset(cx + shoulderW * 1.45f, hipY + bodyH * 0.05f))

    // legs
    limb(Offset(cx - hipW * 0.55f, hipY), Offset(cx - hipW * 0.75f, bottom))
    limb(Offset(cx + hipW * 0.55f, hipY), Offset(cx + hipW * 0.75f, bottom))

    // floor marker
    drawLine(
        color = color.copy(alpha = 0.6f),
        start = Offset(cx - bodyH * 0.14f, bottom),
        end = Offset(cx + bodyH * 0.14f, bottom),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawRect(
        color = color.copy(alpha = 0.05f),
        topLeft = Offset(cx - bodyH * 0.2f, top - headR * 0.4f),
        size = Size(bodyH * 0.4f, bodyH * 1.08f),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))),
    )
}
