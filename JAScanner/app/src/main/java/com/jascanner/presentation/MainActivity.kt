package com.jascanner.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.jascanner.presentation.navigation.CompleteNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.all { it.value }

        if (!permissionsGranted) {
            // Show rationale dialog
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            JAScannerApp(
                permissionsGranted = permissionsGranted,
                onRequestPermissions = { checkPermissions() }
            )
        }
    }

    private fun checkPermissions() {
        val allGranted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun showPermissionRationale() {
        // Show dialog explaining why permissions are needed
    }
}

@Composable
fun JAScannerApp(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    if (permissionsGranted) {
        val navController = rememberNavController()
        CompleteNavigation(navController = navController)
    } else {
        PermissionScreen(onRequestPermissions = onRequestPermissions)
    }
}

@Composable
fun PermissionScreen(onRequestPermissions: () -> Unit) {
    // Permission request UI
}