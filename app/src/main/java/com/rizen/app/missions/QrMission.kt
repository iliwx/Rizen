package com.rizen.app.missions

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.util.QrGen
import com.rizen.app.ui.theme.LocalWake
import kotlinx.coroutines.delay

private class QrAnalyzer(private val onCode: (String?) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue?.let(onCode)
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = runCatching { scanner.close() }
}

/**
 * The "get out of bed physically" mission: the code lives on a printout you taped
 * somewhere far from your bed, so passing it requires actually walking there.
 */
@Composable
fun QrMission(
    expectedPayload: String,
    onPass: () -> Unit,
    onWrong: () -> Unit,
    onSubstitute: () -> Unit,
) {
    val c = LocalWake.current
    val s = LocalStrings.current

    if (expectedPayload.isBlank()) {
        MissionStatusLine(s.qrNoCodeSet, bad = true)
        LaunchedEffect(Unit) { delay(1200); onSubstitute() }
        return
    }

    CameraGate(denied = { req -> CameraDeniedPanel(req, onSubstitute) }) {
        var scanned by remember { mutableStateOf<String?>(null) }
        var wrong by remember { mutableStateOf(false) }
        var passed by remember { mutableStateOf(false) }

        val analyzer = remember { QrAnalyzer { scanned = it } }
        DisposableEffect(Unit) { onDispose { analyzer.close() } }

        LaunchedEffect(scanned) {
            val v = scanned ?: return@LaunchedEffect
            if (QrGen.matches(v, expectedPayload)) {
                if (!passed) {
                    passed = true
                    delay(600)
                    onPass()
                }
            } else {
                wrong = true
                onWrong()
                delay(1400)
                wrong = false
                scanned = null
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                CameraPreview(
                    analyzer = analyzer,
                    modifier = Modifier.fillMaxSize(),
                    lensFacing = CameraSelector.LENS_FACING_BACK,
                )
                Canvas(Modifier.fillMaxSize()) {
                    val side = size.minDimension * 0.62f
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    val arm = side * 0.18f
                    val col = if (passed) c.accent else if (wrong) c.danger else c.accent
                    listOf(
                        Offset(left, top) to listOf(Offset(arm, 0f), Offset(0f, arm)),
                        Offset(left + side, top) to listOf(Offset(-arm, 0f), Offset(0f, arm)),
                        Offset(left, top + side) to listOf(Offset(arm, 0f), Offset(0f, -arm)),
                        Offset(left + side, top + side) to
                            listOf(Offset(-arm, 0f), Offset(0f, -arm)),
                    ).forEach { (corner, arms) ->
                        arms.forEach { a ->
                            drawLine(
                                color = col,
                                start = corner,
                                end = Offset(corner.x + a.x, corner.y + a.y),
                                strokeWidth = 4f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MissionStatusLine(
                when {
                    passed -> s.qrLocked
                    wrong -> s.qrWrongCode
                    else -> s.qrLookingFor
                },
                good = passed,
                bad = wrong,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}
