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

        fun logout() {
            viewModelScope.launch {
                _isLoggingOut.value = true
                try {
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            val dbJob = async { database.clearAllTables() }
                            val cacheJob = async { imageLoader.diskCache?.clear() }
                            dbJob.await()
                            cacheJob.await()
                        }
                    }
                    apiClient.clearAll()
                    imageLoader.memoryCache?.clear()
                    authManager.logout()
                } finally {
                    _isLoggingOut.value = false
                }
            }
        }

        fun resetState() = authManager.resetState()
    }
