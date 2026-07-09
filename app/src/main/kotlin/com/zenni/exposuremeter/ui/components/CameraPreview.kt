package com.zenni.exposuremeter.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zenni.exposuremeter.metering.Ev100Reading
import com.zenni.exposuremeter.metering.ReflectedCameraMeter
import kotlin.math.min

/**
 * The reflected-mode camera surface (brief §3.2, §5): a live preview the user
 * taps to place the spot, with a reticle drawn at the tap point. Requests CAMERA
 * only when this composable first appears — i.e. when the user switches to
 * reflected mode, not at launch (brief §2).
 */
@Composable
fun CameraPreview(
    spot: Offset,
    onSpotChanged: (Offset) -> Unit,
    onReading: (Ev100Reading) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Column(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Reflected metering needs camera access to read the scene.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access")
            }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val meter = remember { ReflectedCameraMeter(context) }

    // Keep the analyser's spot in sync with the UI.
    meter.spot = floatArrayOf(spot.x, spot.y)

    DisposableEffect(granted) {
        meter.start(lifecycleOwner, previewView, onReading, onError)
        onDispose { meter.stop() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    onSpotChanged(
                        Offset(
                            (tap.x / size.width).coerceIn(0f, 1f),
                            (tap.y / size.height).coerceIn(0f, 1f),
                        ),
                    )
                }
            },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Reticle(spot, Modifier.fillMaxSize())
    }
}

/** Crosshair + circle reticle at the normalised [spot] (brief §3.2). */
@Composable
private fun Reticle(spot: Offset, modifier: Modifier) {
    val reticleColor = Color.White
    Canvas(modifier = modifier) {
        val cx = spot.x * size.width
        val cy = spot.y * size.height
        val radius = 0.05f * min(size.width, size.height) // matches the sampled region
        // Outline ring (not filled) so the metered area stays visible.
        drawCircle(
            color = Color.Black,
            radius = radius + 1f,
            center = Offset(cx, cy),
            alpha = 0.6f,
            style = Stroke(width = 4f),
        )
        drawCircle(
            color = reticleColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f),
        )
        val tick = radius * 0.5f
        drawLine(reticleColor, Offset(cx - tick, cy), Offset(cx + tick, cy), strokeWidth = 2f)
        drawLine(reticleColor, Offset(cx, cy - tick), Offset(cx, cy + tick), strokeWidth = 2f)
    }
}
