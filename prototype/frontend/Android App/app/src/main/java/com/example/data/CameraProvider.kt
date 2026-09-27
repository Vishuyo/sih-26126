package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX wrapper handling camera lifecycle, Preview use case,
 * and ImageAnalysis frame capture converted to JPEG byte arrays.
 */
class CameraProvider(
    private val context: Context,
    private val onFrameCaptured: (ByteArray) -> Unit
) {
    private val tag = "UGV_CameraProvider"

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var processCameraProvider: ProcessCameraProvider? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isTorchOn = false

    private var lastFrameTimeMs = 0L
    private val frameIntervalMs = 33L // ~30 fps throttle

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onInitialized: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                processCameraProvider = cameraProviderFuture.get()
                bindUseCases(lifecycleOwner, previewView)
                onInitialized()
            } catch (e: Exception) {
                Log.w(tag, "Failed to bind camera use cases: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var previewUseCase: Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null

    fun updateTargetRotation(rotation: Int) {
        try {
            previewUseCase?.targetRotation = rotation
            imageAnalysisUseCase?.targetRotation = rotation
        } catch (e: Exception) {
            Log.w(tag, "Rotation update error: ${e.message}")
        }
    }

    private fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProvider = processCameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        // Use 1280x720 for wider FOV and better small-object detection
        // Backend letterboxes to 640x640 for YOLO inference
        val targetResolution = Size(1280, 720)

        val preview = Preview.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(displayRotation)
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }
        previewUseCase = preview

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(displayRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processFrame(imageProxy)
        }
        imageAnalysisUseCase = imageAnalysis

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Apply AE/AF lock for stable frames after binding
            applyAeAfLock()
        } catch (e: Exception) {
            Log.w(tag, "Use case binding warning: ${e.message}")
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyAeAfLock() {
        camera?.cameraControl?.let { control ->
            try {
                // Lock auto-exposure and auto-focus to prevent frame brightness/focus shifts
                val camera2Control = Camera2CameraControl.from(control)
                val captureRequestOptions = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    .build()
                camera2Control.setCaptureRequestOptions(captureRequestOptions)
            } catch (e: Exception) {
                Log.w(tag, "AE/AF lock failed: ${e.message}")
            }
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastFrameTimeMs < frameIntervalMs) {
            imageProxy.close()
            return
        }
        lastFrameTimeMs = now

        try {
            val bitmap = imageProxy.toBitmap()
            val stream = ByteArrayOutputStream()
            // JPEG Q85 for better feature preservation (backend uses this for detection)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val jpegBytes = stream.toByteArray()
            onFrameCaptured(jpegBytes)
        } catch (e: Exception) {
            Log.w(tag, "Frame conversion warning: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun toggleTorch(): Boolean {
        camera?.cameraControl?.let { control ->
            isTorchOn = !isTorchOn
            control.enableTorch(isTorchOn)
            return isTorchOn
        }
        return false
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases(lifecycleOwner, previewView)
    }

    fun release() {
        try {
            processCameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing camera: ${e.message}")
        }
    }

    /**
     * Generates a synthetic test frame for simulation mode or when physical camera is offline.
     */
    fun generateSyntheticFrame(tick: Long): ByteArray {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Background terrain gradient
        paint.color = 0xFF232A32.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Ground plane
        paint.color = 0xFF181D23.toInt()
        canvas.drawRect(0f, height * 0.5f, width.toFloat(), height.toFloat(), paint)

        // Grid lines simulating UGV path
        paint.color = 0x3300E676.toInt()
        paint.strokeWidth = 2f
        for (i in -4..4) {
            val xBottom = width * 0.5f + i * 80f
            val xTop = width * 0.5f + i * 20f
            canvas.drawLine(xTop, height * 0.5f, xBottom, height.toFloat(), paint)
        }

        // Horizontal distance rings
        for (y in 1..4) {
            val lineY = height * 0.5f + (y * y) * 20f
            canvas.drawLine(0f, lineY, width.toFloat(), lineY, paint)
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        return stream.toByteArray()
    }
}
