package com.squads.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.MailApi
import com.squads.app.data.MailMessage
import com.squads.app.data.repository.MailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MailViewModel
    @Inject
    constructor(
        private val mailApi: MailApi,
        private val mailRepository: MailRepository,
        private val authManager: AuthManager,
    ) : ViewModel() {
        val messages: StateFlow<List<MailMessage>> =
            mailRepository
                .observeMail()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        private val _selectedMail = MutableStateFlow<MailMessage?>(null)
        val selectedMail: StateFlow<MailMessage?> = _selectedMail

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _isDetailLoading = MutableStateFlow(false)
        val isDetailLoading: StateFlow<Boolean> = _isDetailLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private var lastLoadTime = 0L

        init {
            refreshMail()
            viewModelScope.launch {
                authManager.onSessionStart().collect {
                    lastLoadTime = 0L
                    _selectedMail.value = null
                    refreshMail(forceRefresh = true)
                }
            }
        }

        fun refreshMail(forceRefresh: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && now - lastLoadTime < 60_000 && lastLoadTime > 0) return

            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    mailRepository.refreshMail()
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
                    _selectedMail.value = mailApi.getMailDetail(mail.id)
                } catch (e: Exception) {
                    Log.w("MailViewModel", "Failed to load mail detail", e)
                } finally {
                    _isDetailLoading.value = false
                }
            }
        }

        fun clearSelection() {
            _selectedMail.value = null
        }

        suspend fun getTokenForUrl(url: String): String? = mailApi.getTokenForUrl(url)
    }
