package com.jascanner.repository

import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanSessionDao
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.entities.ScanSessionEntity
import com.jascanner.utils.FileManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val scanSessionDao: ScanSessionDao,
    private val fileManager: FileManager
) {
    suspend fun insertDocument(title: String, textContent: String, filePath: String, sessionId: Long? = null): Long {
        val doc = DocumentEntity(title = title, textContent = textContent, filePath = filePath, sessionId = sessionId)
        return documentDao.insert(doc)
    }
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    fun getDocumentById(id: Long): Flow<DocumentEntity?> = documentDao.getById(id)
    suspend fun updateDocument(doc: DocumentEntity) = documentDao.update(doc)
    suspend fun deleteDocument(doc: DocumentEntity) { fileManager.deleteFile(doc.filePath); documentDao.delete(doc) }
    suspend fun createScanSession(name: String): Long = scanSessionDao.insert(ScanSessionEntity(name = name))
    fun getAllSessions(): Flow<List<ScanSessionEntity>> = scanSessionDao.getAll()
}

