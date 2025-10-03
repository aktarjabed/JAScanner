package com.jascanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jascanner.data.local.DocumentDao
import com.jascanner.data.local.entities.DocumentEntity
import com.jascanner.data.local.entities.PageEntity
import com.jascanner.domain.model.EditableDocument
import com.jascanner.domain.model.EditablePage
import com.jascanner.domain.model.TextBlock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    @ApplicationContext private val context: Context
) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun loadEditableDocument(documentId: String): EditableDocument? = withContext(Dispatchers.IO) {
        val documentEntity = documentDao.getDocumentById(documentId)
        val pages = documentDao.getPagesForDocument(documentId)

        documentEntity?.let { docEntity ->
            EditableDocument(
                id = docEntity.id,
                name = docEntity.name,
                createdAt = docEntity.createdAt,
                modifiedAt = docEntity.modifiedAt,
                hasSignature = docEntity.hasSignature,
                signatureInvalidated = docEntity.signatureInvalidated,
                pages = pages.map { pageEntity ->
                    EditablePage(
                        pageId = pageEntity.id,
                        originalImageUri = pageEntity.originalImageUri,
                        processedImageUri = pageEntity.processedImageUri
                    )
                }
            )
        }
    }

    suspend fun saveEditableDocument(document: EditableDocument) = withContext(Dispatchers.IO) {
        val documentEntity = DocumentEntity(
            id = document.id,
            name = document.name,
            createdAt = document.createdAt,
            modifiedAt = System.currentTimeMillis(),
            hasSignature = document.hasSignature,
            signatureInvalidated = document.signatureInvalidated,
            pageCount = document.pages.size
        )
        documentDao.insertDocument(documentEntity)

        val pageEntities = document.pages.mapIndexed { index, editablePage ->
            PageEntity(
                id = editablePage.pageId,
                documentId = document.id,
                pageNumber = index,
                originalImageUri = editablePage.originalImageUri,
                processedImageUri = editablePage.processedImageUri
            )
        }
        documentDao.insertPages(pageEntities)
    }

    suspend fun performOCR(bitmap: Bitmap): List<TextBlock> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(inputImage).await()

            result.textBlocks.map { block ->
                TextBlock(
                    id = UUID.randomUUID().toString(),
                    text = block.text,
                    boundingBox = block.boundingBox?.let {
                        android.graphics.RectF(
                            it.left.toFloat(),
                            it.top.toFloat(),
                            it.right.toFloat(),
                            it.bottom.toFloat()
                        )
                    } ?: android.graphics.RectF(),
                    confidence = block.confidence
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun cropBitmap(
        bitmap: Bitmap,
        cropRect: android.graphics.RectF
    ): Bitmap = withContext(Dispatchers.IO) {
        val x = cropRect.left.toInt().coerceIn(0, bitmap.width)
        val y = cropRect.top.toInt().coerceIn(0, bitmap.height)
        val width = (cropRect.width().toInt()).coerceIn(1, bitmap.width - x)
        val height = (cropRect.height().toInt()).coerceIn(1, bitmap.height - y)

        Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    suspend fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap = withContext(Dispatchers.IO) {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun loadBitmap(uriString: String): Bitmap {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "content" || uri.scheme == "file") {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Cannot load bitmap from $uriString")
        } else {
            BitmapFactory.decodeFile(uriString)
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap, filename: String): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }
}