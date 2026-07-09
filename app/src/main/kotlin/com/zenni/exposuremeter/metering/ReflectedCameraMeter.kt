package com.zenni.exposuremeter.metering

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zenni.exposuremeter.engine.ExposureMath
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Reflected spot metering via CameraX (brief §3.2, §5).
 *
 * A live [Preview] fills the UI while an [ImageAnalysis] stream (RGBA_8888)
 * samples the tapped spot. Each analysed frame is combined with the camera's own
 * auto-exposure result — aperture N, exposure time t, ISO S read from the
 * [CaptureResult] via Camera2 interop — to derive scene EV₁₀₀ with
 * [ExposureMath.reflectedEv100]. AE is left running (brief §3.2): every frame
 * uses the current exposure, no lock.
 *
 * Aperture fallback chain (brief §3.2): `CaptureResult.LENS_APERTURE`, then
 * `CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES[0]`; if neither is
 * available the meter reports an error so reflected mode can be disabled with an
 * explanation rather than reading garbage.
 *
 * @property spot the tapped point in display-normalised coordinates (0..1, y
 *   down). Updated live from the UI; sampled each frame.
 */
@OptIn(ExperimentalCamera2Interop::class)
class ReflectedCameraMeter(private val context: Context) {

    @Volatile
    var spot: FloatArray = floatArrayOf(0.5f, 0.5f)

    @Volatile private var latestAperture: Double? = null
    @Volatile private var latestExposureNs: Long? = null
    @Volatile private var latestIso: Int? = null
    @Volatile private var apertureFallback: Double? = null
    private var apertureMisses = 0
    private var errorReported = false

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            latestAperture = result.get(CaptureResult.LENS_APERTURE)?.toDouble()
            latestExposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            latestIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        }
    }

    /**
     * Bind preview + analysis to [owner], rendering into [previewView]. [onReading]
     * fires (on the main thread) for each successfully metered frame; [onError]
     * fires once if aperture is unavailable on this device.
     */
    fun start(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onReading: (Ev100Reading) -> Unit,
        onError: (String) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            Camera2Interop.Extender(analysisBuilder).setSessionCaptureCallback(captureCallback)
            val analysis = analysisBuilder.build().also {
                it.setAnalyzer(analysisExecutor) { image -> process(image, onReading, onError) }
            }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
            )
            apertureFallback = runCatching {
                Camera2CameraInfo.from(camera.cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.firstOrNull()?.toDouble()
            }.getOrNull()
        }, mainExecutor)
    }

    /** Unbind the camera (called when leaving reflected mode / on dispose). */
    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        latestAperture = null
        latestExposureNs = null
        latestIso = null
        apertureMisses = 0
        errorReported = false
    }

    private fun process(
        image: ImageProxy,
        onReading: (Ev100Reading) -> Unit,
        onError: (String) -> Unit,
    ) {
        image.use {
            val aperture = latestAperture ?: apertureFallback
            val exposureNs = latestExposureNs
            val iso = latestIso

            if (aperture == null) {
                // No aperture from capture result or characteristics: after a short
                // grace period, disable reflected mode with an explanation.
                if (++apertureMisses > 20 && !errorReported) {
                    errorReported = true
                    mainExecutor.execute {
                        onError("This device does not report its aperture, so reflected metering is unavailable. Use incident or manual mode.")
                    }
                }
                return
            }
            if (exposureNs == null || iso == null) return // AE not settled yet

            val luma = regionLinearLuma(image) ?: return
            val ev = ExposureMath.reflectedEv100(
                apertureFNumber = aperture,
                exposureTimeSeconds = exposureNs / 1_000_000_000.0,
                iso = iso.toDouble(),
                regionLuminanceLinear = luma,
            )
            mainExecutor.execute { onReading(Ev100Reading(ev100 = ev)) }
        }
    }

    /**
     * Mean **linearised** luma over the circular spot region (brief §3.2): each
     * RGBA pixel is linearised from sRGB *before* averaging. Region diameter is
     * ~10% of the shorter image dimension, centred on [spot] mapped from display
     * to buffer coordinates through the frame's rotation.
     *
     * @return the mean linear luminance, or null if no pixels were sampled.
     */
    private fun regionLinearLuma(image: ImageProxy): Double? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val (nx, ny) = mapSpotToBuffer(spot[0], spot[1], image.imageInfo.rotationDegrees)
        val centerX = (nx * width)
        val centerY = (ny * height)
        val radius = 0.05 * min(width, height) // diameter ~10% of shorter side
        val r2 = radius * radius

        val minX = (centerX - radius).toInt().coerceIn(0, width - 1)
        val maxX = (centerX + radius).toInt().coerceIn(0, width - 1)
        val minY = (centerY - radius).toInt().coerceIn(0, height - 1)
        val maxY = (centerY + radius).toInt().coerceIn(0, height - 1)

        var sum = 0.0
        var count = 0
        for (y in minY..maxY) {
            val dy = y - centerY
            val rowOffset = y * rowStride
            for (x in minX..maxX) {
                val dx = x - centerX
                if (dx * dx + dy * dy > r2) continue
                val offset = rowOffset + x * pixelStride
                val rLin = ExposureMath.srgbToLinear((buffer.get(offset).toInt() and 0xFF) / 255.0)
                val gLin = ExposureMath.srgbToLinear((buffer.get(offset + 1).toInt() and 0xFF) / 255.0)
                val bLin = ExposureMath.srgbToLinear((buffer.get(offset + 2).toInt() and 0xFF) / 255.0)
                sum += ExposureMath.linearLumaBt709(rLin, gLin, bLin)
                count++
            }
        }
        return if (count == 0) null else sum / count
    }

    /**
     * Map a display-normalised point to buffer-normalised coordinates by undoing
     * the frame's clockwise display rotation. Assumes a non-mirrored back camera;
     * exact placement may need on-device calibration.
     */
    private fun mapSpotToBuffer(nx: Float, ny: Float, rotationDegrees: Int): Pair<Double, Double> =
        when (rotationDegrees) {
            90 -> ny.toDouble() to (1.0 - nx)
            180 -> (1.0 - nx).toDouble() to (1.0 - ny)
            270 -> (1.0 - ny).toDouble() to nx.toDouble()
            else -> nx.toDouble() to ny.toDouble()
        }
}
