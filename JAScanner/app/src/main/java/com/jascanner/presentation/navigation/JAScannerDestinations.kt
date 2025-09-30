package com.jascanner.presentation.navigation

object JAScannerDestinations {
    const val DOCUMENTS_ROUTE = "documents"
    const val CAMERA_ROUTE = "camera"
    const val THZ_ROUTE = "thz"
    const val SETTINGS_ROUTE = "settings"
    const val EDITOR_ROUTE = "editor/{docId}"
    fun editorRoute(id: Long) = "editor/$id"
    const val COMPRESSION_SETTINGS_ROUTE = "compression/{docId}/{pageCount}/{originalSize}"
    fun compressionSettingsRoute(id: Long, pageCount: Int, originalSize: Long) = "compression/$id/$pageCount/$originalSize"
}

