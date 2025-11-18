package com.aktarjabed.jascanner.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aktarjabed.jascanner.batch.BatchScanManager
import com.aktarjabed.jascanner.databinding.ActivityDocumentListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentListBinding
    private lateinit var batchManager: BatchScanManager
    private lateinit var adapter: DocumentAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        batchManager = BatchScanManager(this)
        setupRecyclerView()
        loadDocuments()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onOpenClick = { file ->
                openDocument(file)
            },
            onDeleteClick = { file ->
                deleteDocument(file)
            }
        )
        binding.documentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.documentsRecyclerView.adapter = adapter
    }

    private fun loadDocuments() {
        // For a simple implementation, scan cacheDir and filesDir for pdfs
        scope.launch {
            val docs = withContext(Dispatchers.IO) {
                val list = mutableListOf<File>()
                val cache = cacheDir.listFiles()?.filter { it.extension.equals("pdf", true) } ?: emptyList()
                val files = filesDir.listFiles()?.filter { it.extension.equals("pdf", true) } ?: emptyList()
                list.addAll(cache)
                list.addAll(files)
                list.sortedByDescending { it.lastModified() }
            }
            adapter.submitList(docs)
            binding.countText.text = "${docs.size} documents"
        }
    }

    private fun setupListeners() {
        binding.newScanButton.setOnClickListener {
            // Launch main camera activity (MainActivity)
            val intent = Intent(this, com.aktarjabed.jascanner.MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun openDocument(file: File) {
        try {
            val authority = "${applicationContext.packageName}.provider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to open document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteDocument(file: File) {
        scope.launch {
            withContext(Dispatchers.IO) {
                file.delete()
            }
            loadDocuments()
            Toast.makeText(this@DocumentListActivity, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

}