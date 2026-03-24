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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val _messageImages = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val messageImages: StateFlow<Map<String, ImageBitmap>> = _messageImages

    private var myUserId: String? = null
    private var chatPollingJob: Job? = null
    private var messagePollingJob: Job? = null

    /** Current user's profile photo, derived from the shared photos map. */
    val myPhoto: StateFlow<ImageBitmap?> = _photos
        .map { myUserId?.let { id -> it[id] } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    init {
        loadChats()
        startChatPolling()
        loadMyPhoto()
    }

    private fun loadMyPhoto() {
        viewModelScope.launch {
            try {
                val me = api.getMe()
                myUserId = me.id
                loadBitmaps(listOf(me.id), _photos) { api.getProfilePhoto(it) }
            } catch (_: Exception) { }
        }
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
                } catch (_: Exception) { }
            }
        }
    }

    fun selectChat(chat: ChatConversation) {
        _selectedChat.value = chat
        _messagesLoading.value = true
        viewModelScope.launch {
            try {
                val msgs = api.getChatMessages(chat.id)
                _messages.value = msgs
                loadPhotosForIds(msgs.map { it.senderObjectId })
                loadMessageImages(msgs)
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
                        loadPhotosForIds(fresh.map { it.senderObjectId })
                        loadMessageImages(fresh)
                    }
                } catch (_: Exception) { }
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
            contentHtml = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>"),
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

    private fun loadMessageImages(messages: List<ChatMessage>) {
        loadBitmaps(messages.flatMap { it.imageUrls }, _messageImages) { api.fetchImage(it) }
    }

    private fun loadPhotosForIds(userIds: List<String>) {
        loadBitmaps(userIds.filter { it.isNotBlank() }, _photos) { api.getProfilePhoto(it) }
    }

    private fun loadBitmaps(
        keys: List<String>,
        target: MutableStateFlow<Map<String, ImageBitmap>>,
        fetcher: suspend (String) -> ByteArray?,
    ) {
        val toLoad = keys.distinct().filter { it !in target.value }.take(30)
        if (toLoad.isEmpty()) return

        viewModelScope.launch {
            val loaded = toLoad.map { key ->
                async {
                    fetcher(key)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?.let { key to it.asImageBitmap() }
                    }
                }
            }.awaitAll().filterNotNull().toMap()

            if (loaded.isNotEmpty()) {
                target.update { it + loaded }
            }
        }
    }
}
