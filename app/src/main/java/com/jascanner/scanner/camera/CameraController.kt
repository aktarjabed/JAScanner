package com.jascanner.scanner.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jascanner.device.DeviceCapabilitiesDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CameraController @Inject constructor(
    private val context: Context,
    private val deviceCapabilities: DeviceCapabilitiesDetector
) {
    
    data class CameraState(
        val isInitialized: Boolean = false,
        val isCapturing: Boolean = false,
        val hasFlash: Boolean = false,
        val flashEnabled: Boolean = false,
        val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        val error: String? = null
    )

    data class CaptureResult(
        val success: Boolean,
        val bitmap: Bitmap? = null,
        val file: File? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val burstCaptureManager = BurstCaptureManager()
    private val focusEvaluator = FocusEvaluator()

    suspend fun initialize(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    val capabilities = deviceCapabilities.getCapabilities()
                    _state.value = _state.value.copy(
                        isInitialized = true,
                        hasFlash = capabilities.hasFlash,
                        error = null
                    )
                    Timber.i("Camera initialized successfully")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize camera")
                    _state.value = _state.value.copy(error = e.message)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }, ContextCompat.getMainExecutor(context))

            continuation.invokeOnCancellation {
                cameraProviderFuture.cancel(true)
            }
        }
    }

    fun startPreview(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        try {
            val provider = cameraProvider ?: return
            
            // Preview use case
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image capture use case
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(_state.value.lensFacing)
                .build()

            // Unbind all use cases before rebinding
            provider.unbindAll()

            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            // Set up tap to focus
            setupTapToFocus(previewView)
            
            Timber.i("Camera preview started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start camera preview")
            _state.value = _state.value.copy(error = e.message)
        }
    }

    private fun setupTapToFocus(previewView: PreviewView) {
        previewView.setOnTouchListener { _, event ->
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point).build()
            
            camera?.cameraControl?.startFocusAndMetering(action)
            true
        }
    }

    suspend fun captureImage(outputFile: File): CaptureResult {
        return try {
            _state.value = _state.value.copy(isCapturing = true)
            
            val imageCapture = this.imageCapture ?: return CaptureResult(
                success = false,
                error = "Image capture not initialized"
            )

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            
            var result: CaptureResult? = null
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        result = CaptureResult(
                            success = true,
                            bitmap = bitmap,
                            file = outputFile
                        )
                        Timber.i("Image captured: ${outputFile.absolutePath}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        result = CaptureResult(
                            success = false,
                            error = exception.message
                        )
                        Timber.e(exception, "Image capture failed")
                    }
                }
            )

            // Wait for capture to complete (simplified for demo)
            while (result == null) {
                kotlinx.coroutines.delay(100)
            }
            
            _state.value = _state.value.copy(isCapturing = false)
            result!!
        } catch (e: Exception) {
            _state.value = _state.value.copy(isCapturing = false, error = e.message)
            CaptureResult(success = false, error = e.message)
        }
    }

    suspend fun captureBurst(outputDir: File, count: Int = 3): List<CaptureResult> {
        return burstCaptureManager.captureBurst(
            imageCapture = imageCapture,
            outputDir = outputDir,
            count = count,
            context = context
        )
    }

    fun toggleFlash() {
        if (!_state.value.hasFlash) return
        
        val newFlashState = !_state.value.flashEnabled
        camera?.cameraControl?.enableTorch(newFlashState)
        _state.value = _state.value.copy(flashEnabled = newFlashState)
    }

    fun switchCamera() {
        val newLensFacing = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        
        _state.value = _state.value.copy(lensFacing = newLensFacing)
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    fun getZoomState() = camera?.cameraInfo?.zoomState

    fun evaluateFocus(bitmap: Bitmap): FocusEvaluator.FocusResult {
        return focusEvaluator.evaluateFocus(bitmap)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}