package com.jascanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.jascanner.device.DeviceCapabilitiesDetector
import com.jascanner.presentation.navigation.JAScannerNavigation
import com.jascanner.presentation.screens.documents.DocumentListViewModel
import com.jascanner.security.SecurityManager
import com.jascanner.ui.theme.JAScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val documentListViewModel: DocumentListViewModel by viewModels()
    
    @Inject
    lateinit var deviceCapabilities: DeviceCapabilitiesDetector
    
    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log device capabilities
        deviceCapabilities.logCapabilities()
        
        // Setup security if not already done
        if (!securityManager.isSecuritySetup()) {
            securityManager.setupSecurity()
        }
        
        setContent {
            JAScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    JAScannerNavigation(navController, documentListViewModel)
                }
            }
        }
        
        Timber.i("MainActivity created")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity paused")
    }
}