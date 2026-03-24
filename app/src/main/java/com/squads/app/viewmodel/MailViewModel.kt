package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.data.MailMessage
import com.squads.app.data.TeamsApiClient
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
        private val api: TeamsApiClient,
        private val mailRepository: MailRepository,
    ) : ViewModel() {
        val messages: StateFlow<List<MailMessage>> =
            mailRepository
                .observeMail()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        private val _selectedMail = MutableStateFlow<MailMessage?>(null)
        val selectedMail: StateFlow<MailMessage?> = _selectedMail

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private var lastLoadTime = 0L

        init {
            refreshMail()
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
            viewModelScope.launch {
                try {
                    _selectedMail.value = api.getMailDetail(mail.id)
                } catch (_: Exception) {
                }
            }
        }

        fun clearSelection() {
            _selectedMail.value = null
        }
    }
