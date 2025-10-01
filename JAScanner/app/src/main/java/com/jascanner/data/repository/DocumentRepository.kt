package com.jascanner.data.repository

import com.jascanner.domain.model.EditableDocument
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor() {
    suspend fun loadEditableDocument(documentId: String): EditableDocument? {
        // TODO: Implement actual data fetching
        return null
    }

    suspend fun saveEditableDocument(document: EditableDocument) {
        // TODO: Implement actual data saving
    }
}