package com.squads.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.MailApi
import com.squads.app.data.MailFolder
import com.squads.app.data.MailMessage
import com.squads.app.data.repository.MailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MailViewModel
    @Inject
    constructor(
        private val mailApi: MailApi,
        private val mailRepository: MailRepository,
        private val authManager: AuthManager,
        val okHttpClient: OkHttpClient,
    ) : ViewModel() {
        private val _folders = MutableStateFlow<List<MailFolder>>(emptyList())
        val folders: StateFlow<List<MailFolder>> = _folders

        private val _currentFolderId = MutableStateFlow<String?>(null)
        val currentFolderId: StateFlow<String?> = _currentFolderId

        val messages: StateFlow<List<MailMessage>> =
            _currentFolderId
                .flatMapLatest { folderId ->
                    if (folderId != null) {
                        mailRepository.observeMailByFolder(folderId)
                    } else {
                        flowOf(emptyList())
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        private val _selectedMail = MutableStateFlow<MailMessage?>(null)
        val selectedMail: StateFlow<MailMessage?> = _selectedMail

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _isDetailLoading = MutableStateFlow(false)
        val isDetailLoading: StateFlow<Boolean> = _isDetailLoading

        private val _authToken = MutableStateFlow<String?>(null)
        val authToken: StateFlow<String?> = _authToken

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private var lastLoadTime = 0L

        init {
            loadFolders()
            viewModelScope.launch {
                authManager.onSessionStart().collect {
                    lastLoadTime = 0L
                    _selectedMail.value = null
                    _currentFolderId.value = null
                    _folders.value = emptyList()
                    loadFolders()
                }
            }
        }

        private fun loadFolders() {
            viewModelScope.launch {
                try {
                    val foldersDeferred = async { mailRepository.getMailFolders() }
                    val inboxIdDeferred = async { mailRepository.getInboxFolderId() }
                    val folders = foldersDeferred.await()
                    val inboxId = inboxIdDeferred.await()
                    _folders.value = folders
                    if (_currentFolderId.value == null && folders.isNotEmpty()) {
                        _currentFolderId.value = inboxId ?: folders.first().id
                    }
                    refreshMail(forceRefresh = true)
                } catch (e: Exception) {
                    Log.w("MailViewModel", "Failed to load folders", e)
                }
            }
        }

        fun switchFolder(folderId: String) {
            if (_currentFolderId.value == folderId) return
            _currentFolderId.value = folderId
            lastLoadTime = 0L
            refreshMail(forceRefresh = true)
        }

        fun refreshMail(forceRefresh: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && now - lastLoadTime < 60_000 && lastLoadTime > 0) return

            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    mailRepository.refreshMail(folderId = _currentFolderId.value)
                    lastLoadTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun selectMail(mail: MailMessage) {
            _selectedMail.value = mail
            _isDetailLoading.value = true
            viewModelScope.launch {
                try {
                    val detailDeferred = async { mailApi.getMailDetail(mail.id) }
                    val tokenDeferred =
                        async {
                            try {
                                mailApi.getGraphToken()
                            } catch (_: Exception) {
                                null
                            }
                        }
                    _selectedMail.value = detailDeferred.await()
                    _authToken.value = tokenDeferred.await()
                } catch (e: Exception) {
                    Log.w("MailViewModel", "Failed to load mail detail", e)
                } finally {
                    _isDetailLoading.value = false
                }
            }
        }

        fun clearSelection() {
            _selectedMail.value = null
            _authToken.value = null
        }
    }
