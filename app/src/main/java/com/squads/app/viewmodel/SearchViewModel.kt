package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.data.SearchResult
import com.squads.app.data.SearchResultType
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: TeamsApiClient,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results

    private var searchJob: Job? = null

    /**
     * Search across chats and mail in parallel with debounce.
     */
    fun search(query: String) {
        _query.value = query
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce

            val q = query.lowercase()

            // Search chats and mail in parallel
            val chatsDeferred = async {
                try {
                    val (chats, _) = api.getUserDetails()
                    chats.filter {
                        it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q)
                    }.map {
                        SearchResult(
                            type = SearchResultType.CHAT,
                            title = it.title,
                            subtitle = "Chat",
                            preview = it.lastMessage,
                            id = it.id,
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val mailDeferred = async {
                try {
                    api.getMail(50).filter {
                        it.subject.lowercase().contains(q) || it.bodyPreview.lowercase().contains(q)
                    }.map {
                        SearchResult(
                            type = SearchResultType.MAIL,
                            title = it.subject,
                            subtitle = it.fromName,
                            preview = it.bodyPreview,
                            id = it.id,
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            _results.value = chatsDeferred.await() + mailDeferred.await()
        }
    }
}
