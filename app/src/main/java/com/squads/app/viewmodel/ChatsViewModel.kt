package com.squads.app.viewmodel

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.data.ChatConversation
import com.squads.app.data.ChatMessage
import com.squads.app.data.TeamsApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val api: TeamsApiClient,
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatConversation>>(emptyList())
    val chats: StateFlow<List<ChatConversation>> = _chats

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _selectedChat = MutableStateFlow<ChatConversation?>(null)
    val selectedChat: StateFlow<ChatConversation?> = _selectedChat

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _messagesLoading = MutableStateFlow(false)
    val messagesLoading: StateFlow<Boolean> = _messagesLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _photos = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val photos: StateFlow<Map<String, ImageBitmap>> = _photos

    private var chatPollingJob: Job? = null
    private var messagePollingJob: Job? = null

    init {
        loadChats()
        startChatPolling()
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val (chats, _) = api.getUserDetails()
                _chats.value = chats
                loadPhotosForIds(chats.mapNotNull { if (it.isOneOnOne) it.memberId else null })
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startChatPolling() {
        chatPollingJob?.cancel()
        chatPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    val (chats, _) = api.getUserDetails()
                    if (chats != _chats.value) {
                        _chats.value = chats
                        loadPhotosForIds(chats.mapNotNull { if (it.isOneOnOne) it.memberId else null })
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun selectChat(chat: ChatConversation) {
        _selectedChat.value = chat
        _messagesLoading.value = true
        viewModelScope.launch {
            try {
                _messages.value = api.getChatMessages(chat.id)
                loadPhotosForIds(_messages.value.filter { !it.isFromMe }.map { it.senderObjectId })
            } catch (e: Exception) {
                _error.value = "Failed to load messages: ${e.message}"
            } finally {
                _messagesLoading.value = false
            }
        }
        startMessagePolling(chat.id)
    }

    private fun startMessagePolling(chatId: String) {
        messagePollingJob?.cancel()
        messagePollingJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                try {
                    val fresh = api.getChatMessages(chatId)
                    if (fresh.size != _messages.value.size ||
                        fresh.lastOrNull()?.id != _messages.value.lastOrNull()?.id
                    ) {
                        _messages.value = fresh
                        loadPhotosForIds(fresh.filter { !it.isFromMe }.map { it.senderObjectId })
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stopMessagePolling() {
        messagePollingJob?.cancel()
        messagePollingJob = null
    }

    fun sendMessage(chatId: String, content: String) {
        if (content.isBlank()) return
        val newMsg = ChatMessage(
            id = "local-${System.currentTimeMillis()}",
            content = content,
            senderName = "You",
            senderId = "me",
            timestamp = java.time.LocalDateTime.now(),
            isFromMe = true,
        )
        _messages.value = _messages.value + newMsg

        viewModelScope.launch {
            try {
                api.sendMessage(chatId, content)
            } catch (e: Exception) {
                _error.value = "Failed to send: ${e.message}"
            }
        }
    }

    private fun loadPhotosForIds(userIds: List<String>) {
        val idsToLoad = userIds
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it !in _photos.value }
            .take(20)

        if (idsToLoad.isEmpty()) return

        viewModelScope.launch {
            val loaded = idsToLoad.map { userId ->
                async {
                    val bytes = api.getProfilePhoto(userId)
                    if (bytes != null) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) userId to bmp.asImageBitmap() else null
                    } else null
                }
            }.awaitAll().filterNotNull().toMap()

            if (loaded.isNotEmpty()) {
                _photos.update { it + loaded }
            }
        }
    }
}
