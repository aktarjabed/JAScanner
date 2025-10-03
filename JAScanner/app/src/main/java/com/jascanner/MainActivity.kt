package com.jascanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.jascanner.presentation.navigation.JAScannerNavigation
import com.jascanner.presentation.screens.auth.AuthenticationScreen
import com.jascanner.presentation.screens.documents.DocumentListViewModel
import com.jascanner.security.RobustSecurityManager
import com.jascanner.ui.theme.JAScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Authenticating : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val documentListViewModel: DocumentListViewModel by viewModels()

    @Inject
    lateinit var securityManager: RobustSecurityManager

    private var authState by mutableStateOf<AuthState>(AuthState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // check biometric availability once
        lifecycleScope.launch {
            val capability = securityManager.checkBiometricAvailability()
            authState = if (capability == RobustSecurityManager.BiometricCapability.AVAILABLE) {
                AuthState.Idle
            } else {
                AuthState.Error("Biometric authentication is not available on this device.")
            }
        }

        setContent {
            JAScannerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    when (val state = authState) {
                        is AuthState.Idle -> {
                            AuthenticationScreen(
                                onAuthenticate = { authenticate() },
                                onRetry = { authenticate() },
                                errorMessage = null
                            )
                        }
                        is AuthState.Authenticating -> {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator()
                            }
                        }
                        is AuthState.Authenticated -> {
                            // use the navController declared above
                            JAScannerNavigation(navController, documentListViewModel)
                        }
                        is AuthState.Error -> {
                            AuthenticationScreen(
                                onAuthenticate = { authenticate() },
                                onRetry = { authenticate() },
                                errorMessage = state.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun authenticate() {
        lifecycleScope.launch {
            authState = AuthState.Authenticating
            val result = securityManager.authenticateWithBiometrics(
                activity = this@MainActivity as FragmentActivity,
                title = "Authenticate to access JAScanner",
                subtitle = "Use your biometric credentials",
                description = "Your documents are protected with the highest level of security."
            )
            authState = when (result) {
                is RobustSecurityManager.AuthenticationResult.Success -> AuthState.Authenticated
                is RobustSecurityManager.AuthenticationResult.Error -> AuthState.Error(result.message)
                is RobustSecurityManager.AuthenticationResult.Cancelled -> AuthState.Idle
                is RobustSecurityManager.AuthenticationResult.Failed -> AuthState.Error("Authentication failed. Please try again.")
            }
        }
    }
}