package com.jascanner.scanner.thz

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DummyTerahertzScanner @Inject constructor() : TerahertzScanner {
    
    private val _scanProgress = MutableStateFlow(0f)
    private var isInitialized = false
    
    override suspend fun initialize(): Boolean {
        return try {
            delay(1000) // Simulate initialization time
            isInitialized = true
            Timber.i("Dummy THz scanner initialized")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize dummy THz scanner")
            false
        }
    }

    override suspend fun isAvailable(): Boolean {
        return isInitialized
    }

    override suspend fun scan(settings: TerahertzScanner.ScanSettings): TerahertzScanner.ThzScanResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            if (!isInitialized) {
                return TerahertzScanner.ThzScanResult(
                    success = false,
                    processingTimeMs = 0,
                    error = "Scanner not initialized"
                )
            }

            // Simulate scanning progress
            for (i in 0..100 step 10) {
                _scanProgress.value = i / 100f
                delay(100)
            }

            val image = generateMockThzImage()
            val spectralData = generateMockSpectralData(settings)
            val analysis = generateMockAnalysis()
            
            val processingTime = System.currentTimeMillis() - startTime

            TerahertzScanner.ThzScanResult(
                success = true,
                image = image,
                spectralData = spectralData,
                analysis = analysis,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Dummy THz scan failed")
            TerahertzScanner.ThzScanResult(
                success = false,
                processingTimeMs = System.currentTimeMillis() - startTime,
                error = e.message
            )
        } finally {
            _scanProgress.value = 0f
        }
    }

    override suspend fun calibrate(): Boolean {
        return try {
            delay(2000) // Simulate calibration time
            Timber.i("Dummy THz scanner calibrated")
            true
        } catch (e: Exception) {
            Timber.e(e, "Calibration failed")
            false
        }
    }

    override fun getScanProgress(): Flow<Float> {
        return _scanProgress.asStateFlow()
    }

    override fun shutdown() {
        isInitialized = false
        _scanProgress.value = 0f
        Timber.i("Dummy THz scanner shutdown")
    }

    private fun generateMockThzImage(): Bitmap {
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        val random = Random.Default
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Generate mock THz image with some patterns
                val noise = random.nextFloat() * 0.2f
                val pattern = kotlin.math.sin(x * 0.02) * kotlin.math.cos(y * 0.02) * 0.3f
                val intensity = (0.5f + pattern + noise).coerceIn(0f, 1f)
                
                val gray = (intensity * 255).toInt()
                pixels[y * width + x] = Color.rgb(gray, gray, gray)
            }
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun generateMockSpectralData(settings: TerahertzScanner.ScanSettings): TerahertzScanner.SpectralData {
        val numPoints = ((settings.frequencyRange.second - settings.frequencyRange.first) / settings.resolution).toInt()
        val frequencies = mutableListOf<Float>()
        val amplitudes = mutableListOf<Float>()
        val phases = mutableListOf<Float>()
        val timeStamps = mutableListOf<Long>()
        
        val baseTime = System.currentTimeMillis()
        val random = Random.Default
        
        for (i in 0 until numPoints) {
            val freq = settings.frequencyRange.first + i * settings.resolution
            frequencies.add(freq)
            
            // Generate mock amplitude with some spectral features
            val amplitude = when {
                freq < 0.5f -> 0.8f + random.nextFloat() * 0.2f
                freq < 1.0f -> 0.6f + kotlin.math.sin(freq * 10).toFloat() * 0.3f
                freq < 2.0f -> 0.4f + random.nextFloat() * 0.3f
                else -> 0.2f + random.nextFloat() * 0.1f
            }
            amplitudes.add(amplitude)
            
            // Generate mock phase
            phases.add(random.nextFloat() * 2 * kotlin.math.PI.toFloat())
            
            // Generate timestamps
            timeStamps.add(baseTime + i * 10)
        }
        
        return TerahertzScanner.SpectralData(
            frequencies = frequencies,
            amplitudes = amplitudes,
            phases = phases,
            timeStamps = timeStamps,
            resolution = settings.resolution,
            signalToNoise = 15.0f + random.nextFloat() * 10f
        )
    }

    private fun generateMockAnalysis(): TerahertzScanner.ThzAnalysis {
        val random = Random.Default
        
        val materials = listOf(
            TerahertzScanner.MaterialDetection(
                material = "Paper",
                confidence = 0.85f + random.nextFloat() * 0.1f,
                region = Rect(50, 50, 200, 300)
            ),
            TerahertzScanner.MaterialDetection(
                material = "Plastic",
                confidence = 0.75f + random.nextFloat() * 0.15f,
                region = Rect(250, 100, 400, 250)
            )
        )
        
        val defects = if (random.nextFloat() > 0.7f) {
            listOf(
                TerahertzScanner.DefectDetection(
                    type = TerahertzScanner.DefectType.THICKNESS_VARIATION,
                    location = Point(150, 200),
                    severity = random.nextFloat() * 0.5f,
                    description = "Minor thickness variation detected"
                )
            )
        } else {
            emptyList()
        }
        
        return TerahertzScanner.ThzAnalysis(
            materialComposition = materials,
            thickness = 0.2f + random.nextFloat() * 0.5f, // 0.2-0.7mm
            density = 0.8f + random.nextFloat() * 0.4f, // g/cm³
            moistureContent = random.nextFloat() * 5f, // 0-5%
            defects = defects,
            confidence = 0.8f + random.nextFloat() * 0.15f
        )
    }
}