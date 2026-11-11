package com.squads.app.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.squads.app.auth.AuthManager
import com.squads.app.data.NetworkMonitor
import com.squads.app.data.TeamsApiClient
import com.squads.app.data.db.SquadsDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val apiClient: TeamsApiClient,
        private val imageLoader: ImageLoader,
        private val database: SquadsDatabase,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        val isAuthenticated = authManager.isAuthenticated
        val userName = authManager.userName
        val deviceCodeState = authManager.deviceCodeState
        val isOnline = networkMonitor.isOnline
        val myUserId = apiClient.myUserId

        private val _isLoggingOut = MutableStateFlow(false)
        val isLoggingOut: StateFlow<Boolean> = _isLoggingOut

        val isDemoMode: Boolean
            get() = authManager.isDemoMode

        fun requestCode() {
            viewModelScope.launch { authManager.requestDeviceCodeLogin() }
        }

        fun openBrowserAndPoll(activity: Activity) {
            viewModelScope.launch {
                apiClient.activate()
                authManager.openBrowserAndPoll(activity)
            }
        }

        fun mockLogin() {
            apiClient.activate()
            authManager.mockLogin()
        }

        init {
            // A session also ends without logout(), when the refresh token expires or is revoked.
            // Wiping here also covers a process killed before that wipe finished.
            viewModelScope.launch {
                isAuthenticated.collectLatest { authenticated ->
                    if (!authenticated && !_isLoggingOut.value) clearStoredData()
                }
            }
        }

        fun logout() {
            viewModelScope.launch {
                _isLoggingOut.value = true
                try {
                    // End the session first: polling stops, so it cannot write chats after the wipe.
                    apiClient.clearAll()
                    authManager.logout()
                    clearStoredData()
                } finally {
                    _isLoggingOut.value = false
                }
            }
        }

        private suspend fun clearStoredData() {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val dbJob = async { database.clearAllTables() }
                    val cacheJob = async { imageLoader.diskCache?.clear() }
                    dbJob.await()
                    cacheJob.await()
                }
            }
            imageLoader.memoryCache?.clear()
        }

        fun resetState() = authManager.resetState()
    }
