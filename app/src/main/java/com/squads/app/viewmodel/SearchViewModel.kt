package com.squads.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.data.SearchResult
import com.squads.app.data.SearchResultType
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
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

    /**
     * Search across chats and mail using the real APIs.
     * Fetches current data and filters locally.
     */
    fun search(query: String) {
        _query.value = query
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }

        viewModelScope.launch {
            val results = mutableListOf<SearchResult>()
            val q = query.lowercase()

            // Search chats
            try {
                val (chats, _) = api.getUserDetails()
                chats.filter {
                    it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q)
                }.forEach {
                    results.add(SearchResult(
                        type = SearchResultType.CHAT,
                        title = it.title,
                        subtitle = "Chat",
                        preview = it.lastMessage,
                        id = it.id,
                    ))
                }
            } catch (_: Exception) {}

            // Search mail
            try {
                val mail = api.getMail(50)
                mail.filter {
                    it.subject.lowercase().contains(q) || it.bodyPreview.lowercase().contains(q)
                }.forEach {
                    results.add(SearchResult(
                        type = SearchResultType.MAIL,
                        title = it.subject,
                        subtitle = it.fromName,
                        preview = it.bodyPreview,
                        id = it.id,
                    ))
                }
            } catch (_: Exception) {}

            _results.value = results
        }
    }
}
