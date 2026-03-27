package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.CalendarEvent
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val api: TeamsApiClient,
        private val authManager: AuthManager,
    ) : ViewModel() {
        private val _showWeek = MutableStateFlow(false)
        val showWeek: StateFlow<Boolean> = _showWeek

        private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
        val events: StateFlow<List<CalendarEvent>> = _events

        private val _selectedEvent = MutableStateFlow<CalendarEvent?>(null)
        val selectedEvent: StateFlow<CalendarEvent?> = _selectedEvent

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private var lastLoadTime = 0L
        private var lastLoadedForWeek: Boolean? = null

        init {
            loadEvents()
            viewModelScope.launch {
                authManager.onSessionStart().collect {
                    lastLoadTime = 0L
                    lastLoadedForWeek = null
                    _events.value = emptyList()
                    loadEvents(forceRefresh = true)
                }
            }
        }

        fun loadEvents(forceRefresh: Boolean = false) {
            val now = System.currentTimeMillis()
            val showWeekNow = _showWeek.value
            if (!forceRefresh &&
                _events.value.isNotEmpty() &&
                lastLoadedForWeek == showWeekNow &&
                now - lastLoadTime < 60_000
            ) {
                return
            }

            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    _events.value = api.getEvents(if (showWeekNow) 7 else 1)
                    lastLoadTime = System.currentTimeMillis()
                    lastLoadedForWeek = showWeekNow
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun toggleWeekView() {
            _showWeek.value = !_showWeek.value
            loadEvents()
        }

        fun selectEvent(event: CalendarEvent) {
            _selectedEvent.value = event
        }

        fun dismissEvent() {
            _selectedEvent.value = null
        }
    }
