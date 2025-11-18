package com.aktarjabed.jascanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.aktarjabed.jascanner.databinding.ActivityMainBinding
import com.aktarjabed.jascanner.scanner.DocumentDetector
import com.aktarjabed.jascanner.scanner.DocumentStabilityTracker
import com.aktarjabed.jascanner.ui.BatchEditActivity
import com.aktarjabed.jascanner.ui.DocumentListActivity
import com.aktarjabed.jascanner.ui.GalleryPickerActivity
import com.aktarjabed.jascanner.util.CoordinateMapper
import com.aktarjabed.jascanner.util.ImageUtils
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: DocumentDetector
    private lateinit var stabilityTracker: DocumentStabilityTracker

    private var imageCapture: ImageCapture? = null

    private val lastAnalyzed = AtomicLong(0)
    private val lastCapturedAt = AtomicLong(0)
    private val targetFps = 5
    private val minMillisBetween = 1000L / targetFps
    private val analysisMaxDim = 1024
    private val captureCooldownMs = 2000L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Images imported successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = DocumentDetector()
        stabilityTracker = DocumentStabilityTracker()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.captureButton.setOnClickListener {
            captureImage()
        }

        binding.galleryButton.setOnClickListener {
            openGalleryPicker()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                var fullBitmap: Bitmap? = null
                var analysisBitmap: Bitmap? = null

                try {
                    val now = System.currentTimeMillis()

                    if (now - lastAnalyzed.get() < minMillisBetween) {
                        return@setAnalyzer
                    }
                    lastAnalyzed.set(now)

                    val rotation = imageProxy.imageInfo.rotationDegrees

                    fullBitmap = ImageUtils.imageProxyToBitmap(imageProxy, rotation)
                    if (fullBitmap == null) {
                        Log.w(TAG, "Failed to convert ImageProxy to Bitmap")
                        return@setAnalyzer
                    }

                    analysisBitmap = if (maxOf(fullBitmap.width, fullBitmap.height) > analysisMaxDim) {
                        val scaled = ImageUtils.createDownscaledBitmap(fullBitmap, analysisMaxDim)
                        if (scaled !== fullBitmap) {
                            fullBitmap.recycle()
                            fullBitmap = null
                        }
                        scaled
                    } else {
                        fullBitmap
                    }

                    val corners: List<org.opencv.core.Point>? = try {
                        detector.detect(analysisBitmap)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Detection failed", t)
                        null
                    }

                    if (corners == null || corners.size != 4) {
                        stabilityTracker.reset()
                        runOnUiThread { binding.documentOverlay.clear() }
                    } else {
                        val mappedCorners = CoordinateMapper.mapPoints(
                            points = corners,
                            analysisWidth = analysisBitmap.width,
                            analysisHeight = analysisBitmap.height,
                            previewView = binding.previewView,
                            rotationDegrees = rotation
                        )

                        runOnUiThread { binding.documentOverlay.setCorners(mappedCorners) }

                        val isStable = stabilityTracker.push(corners)
                        if (isStable) {
                            val lastCapture = lastCapturedAt.get()
                            if (now - lastCapture > captureCooldownMs) {
                                lastCapturedAt.set(now)
                                runOnUiThread { triggerAutoCapture() }
                                stabilityTracker.reset()
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Analyzer error", e)
                } finally {
                    imageProxy.close()
                    try {
                        analysisBitmap?.let { if (!it.isRecycled) it.recycle() }
                        fullBitmap?.let { if (!it.isRecycled) it.recycle() }
                    } catch (recycleError: Exception) {
                        Log.w(TAG, "Bitmap recycle error", recycleError)
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun triggerAutoCapture() {
        Toast.makeText(this, "Document detected - capturing...", Toast.LENGTH_SHORT).show()
        captureImage()
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "scan_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@MainActivity,
                        "Image captured: ${photoFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Photo saved: ${photoFile.absolutePath}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    Toast.makeText(
                        this@MainActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun openGalleryPicker() {
        if (ContextCompat.checkSelfPermission(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(this, GalleryPickerActivity::class.java)
            galleryLauncher.launch(intent)
        } else {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissionLauncher.launch(permission)
    }

    private fun openBatchEditor() {
        val intent = Intent(this, BatchEditActivity::class.java)
        startActivity(intent)
    }

    private fun showMainMenu() {
        val items = arrayOf(
            "Batch Editor",
            "My Documents",
            "Import from Gallery",
            "Settings"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("JAScanner")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openBatchEditor()
                    1 -> openDocumentList()
                    2 -> openGalleryPicker()
                    3 -> showSettings()
                }
            }
            .show()
    }

    private fun openDocumentList() {
        val intent = Intent(this, DocumentListActivity::class.java)
        startActivity(intent)
    }

    private fun showSettings() {
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_menu -> {
                showMainMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}