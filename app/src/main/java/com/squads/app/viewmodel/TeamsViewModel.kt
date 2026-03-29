package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.ChannelMessage
import com.squads.app.data.Team
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeamsViewModel
    @Inject
    constructor(
        private val api: TeamsApiClient,
        private val authManager: AuthManager,
    ) : ViewModel() {
        private val _teams = MutableStateFlow<List<Team>>(emptyList())
        val teams: StateFlow<List<Team>> = _teams

        private val _selectedTeam = MutableStateFlow<Team?>(null)
        val selectedTeam: StateFlow<Team?> = _selectedTeam

        private val _channelMessages = MutableStateFlow<List<ChannelMessage>>(emptyList())
        val channelMessages: StateFlow<List<ChannelMessage>> = _channelMessages

        private val _selectedChannelName = MutableStateFlow<String?>(null)
        val selectedChannelName: StateFlow<String?> = _selectedChannelName

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private var lastLoadTime = 0L
        private var channelJob: Job? = null

        init {
            loadTeams()
            viewModelScope.launch {
                authManager.onSessionStart().collect {
                    lastLoadTime = 0L
                    _teams.value = emptyList()
                    _selectedTeam.value = null
                    _channelMessages.value = emptyList()
                    _selectedChannelName.value = null
                    loadTeams(forceRefresh = true)
                }
            }
        }

        fun loadTeams(forceRefresh: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && _teams.value.isNotEmpty() && now - lastLoadTime < 120_000) return

            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    val (_, teams) = api.getUserDetails()
                    _teams.value = teams
                    lastLoadTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun selectTeam(team: Team) {
            _selectedTeam.value = team
            _selectedChannelName.value = null
            _channelMessages.value = emptyList()
        }

        fun selectChannel(
            teamId: String,
            channelId: String,
            channelName: String,
        ) {
            _selectedChannelName.value = channelName
            val groupId = _selectedTeam.value?.groupId ?: ""
            channelJob?.cancel()
            channelJob =
                viewModelScope.launch {
                    try {
                        val messages = api.getChannelMessages(teamId, channelId)
                        _channelMessages.value = messages
                        _channelMessages.value = api.resolveAndApplyBotIcons(messages, groupId)
                    } catch (e: Exception) {
                        _error.value = "Failed to load messages: ${e.message}"
                    }
                }
        }

        fun goBack() {
            if (_selectedChannelName.value != null) {
                _selectedChannelName.value = null
                _channelMessages.value = emptyList()
            } else {
                _selectedTeam.value = null
            }
        }
    }
