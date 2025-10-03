package com.jascanner.presentation.navigation

object JAScannerDestinations {
    const val DOCUMENTS_ROUTE = "documentList"
    const val SCANNER_ROUTE = "scanner?documentId={documentId}"
    const val DOCUMENT_DETAIL_ROUTE = "documentDetail/{docId}"
    const val EDITOR_ROUTE = "editor/{documentId}"
    const val THZ_ROUTE = "thz"

    fun scannerRoute(documentId: Long? = null): String {
        return if (documentId != null) {
            "scanner?documentId=$documentId"
        } else {
            "scanner"
        }
    }

    fun documentDetailRoute(docId: Long): String {
        return "documentDetail/$docId"
    }

    fun editorRoute(docId: Long): String {
        return "editor/$docId"
    }
}