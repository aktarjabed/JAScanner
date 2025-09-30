package com.jascanner.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jascanner.compression.presentation.AdvancedCompressionScreen
import com.jascanner.presentation.screens.camera.CameraScreen
import com.jascanner.presentation.screens.documents.DocumentDetailScreen
import com.jascanner.presentation.screens.documents.DocumentListScreen
import com.jascanner.presentation.screens.documents.DocumentListViewModel
import com.jascanner.presentation.screens.thz.TerahertzScanScreen

@Composable
fun JAScannerNavigation(navController: NavHostController, documentListViewModel: DocumentListViewModel) {
    NavHost(navController, startDestination = JAScannerDestinations.DOCUMENTS_ROUTE) {
        composable(JAScannerDestinations.DOCUMENTS_ROUTE) {
            DocumentListScreen(
                onNavigateToCamera = { navController.navigate(JAScannerDestinations.CAMERA_ROUTE) },
                onNavigateToThz = { navController.navigate(JAScannerDestinations.THZ_ROUTE) },
                onNavigateToSettings = { },
                onDocumentSelected = { id -> navController.navigate(JAScannerDestinations.editorRoute(id)) },
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
        composable(JAScannerDestinations.EDITOR_ROUTE) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("docId")?.toLongOrNull() ?: 0L
            DocumentDetailScreen(
                docId = id,
                onBack = { navController.popBackStack() },
                onNavigateToCompressionSettings = { docId, pageCount, originalSize ->
                    navController.navigate(JAScannerDestinations.compressionSettingsRoute(docId, pageCount, originalSize))
                }
            )
        }
        composable(
            route = JAScannerDestinations.COMPRESSION_SETTINGS_ROUTE,
            arguments = listOf(
                navArgument("docId") { type = NavType.LongType },
                navArgument("pageCount") { type = NavType.IntType },
                navArgument("originalSize") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val pageCount = backStackEntry.arguments?.getInt("pageCount") ?: 0
            val originalSize = backStackEntry.arguments?.getLong("originalSize") ?: 0L

            AdvancedCompressionScreen(
                docId = docId,
                pageCount = pageCount,
                estimatedOriginalSize = originalSize,
                viewModel = hiltViewModel(),
                onCancel = { navController.popBackStack() }
            )
        }
    }
}