package com.jascanner.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jascanner.presentation.screens.camera.CameraScreen
import com.jascanner.presentation.screens.documents.DocumentDetailScreen
import com.jascanner.presentation.screens.documents.DocumentListScreen
import com.jascanner.presentation.screens.documents.DocumentListViewModel
import com.jascanner.presentation.screens.editor.EditorScreen
import com.jascanner.presentation.screens.thz.TerahertzScanScreen

@Composable
fun JAScannerNavigation(navController: NavHostController, documentListViewModel: DocumentListViewModel) {
    NavHost(navController, startDestination = JAScannerDestinations.DOCUMENTS_ROUTE) {
        composable(JAScannerDestinations.DOCUMENTS_ROUTE) {
            DocumentListScreen(
                onNavigateToCamera = { navController.navigate(JAScannerDestinations.CAMERA_ROUTE) },
                onNavigateToThz = { navController.navigate(JAScannerDestinations.THZ_ROUTE) },
                onNavigateToSettings = { },
                onDocumentSelected = { id -> navController.navigate(JAScannerDestinations.documentDetailRoute(id)) },
                viewModel = documentListViewModel
            )
        }
        composable(JAScannerDestinations.CAMERA_ROUTE) {
            CameraScreen(
                onNavigateBack = { navController.popBackStack() },
                onDocumentCaptured = { navController.popBackStack() }
            )
        }
        composable(JAScannerDestinations.THZ_ROUTE) {
            TerahertzScanScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(JAScannerDestinations.DOCUMENT_DETAIL_ROUTE) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("docId")?.toLongOrNull() ?: 0L
            DocumentDetailScreen(
                docId = id,
                onBack = { navController.popBackStack() },
                onNavigateToEditor = { docId -> navController.navigate(JAScannerDestinations.editorRoute(docId)) }
            )
        }
        composable(JAScannerDestinations.EDITOR_ROUTE) {
            EditorScreen(
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel()
            )
        }
    }
}