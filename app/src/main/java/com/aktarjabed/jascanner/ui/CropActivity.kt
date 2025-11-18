package com.aktarjabed.jascanner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aktarjabed.jascanner.databinding.ActivityCropBinding
import com.aktarjabed.jascanner.utils.BitmapExtensions
import com.aktarjabed.jascanner.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Point
import java.io.File

class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }

    private lateinit var binding: ActivityCropBinding
    private var imageFilePath: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageFilePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imageFilePath == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadImage()
        setupListeners()
    }

    private fun loadImage() {
        val path = imageFilePath ?: return
        val bmp = BitmapFactory.decodeFile(path) ?: return
        binding.cropView.setImageBitmap(bmp)
    }

    private fun setupListeners() {
        binding.resetButton.setOnClickListener {
            binding.cropView.resetCorners()
        }

        binding.rotateButton.setOnClickListener {
            val bmp = binding.cropView.drawableBitmap() ?: return@setOnClickListener
            val rotated = bmp.rotate(90f)
            binding.cropView.setImageBitmap(rotated)
        }

        binding.saveButton.setOnClickListener {
            performCropAndSave()
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun performCropAndSave() {
        scope.launch {
            val result: File? = withContext(Dispatchers.IO) {
                try {
                    val bmp = binding.cropView.drawableBitmap() ?: return@withContext null
                    val pts = binding.cropView.getCornerPoints().map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
                    val cropped = bmp.cropToQuadrilateral(pts)
                    if (cropped != null) {
                        val out = FileUtils.createOutputFile(this@CropActivity, "crop_", ".jpg")
                        cropped.saveToFile(out)
                        out
                    } else null
                } catch (t: Throwable) {
                    t.printStackTrace()
                    null
                }
            }

            if (result != null) {
                Toast.makeText(this@CropActivity, "Saved: ${result.name}", Toast.LENGTH_SHORT).show()
                // Return path as result
                val intent = intent.apply { putExtra("cropped_path", result.absolutePath) }
                setResult(RESULT_OK, intent)
                finish()
            } else {
                Toast.makeText(this@CropActivity, "Crop failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

}