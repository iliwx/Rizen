package com.rizen.app.missions

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.guava.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera preview with a single [ImageAnalysis] consumer attached.
 *
 * Backpressure is STRATEGY_KEEP_ONLY_LATEST: ML Kit is slower than the sensor and a
 * queued backlog would make the on-screen feedback lag behind the user's face, which
 * feels broken exactly when the user is least patient.
 */
@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    LaunchedEffect(lensFacing) {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                analysis,
            )
        }.onFailure(onError)
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Asks for CAMERA once, then renders [granted] or [denied]. Deliberately does not nag in
 * a loop — the mission screen offers an alternative mission instead.
 */
@Composable
fun CameraGate(
    denied: @Composable (request: () -> Unit) -> Unit,
    granted: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCamera()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) granted() else denied { launcher.launch(Manifest.permission.CAMERA) }
}

private fun Context.hasCamera() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
