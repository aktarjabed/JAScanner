package com.aktarjabed.jascanner

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.core.content.FileProvider
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

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private val detector = DocumentDetector()
    private val stabilityTracker = DocumentStabilityTracker(requiredStableFrames = 3, maxCornerMovementPx = 8.0)

    // Analysis throttling
    private val targetFps = 8
    private val minMillisBetween = 1000L / maxOf(1, targetFps)
    private val lastAnalyzed = AtomicLong(0)

    // Capture cooldown (prevent spam)
    private val lastCapturedAt = AtomicLong(0)
    private val captureCooldownMs = 1500L

    // Analysis sizing
    private val analysisMaxDim = 640
    private val analysisTarget = Size(1280, 720)

    private lateinit var batchManager: BatchScanManager
    private var currentEnhancementType = ImageEnhancer.EnhancementType.AUTO

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
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

        // request camera permission
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
                // Always ensure imageProxy gets closed
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastAnalyzed.get() < minMillisBetween) {
                        return@setAnalyzer
                    }
                    lastAnalyzed.set(now)

                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val fullBitmap = ImageUtils.imageProxyToBitmap(imageProxy, rotation)
                    if (fullBitmap == null) {
                        Log.w(TAG, "imageProxy -> bitmap conversion returned null")
                        return@setAnalyzer
                    }

                    val analysisBitmap = ImageUtils.createDownscaledBitmap(fullBitmap, analysisMaxDim)
                    if (analysisBitmap !== fullBitmap) fullBitmap.recycle()

                    // Run detection
                    val corners: List<Point>? = try {
                        detector.detect(analysisBitmap)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Document detection failed", t)
                        null
                    }

                    if (corners == null) {
                        // Nothing detected -> reset and clear overlay
                        stabilityTracker.reset()
                        runOnUiThread { overlayView.clear() }
                    } else {
                        // Map coords into preview view space (accounting for center-crop)
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
                                triggerAutoCapture() // safe: uses imageCapture and a file you control
                                stabilityTracker.reset()
                            }
                        }
                    }

                    analysisBitmap.recycle()
                } finally {
                    // must close
                    try {
                        imageProxy.close()
                    } catch (ignored: Exception) {
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
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
        val capture = imageCapture ?: run {
            Log.w(TAG, "triggerAutoCapture: imageCapture not ready")
            return
        }

        // create file we control and pass it into OutputFileOptions
        val outFile = File(getOutputDirectory(), "scan_${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()

        capture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Use the outFile reference (guaranteed) instead of savedUri which may be null
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            batchManager.addPageAsync(outFile) { page ->
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Page added to batch (${batchManager.pageCount()} total)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error processing saved capture", t)
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
            // cooldown active
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

                // apply to existing pages asynchronously
                lifecycleScope.launch(Dispatchers.IO) {
                    for (page in batchManager.getPages()) {
                        try {
                            batchManager.updatePageEnhancement(page.id, currentEnhancementType)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed update enhancement for ${page.id}", t)
                        }
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
                if (pageCount > 0) exportBatchToPdf()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportBatchToPdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            val outputFile = File(getOutputDirectory(), "batch_${System.currentTimeMillis()}.pdf")
            val success = try {
                batchManager.exportToPdf(outputFile)
            } catch (t: Throwable) {
                Log.e(TAG, "Export failed", t)
                false
            }

            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MainActivity, "PDF saved: ${outputFile.name}", Toast.LENGTH_SHORT).show()
                    sharePdf(outputFile)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to export PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sharePdf(pdfFile: File) {
        // Use the Activity context and the exact authority declared in manifest
        val uri: Uri = try {
            FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.provider",
                pdfFile
            )
        } catch (t: Throwable) {
            Log.e(TAG, "FileProvider failed", t)
            Toast.makeText(this, "Cannot share file", Toast.LENGTH_SHORT).show()
            return
        }

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
                    // getPathFromUri is best-effort — prefer using InputStream copy in production
                    val path = getPathFromUri(uri)
                    if (path.isNotEmpty()) {
                        batchManager.addPageAsync(File(path)) { page ->
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Page added to batch (${batchManager.pageCount()} total)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Unable to read selected image", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String {
        // Best-effort fallback for older devices/quick copy; consider copying InputStream -> temp file instead
        var path = ""
        val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
            if (cursor.moveToFirst()) path = cursor.getString(columnIndex)
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