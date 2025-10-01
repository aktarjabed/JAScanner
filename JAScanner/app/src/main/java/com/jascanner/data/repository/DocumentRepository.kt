package com.jascanner.data.repository

import android.graphics.BitmapFactory
import com.jascanner.data.local.DocumentDao
import com.jascanner.data.local.entities.DocumentEntity
import com.jascanner.data.local.entities.PageEntity
import com.jascanner.domain.model.EditableDocument
import com.jascanner.domain.model.EditablePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao
) {

    fun getAllDocuments(): Flow<List<DocumentEntity>> {
        return documentDao.getAllDocuments()
    }

    suspend fun loadEditableDocument(documentId: String): EditableDocument? {
        val docEntity = documentDao.getDocumentById(documentId) ?: return null
        val pageEntities = documentDao.getPagesForDocument(documentId)
        return docEntity.toEditableDocument(pageEntities)
    }

    suspend fun saveEditableDocument(document: EditableDocument) {
        documentDao.insertDocument(document.toDocumentEntity())
        documentDao.insertPages(document.pages.map { it.toPageEntity(document.id) })
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document.id)
    }

    // Mappers
    private fun DocumentEntity.toEditableDocument(pages: List<PageEntity>): EditableDocument {
        return EditableDocument(
            id = this.id,
            name = this.name,
            createdAt = this.createdAt,
            modifiedAt = this.modifiedAt,
            pages = pages.map { it.toEditablePage() }
        )
    }

    private fun PageEntity.toEditablePage(): EditablePage {
        // In a real app, you would load the bitmap from the path asynchronously
        val bitmap = try {
            BitmapFactory.decodeFile(this.imagePath)
        } catch (e: Exception) {
            null
        }
        return EditablePage(
            pageId = this.id,
            pageNumber = this.pageNumber,
            originalImageUri = this.imagePath,
            originalBitmap = bitmap
        )
    }

    private fun EditableDocument.toDocumentEntity(): DocumentEntity {
        return DocumentEntity(
            id = this.id,
            name = this.name,
            createdAt = this.createdAt,
            modifiedAt = this.modifiedAt
        )
    }

    private fun EditablePage.toPageEntity(docId: String): PageEntity {
        return PageEntity(
            id = this.pageId,
            documentId = docId,
            pageNumber = this.pageNumber,
            imagePath = this.originalImageUri
        )
    }
}