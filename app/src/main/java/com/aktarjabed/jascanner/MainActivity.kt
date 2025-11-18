package com.aktarjabed.jascanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aktarjabed.jascanner.batch.BatchScanManager
import com.aktarjabed.jascanner.databinding.ActivityMainBinding
import com.aktarjabed.jascanner.detector.DocumentDetector
import com.aktarjabed.jascanner.detector.DocumentStabilityTracker
import com.aktarjabed.jascanner.enhancer.ImageEnhancer
import com.aktarjabed.jascanner.ui.OverlayView
import com.aktarjabed.jascanner.util.CoordinateMapper
import com.aktarjabed.jascanner.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Point
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import com.aktarjabed.jascanner.R

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private val detector = DocumentDetector()
    private val stabilityTracker = DocumentStabilityTracker(requiredStableFrames = 3, maxCornerMovementPx = 8.0)

    private val targetFps = 8
    private val minMillisBetween = 1000L / maxOf(1, targetFps)
    private val lastAnalyzed = AtomicLong(0)
    private val lastCapturedAt = AtomicLong(0)
    private val captureCooldownMs = 1500L

    private val analysisMaxDim = 640
    private val analysisTarget = Size(1280, 720)

    private lateinit var batchManager: BatchScanManager
    private var currentEnhancementType = ImageEnhancer.EnhancementType.AUTO

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = binding.previewView
        overlayView = binding.overlayView

        batchManager = BatchScanManager(this)

        binding.captureButton.setOnClickListener { manualCapture() }
        binding.galleryButton.setOnClickListener { openGallery() }
        binding.enhancementButton.setOnClickListener { showEnhancementOptions() }
        binding.batchButton.setOnClickListener { showBatchDialog() }

        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(analysisTarget)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(analysisTarget)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(analysisTarget)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastAnalyzed.get() < minMillisBetween) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastAnalyzed.set(now)

                val rotation = imageProxy.imageInfo.rotationDegrees
                val fullBitmap = ImageUtils.imageProxyToBitmap(imageProxy, rotation)
                if (fullBitmap == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val analysisBitmap = ImageUtils.createDownscaledBitmap(fullBitmap, analysisMaxDim)
                if (analysisBitmap !== fullBitmap) {
                    fullBitmap.recycle()
                }

                val corners: List<Point>? = try {
                    detector.detect(analysisBitmap)
                } catch (t: Throwable) {
                    Log.e(TAG, "Document detection failed", t)
                    null
                }

                if (corners == null) {
                    stabilityTracker.reset()
                    runOnUiThread { overlayView.clear() }
                } else {
                    val mapped = CoordinateMapper.mapPoints(
                        points = corners,
                        analysisWidth = analysisBitmap.width,
                        analysisHeight = analysisBitmap.height,
                        previewView = previewView,
                        rotationDegrees = rotation
                    )

                    runOnUiThread { overlayView.setCorners(mapped) }

                    val stable = stabilityTracker.push(corners)
                    if (stable) {
                        val lastCap = lastCapturedAt.get()
                        if (now - lastCap > captureCooldownMs) {
                            lastCapturedAt.set(now)
                            triggerAutoCapture()
                            stabilityTracker.reset()
                        }
                    }
                }

                analysisBitmap.recycle()
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun triggerAutoCapture() {
        val capture = imageCapture ?: return
        val outFile = File(getOutputDirectory(), "scan_${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()

        capture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Auto-capture saved: ${output.savedUri?.path}")

                    lifecycleScope.launch(Dispatchers.IO) {
                        batchManager.addPageAsync(File(output.savedUri?.path ?: return@launch)) { page ->
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Page added to batch (${batchManager.pageCount()} total)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Auto-capture failed", exception)
                }
            }
        )
    }

    private fun manualCapture() {
        val now = System.currentTimeMillis()
        val lastCap = lastCapturedAt.get()
        if (now - lastCap < captureCooldownMs) {
            return
        }
        lastCapturedAt.set(now)
        triggerAutoCapture()
    }

    private fun openGallery() {
        val pick = Intent(Intent.ACTION_PICK)
        pick.type = "image/*"
        startActivityForResult(pick, GALLERY_REQUEST_CODE)
    }

    private fun showEnhancementOptions() {
        val options = arrayOf("Auto", "Original", "Black & White", "Grayscale", "Magic Color")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enhancement Type")
            .setItems(options) { _, which ->
                currentEnhancementType = when (which) {
                    0 -> ImageEnhancer.EnhancementType.AUTO
                    1 -> ImageEnhancer.EnhancementType.ORIGINAL
                    2 -> ImageEnhancer.EnhancementType.BLACK_AND_WHITE
                    3 -> ImageEnhancer.EnhancementType.GRAYSCALE
                    4 -> ImageEnhancer.EnhancementType.MAGIC_COLOR
                    else -> ImageEnhancer.EnhancementType.AUTO
                }

                // Apply to all pages in batch
                lifecycleScope.launch(Dispatchers.IO) {
                    for (page in batchManager.getPages()) {
                        batchManager.updatePageEnhancement(page.id, currentEnhancementType)
                    }
                }
            }
            .show()
    }

    private fun showBatchDialog() {
        val pageCount = batchManager.pageCount()
        val message = if (pageCount > 0) {
            "You have $pageCount pages in batch. Export to PDF?"
        } else {
            "No pages in batch. Add some pages first."
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Batch Export")
            .setMessage(message)
            .setPositiveButton("Export") { _, _ ->
                if (pageCount > 0) {
                    exportBatchToPdf()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportBatchToPdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            val outputFile = File(getOutputDirectory(), "batch_${System.currentTimeMillis()}.pdf")
            val success = batchManager.exportToPdf(outputFile)

            runOnUiThread {
                if (success) {
                    Toast.makeText(
                        this@MainActivity,
                        "PDF saved: ${outputFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Share the PDF
                    sharePdf(outputFile)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to export PDF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sharePdf(pdfFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    batchManager.addPageAsync(File(getPathFromUri(uri))) { page ->
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Page added to batch (${batchManager.pageCount()} total)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun getPathFromUri(uri: android.net.Uri): String {
        var path = ""
        uri.let {
            val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(it, projection, null, null)
            cursor?.use {
                val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                if (it.moveToFirst()) {
                    path = it.getString(columnIndex)
                }
            }
        }
        return path
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val GALLERY_REQUEST_CODE = 1001
    }
}