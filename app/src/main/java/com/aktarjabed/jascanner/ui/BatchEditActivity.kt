package com.aktarjabed.jascanner.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.aktarjabed.jascanner.batch.BatchScanManager
import com.aktarjabed.jascanner.databinding.ActivityBatchEditBinding
import com.aktarjabed.jascanner.enhancer.ImageEnhancer
import com.aktarjabed.jascanner.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchEditBinding
    private lateinit var batchManager: BatchScanManager
    private lateinit var adapter: PageAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        batchManager = BatchScanManager(this)

        setupRecyclerView()
        setupListeners()
        loadPages()
    }

    private fun setupRecyclerView() {
        adapter = PageAdapter(
            onDeleteClick = { page ->
                batchManager.removePage(page.id)
                loadPages()
            },
            onEnhanceClick = { page ->
                showEnhancementDialog(page)
            }
        )

        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pagesRecyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(PageDragCallback(adapter) { fromPos, toPos ->
            val pages = batchManager.getPages().toMutableList()
            if (fromPos in pages.indices && toPos in pages.indices) {
                val moved = pages.removeAt(fromPos)
                pages.add(toPos, moved)
                // Rebuild batch manager pages in new order
                val originals = pages.map { it.originalFile }
                batchManager.clearAll()
                originals.forEach { batchManager.addPageAsync(it) }
                loadPages()
            }
        })
        touchHelper.attachToRecyclerView(binding.pagesRecyclerView)
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.exportButton.setOnClickListener { exportToPdf() }

        binding.addPageButton.setOnClickListener {
            // Launch camera or gallery — simple intent to GalleryPickerActivity for now
            val intent = Intent(this, GalleryPickerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadPages() {
        val pages = batchManager.getPages()
        adapter.submitList(pages)
        binding.pageCountText.text = "${pages.size} pages"
    }

    private fun showEnhancementDialog(page: BatchScanManager.ScannedPage) {
        val options = ImageEnhancer.EnhancementType.values()
        val items = options.map { it.name }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enhancement Type")
            .setItems(items) { _, which ->
                scope.launch {
                    val type = options[which]
                    val success = batchManager.updatePageEnhancement(page.id, type)
                    if (success) {
                        loadPages()
                        Toast.makeText(this@BatchEditActivity, "Enhanced as ${type.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@BatchEditActivity, "Enhancement failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun exportToPdf() {
        if (batchManager.pageCount() == 0) {
            Toast.makeText(this, "No pages to export", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val result: File? = withContext(Dispatchers.IO) {
                try {
                    val outputFile = FileUtils.createOutputFile(this@BatchEditActivity, "scanned_", ".pdf")
                    val ok = batchManager.exportToPdf(outputFile)
                    if (ok) outputFile else null
                } catch (t: Throwable) {
                    t.printStackTrace()
                    null
                }
            }

            if (result != null) {
                Toast.makeText(this@BatchEditActivity, "PDF saved: ${result.name}", Toast.LENGTH_LONG).show()
                sharePdf(result)
            } else {
                Toast.makeText(this@BatchEditActivity, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sharePdf(file: File) {
        try {
            val authority = "${applicationContext.packageName}.provider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share PDF", Toast.LENGTH_SHORT).show()
        }
    }

}