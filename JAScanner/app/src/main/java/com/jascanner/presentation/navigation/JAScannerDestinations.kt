package com.jascanner.presentation.navigation

object JAScannerDestinations {
    const val DOCUMENTS_ROUTE = "documents"
    const val CAMERA_ROUTE = "camera"
    const val THZ_ROUTE = "thz"
    const val SETTINGS_ROUTE = "settings"
    const val DOCUMENT_DETAIL_ROUTE = "document/{docId}"
    fun documentDetailRoute(id: Long) = "document/$id"
    const val EDITOR_ROUTE = "editor/{docId}"
    fun editorRoute(id: Long) = "editor/$id"
}

