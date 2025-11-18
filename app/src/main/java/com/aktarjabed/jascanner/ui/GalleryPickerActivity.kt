package com.aktarjabed.jascanner.ui

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import com.aktarjabed.jascanner.batch.BatchScanManager
import com.aktarjabed.jascanner.databinding.ActivityGalleryPickerBinding
import com.aktarjabed.jascanner.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryPickerActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor> {

    private lateinit var binding: ActivityGalleryPickerBinding
    private lateinit var adapter: GalleryImageAdapter
    private lateinit var batchManager: BatchScanManager
    private val selectedUris = mutableSetOf<Uri>()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        batchManager = BatchScanManager(this)

        setupRecyclerView()
        setupListeners()

        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
    }

    private fun setupRecyclerView() {
        adapter = GalleryImageAdapter { uri, isSelected ->
            if (isSelected) selectedUris.add(uri) else selectedUris.remove(uri)
            updateSelectionCount()
        }

        binding.galleryRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.galleryRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.selectAllButton.setOnClickListener { selectAll() }
        binding.importButton.setOnClickListener { importSelected() }
    }

    private fun selectAll() {
        val allUris = adapter.getAllUris()
        selectedUris.clear()
        selectedUris.addAll(allUris)
        adapter.setSelectedUris(selectedUris)
        updateSelectionCount()
    }

    private fun updateSelectionCount() {
        binding.selectionCountText.text = "${selectedUris.size} selected"
        binding.importButton.isEnabled = selectedUris.isNotEmpty()
    }

    private fun importSelected() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }

        binding.importButton.isEnabled = false
        binding.importButton.text = "Importing..."

        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                var count = 0
                selectedUris.forEach { uri ->
                    try {
                        val file = FileUtils.copyUriToFile(this@GalleryPickerActivity, uri)
                        if (file != null) {
                            batchManager.addPageAsync(file)
                            count++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                count
            }

            Toast.makeText(this@GalleryPickerActivity, "Imported $imported images", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.MIME_TYPE} = ? OR ${MediaStore.Images.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("image/jpeg", "image/png")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return CursorLoader(
            this,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor == null) return

        val images = mutableListOf<Uri>()
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            images.add(contentUri)
        }

        adapter.submitList(images)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        adapter.submitList(emptyList())
    }

    companion object {
        private const val LOADER_ID = 1
    }

}