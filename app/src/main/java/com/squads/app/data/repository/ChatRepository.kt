package com.squads.app.data.repository

import com.squads.app.data.ChatConversation
import com.squads.app.data.ChatMessage
import com.squads.app.data.TeamsApiClient
import com.squads.app.data.db.ChatDao
import com.squads.app.data.db.toDomain
import com.squads.app.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository
    @Inject
    constructor(
        private val api: TeamsApiClient,
        private val chatDao: ChatDao,
    ) {
        fun observeChats(): Flow<List<ChatConversation>> = chatDao.observeChats().map { entities -> entities.map { it.toDomain() } }

        suspend fun refreshChats(): List<ChatConversation> {
            val (chats, _) = api.getUserDetails()
            chatDao.insertChats(chats.map { it.toEntity() })
            return chats
        }

        suspend fun refreshMessages(chatId: String): List<ChatMessage> {
            val messages = api.getChatMessages(chatId)
            chatDao.replaceMessages(chatId, messages.map { it.toEntity(chatId) })
            return messages
        }

        suspend fun insertLocalMessage(
            chatId: String,
            message: ChatMessage,
        ) {
            chatDao.insertMessages(listOf(message.toEntity(chatId)))
        }
    }
