package com.jascanner.device.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jascanner.core.EnhancedErrorHandler
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RobustCameraController(
    private val context: Context,
    private val errorHandler: EnhancedErrorHandler
) {

    sealed class CameraState {
        object Idle : CameraState()
        object Initializing : CameraState()
        object Ready : CameraState()
        data class Error(val message: String) : CameraState()
    }

    sealed class Result<out T> {
        data class Success<out T>(val data: T) : Result<T>()
        data class Error(val exception: Exception) : Result<Nothing>()
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentState: CameraState = CameraState.Idle

    fun initialize(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        currentState = CameraState.Initializing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView, lifecycleOwner)
                currentState = CameraState.Ready
                Timber.d("Camera initialized successfully")
            } catch (e: Exception) {
                val errorMessage = "Failed to initialize camera"
                Timber.e(e, errorMessage)
                errorHandler.recordException(e, errorMessage)
                currentState = CameraState.Error(errorMessage)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            val errorMessage = "Failed to bind camera use cases"
            Timber.e(e, errorMessage)
            errorHandler.recordException(e, errorMessage)
            currentState = CameraState.Error(errorMessage)
        }
    }

    suspend fun captureImage(): Result<Bitmap> {
        val imageCapture = this.imageCapture ?: return Result.Error(IllegalStateException("Camera not initialized"))

        return withTimeoutOrNull(10000L) {
            suspendCoroutine { continuation ->
                imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
                        continuation.resume(Result.Success(bitmap))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Timber.e(exception, "Image capture failed")
                        errorHandler.recordException(exception, "Image capture failed")
                        continuation.resume(Result.Error(exception))
                    }
                })
            }
        } ?: Result.Error(Exception("Image capture timed out"))
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate bitmap if needed
        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
        return if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    fun release() {
        cameraProvider?.unbindAll()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        Timber.d("Camera resources released")
    }
}