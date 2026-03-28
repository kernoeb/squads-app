package com.squads.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squads.app.auth.AuthManager
import com.squads.app.data.ChatConversation
import com.squads.app.data.ChatMessage
import com.squads.app.data.EmojiManager
import com.squads.app.data.NetworkMonitor
import com.squads.app.data.TeamsApiClient
import com.squads.app.data.TrouterClient
import com.squads.app.data.escapeForTeamsHtml
import com.squads.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel
    @Inject
    constructor(
        private val api: TeamsApiClient,
        private val chatRepository: ChatRepository,
        private val networkMonitor: NetworkMonitor,
        private val trouterClient: TrouterClient,
        private val authManager: AuthManager,
        private val emojiManager: EmojiManager,
    ) : ViewModel() {
        private val _chats = MutableStateFlow<List<ChatConversation>>(emptyList())

        companion object {
            private const val TAG = "ChatsViewModel"
        }

        val chats: StateFlow<List<ChatConversation>> = _chats

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages

        private val _selectedChat = MutableStateFlow<ChatConversation?>(null)
        val selectedChat: StateFlow<ChatConversation?> = _selectedChat

        private val _presenceMap = MutableStateFlow<Map<String, String>>(emptyMap())
        val presenceMap: StateFlow<Map<String, String>> = _presenceMap

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _messagesLoading = MutableStateFlow(false)
        val messagesLoading: StateFlow<Boolean> = _messagesLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        @Volatile
        private var myDisplayName: String? = null
        private var chatPollingJob: Job? = null
        private var messagePollingJob: Job? = null
        private var chatRefreshJob: Job? = null
        private var messageRefreshJob: Job? = null
        private var presenceJob: Job? = null

        init {
            api.invalidateCache()
            viewModelScope.launch { emojiManager.init() }
            viewModelScope.launch { chatRepository.observeChats().collect { _chats.value = it } }
            refreshChats()
            startChatPolling()
            startPresencePolling()
            loadMyInfo()
            trouterClient.start()
            collectTrouterEvents()
            observeAuthState()
        }

        private fun observeAuthState() {
            viewModelScope.launch {
                authManager.isAuthenticated
                    .drop(1)
                    .collect { authenticated ->
                        if (authenticated) reinitialize() else teardown()
                    }
            }
        }

        private fun reinitialize() {
            cancelAllJobs()
            _chats.value = emptyList()
            _messages.value = emptyList()
            _selectedChat.value = null
            _error.value = null
            _presenceMap.value = emptyMap()
            myDisplayName = null
            api.invalidateCache()
            refreshChats()
            startChatPolling()
            startPresencePolling()
            loadMyInfo()
            trouterClient.start()
        }

        private fun teardown() {
            cancelAllJobs()
            trouterClient.stop()
            _chats.value = emptyList()
            _messages.value = emptyList()
            _selectedChat.value = null
            _presenceMap.value = emptyMap()
        }

        private fun cancelAllJobs() {
            chatPollingJob?.cancel()
            messagePollingJob?.cancel()
            chatRefreshJob?.cancel()
            messageRefreshJob?.cancel()
            presenceJob?.cancel()
        }

        private fun loadMyInfo() {
            viewModelScope.launch {
                try {
                    val me = api.getMe()
                    myDisplayName = me.displayName
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load user info", e)
                }
            }
        }

        private fun refreshChats() {
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    chatRepository.refreshChats()
                } catch (e: Exception) {
                    _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }

        private fun collectTrouterEvents() {
            viewModelScope.launch {
                trouterClient.events.collect { event ->
                    when (event) {
                        is TrouterClient.Event.NewMessage -> onTrouterMessage(event)
                        is TrouterClient.Event.PresenceChanged -> onPresenceChanged(event)
                        is TrouterClient.Event.ReadHorizonUpdate -> refreshChatsDebounced()
                        is TrouterClient.Event.Connected -> onTrouterConnected()
                        is TrouterClient.Event.Typing -> {}
                        is TrouterClient.Event.Disconnected -> {}
                    }
                }
            }
        }

        private fun onTrouterConnected() {
            viewModelScope.launch {
                _chats.first { it.isNotEmpty() }
                val memberIds = oneOnOneMemberIds()
                if (memberIds.isNotEmpty()) {
                    trouterClient.subscribePresence(memberIds)
                }
            }
        }

        private fun oneOnOneMemberIds(): List<String> =
            _chats.value
                .filter { it.isOneOnOne && it.memberId != null }
                .mapNotNull { it.memberId }
                .distinct()

        private fun onPresenceChanged(event: TrouterClient.Event.PresenceChanged) {
            val current = _presenceMap.value.toMutableMap()
            current[event.userId] = event.availability
            _presenceMap.value = current
        }

        private fun refreshChatsDebounced() {
            chatRefreshJob?.cancel()
            chatRefreshJob =
                viewModelScope.launch {
                    delay(300)
                    api.invalidateCache()
                    try {
                        chatRepository.refreshChats()
                    } catch (e: Exception) {
                        Log.w(TAG, "Debounced chat refresh failed", e)
                    }
                }
        }

        private fun onTrouterMessage(event: TrouterClient.Event.NewMessage) {
            refreshChatsDebounced()
            // Debounce message refresh for the selected chat
            if (event.chatId == _selectedChat.value?.id) {
                val chatId = event.chatId
                messageRefreshJob?.cancel()
                messageRefreshJob =
                    viewModelScope.launch {
                        delay(300)
                        if (chatId != _selectedChat.value?.id) return@launch
                        try {
                            val fresh = chatRepository.refreshMessages(chatId)
                            _messages.value = mergeWithOptimistic(fresh)
                        } catch (e: Exception) {
                            Log.w(TAG, "Trouter message refresh failed", e)
                        }
                    }
            }
        }

        private fun startChatPolling() {
            chatPollingJob?.cancel()
            chatPollingJob =
                viewModelScope.launch {
                    while (isActive) {
                        delay(if (trouterClient.isConnected.value) 60_000 else 15_000)
                        if (!networkMonitor.isOnline.value) continue
                        try {
                            chatRepository.refreshChats()
                        } catch (e: Exception) {
                            Log.w(TAG, "Chat polling refresh failed", e)
                        }
                    }
                }
        }

        fun selectChat(chat: ChatConversation) {
            _selectedChat.value = chat
            _messages.value = emptyList()
            _messagesLoading.value = true
            viewModelScope.launch {
                try {
                    val msgs = chatRepository.refreshMessages(chat.id)
                    _messages.value = msgs
                    if (chat.isUnread) {
                        chatRepository.markChatAsRead(chat.id, msgs.lastOrNull()?.id)
                    }
                } catch (e: Exception) {
                    _error.value = "Failed to load messages: ${e.message}"
                } finally {
                    _messagesLoading.value = false
                }
            }
            startMessagePolling(chat.id)
        }

        private fun mergeWithOptimistic(server: List<ChatMessage>): List<ChatMessage> {
            val pending = _messages.value.filter { it.id.startsWith("local-") }
            if (pending.isEmpty()) return server
            val matched = mutableSetOf<String>()
            val stillPending =
                pending.filter { local ->
                    val echo =
                        server.firstOrNull { s ->
                            s.isFromMe && s.content == local.content && s.id !in matched
                        }
                    if (echo != null) matched.add(echo.id)
                    echo == null
                }
            return server + stillPending
        }

        private fun startMessagePolling(chatId: String) {
            messagePollingJob?.cancel()
            messagePollingJob =
                viewModelScope.launch {
                    while (isActive) {
                        delay(if (trouterClient.isConnected.value) 30_000 else 10_000)
                        if (!networkMonitor.isOnline.value) continue
                        try {
                            val fresh = chatRepository.refreshMessages(chatId)
                            val merged = mergeWithOptimistic(fresh)
                            val serverIds = _messages.value.filter { !it.id.startsWith("local-") }
                            if (fresh.size != serverIds.size ||
                                fresh.lastOrNull()?.id != serverIds.lastOrNull()?.id
                            ) {
                                _messages.value = merged
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Message polling refresh failed", e)
                        }
                    }
                }
        }

        private fun startPresencePolling() {
            presenceJob?.cancel()
            presenceJob =
                viewModelScope.launch {
                    _chats.first { it.isNotEmpty() }
                    // Skip initial fetch if Trouter is already connected (it will subscribe)
                    if (!trouterClient.isConnected.value) {
                        fetchPresences()
                    }
                    while (isActive) {
                        delay(300_000)
                        if (!networkMonitor.isOnline.value) continue
                        fetchPresences()
                    }
                }
        }

        private suspend fun fetchPresences() {
            val memberIds = oneOnOneMemberIds()
            if (memberIds.isNotEmpty()) {
                try {
                    _presenceMap.value = api.getPresences(memberIds)
                } catch (e: Exception) {
                    Log.w(TAG, "Presence fetch failed", e)
                }
            }
        }

        fun stopMessagePolling() {
            messagePollingJob?.cancel()
            messagePollingJob = null
        }

        fun sendMessage(
            chatId: String,
            content: String,
            replyTo: ChatMessage? = null,
        ) {
            if (content.isBlank()) return

            val senderObjectId = api.myUserId.value ?: ""
            val senderDisplayName = myDisplayName ?: "You"

            val escapedContent = content.escapeForTeamsHtml()

            val htmlContent =
                if (replyTo != null) {
                    val replyName =
                        if (replyTo.isFromMe) senderDisplayName else replyTo.senderName
                    val preview = replyTo.content.take(200).escapeForTeamsHtml()
                    "<blockquote itemtype=\"http://schema.skype.com/Reply\">" +
                        "<strong>$replyName</strong>" +
                        "<div itemprop=\"preview\">$preview</div>" +
                        "</blockquote>" +
                        "<p>$escapedContent</p>"
                } else {
                    escapedContent
                }

            val newMsg =
                ChatMessage(
                    id = "local-${System.currentTimeMillis()}",
                    content = content,
                    contentHtml = htmlContent,
                    senderName = senderDisplayName,
                    senderId = "me",
                    senderObjectId = senderObjectId,
                    timestamp = java.time.LocalDateTime.now(),
                    isFromMe = true,
                    replyToName =
                        replyTo?.let {
                            if (it.isFromMe) senderDisplayName else it.senderName
                        },
                    replyToPreview = replyTo?.content?.take(200),
                )
            _messages.value = _messages.value + newMsg

            viewModelScope.launch {
                try {
                    chatRepository.insertLocalMessage(chatId, newMsg)
                    if (replyTo != null) {
                        api.sendHtmlMessage(chatId, htmlContent)
                    } else {
                        api.sendTextMessage(chatId, content)
                    }
                } catch (e: Exception) {
                    _error.value = "Failed to send: ${e.message}"
                }
            }
        }
    }
