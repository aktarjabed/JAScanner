package com.jascanner.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jascanner.presentation.batch.BatchOperationsScreen
import com.jascanner.presentation.editor.comparison.BeforeAfterComparisonView
import com.jascanner.presentation.onboarding.OnboardingScreen
import com.jascanner.presentation.pdf.PdfOperationsScreen
import com.jascanner.presentation.presets.PresetsScreen
// import com.jascanner.presentation.editor.EditorScreen // TODO: Uncomment when EditorScreen is created

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object Editor : Screen("editor/{documentId}") {
        fun createRoute(documentId: String) = "editor/$documentId"
    }
    object Presets : Screen("presets")
    object BatchOperations : Screen("batch_operations")
    object PdfOperations : Screen("pdf_operations")
    object Comparison : Screen("comparison/{documentId}") {
        fun createRoute(documentId: String) = "comparison/$documentId"
    }
}

@Composable
fun CompleteNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Onboarding
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Home screen (placeholder)
        composable(Screen.Home.route) {
            // Home screen implementation
        }

        /*
        // Editor
        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")
                ?: return@composable

            EditorScreen(
                documentId = documentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        */

        // Presets
        composable(Screen.Presets.route) {
            PresetsScreen(
                onPresetSelected = { preset ->
                    // Handle preset selection
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Batch Operations
        composable(Screen.BatchOperations.route) {
            BatchOperationsScreen(
                documents = emptyList(), // Pass from ViewModel
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // PDF Operations
        composable(Screen.PdfOperations.route) {
            PdfOperationsScreen()
        }

        // Comparison
        composable(
            route = Screen.Comparison.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")
                ?: return@composable

            BeforeAfterComparisonView(documentId = documentId)
        }
    }
}