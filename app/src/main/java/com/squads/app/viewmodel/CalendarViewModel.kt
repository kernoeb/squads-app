package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.CalendarApi
import com.squads.app.data.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val calendarApi: CalendarApi,
        private val authManager: AuthManager,
    ) : ViewModel() {
        private val _showWeek = MutableStateFlow(false)
        val showWeek: StateFlow<Boolean> = _showWeek

        private val _weekOffset = MutableStateFlow(0)
        val weekOffset: StateFlow<Int> = _weekOffset

        private val _weekStartDate = MutableStateFlow(currentWeekStart(0))
        val weekStartDate: StateFlow<LocalDate> = _weekStartDate

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
        private var lastLoadedWeekOffset: Int? = null

        init {
            loadEvents()
            viewModelScope.launch {
                authManager.onSessionStart().collect {
                    lastLoadTime = 0L
                    lastLoadedForWeek = null
                    lastLoadedWeekOffset = null
                    _weekOffset.value = 0
                    _events.value = emptyList()
                    loadEvents(forceRefresh = true)
                }
            }
        }

        fun onAppResumed() {
            val now = System.currentTimeMillis()
            if (now - lastLoadTime < 5_000) return
            loadEvents(forceRefresh = true)
        }

        fun loadEvents(forceRefresh: Boolean = false) {
            val now = System.currentTimeMillis()
            val showWeekNow = _showWeek.value
            val offset = _weekOffset.value
            if (!forceRefresh &&
                _events.value.isNotEmpty() &&
                lastLoadedForWeek == showWeekNow &&
                lastLoadedWeekOffset == offset &&
                now - lastLoadTime < 60_000
            ) {
                return
            }

            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    val startDate =
                        if (showWeekNow) {
                            currentWeekStart(offset)
                        } else {
                            LocalDate.now()
                        }
                    _weekStartDate.value = currentWeekStart(offset)
                    _events.value = calendarApi.getEvents(if (showWeekNow) 7 else 1, startDate)
                    lastLoadTime = System.currentTimeMillis()
                    lastLoadedForWeek = showWeekNow
                    lastLoadedWeekOffset = offset
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun toggleWeekView() {
            _showWeek.value = !_showWeek.value
            _weekOffset.value = 0
            _events.value = emptyList()
            loadEvents()
        }

        fun nextWeek() {
            _weekOffset.value++
            _events.value = emptyList()
            loadEvents()
        }

        fun previousWeek() {
            _weekOffset.value--
            _events.value = emptyList()
            loadEvents()
        }

        fun selectEvent(event: CalendarEvent) {
            _selectedEvent.value = event
        }

        fun dismissEvent() {
            _selectedEvent.value = null
        }

        companion object {
            private fun currentWeekStart(offset: Int): LocalDate = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
        }
    }
