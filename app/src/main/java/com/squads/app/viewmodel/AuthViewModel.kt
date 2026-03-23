package com.squads.app.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {
    val isAuthenticated = authManager.isAuthenticated
    val userName = authManager.userName
    val deviceCodeState = authManager.deviceCodeState

    val isDemoMode: Boolean
        get() = authManager.getRefreshToken() == "mock_refresh_token"

    fun requestCode() {
        viewModelScope.launch { authManager.requestDeviceCodeLogin() }
    }

    fun openBrowserAndPoll(activity: Activity) {
        viewModelScope.launch { authManager.openBrowserAndPoll(activity) }
    }

    fun mockLogin() = authManager.mockLogin()
    fun logout() = authManager.logout()
    fun resetState() = authManager.resetState()
}
