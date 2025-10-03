package com.jascanner.presentation.auth

sealed class AuthState {
    object Idle : AuthState()
    object Authenticating : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}