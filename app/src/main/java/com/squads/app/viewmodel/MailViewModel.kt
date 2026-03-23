package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.data.MailMessage
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MailViewModel @Inject constructor(
    private val api: TeamsApiClient,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MailMessage>>(emptyList())
    val messages: StateFlow<List<MailMessage>> = _messages

    private val _selectedMail = MutableStateFlow<MailMessage?>(null)
    val selectedMail: StateFlow<MailMessage?> = _selectedMail

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadMail()
    }

    fun loadMail() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _messages.value = api.getMail()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectMail(mail: MailMessage) {
        viewModelScope.launch {
            try {
                _selectedMail.value = api.getMailDetail(mail.id)
            } catch (e: Exception) {
                _error.value = "Failed to load email: ${e.message}"
                _selectedMail.value = mail
            }
        }
    }

    fun clearSelection() {
        _selectedMail.value = null
    }
}
