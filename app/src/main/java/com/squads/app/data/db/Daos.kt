package com.squads.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTimeEpoch DESC")
    fun observeChats(): Flow<List<ChatConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatConversationEntity>)

    @Query("DELETE FROM chats WHERE id NOT IN (:keepIds)")
    suspend fun deleteChatsNotIn(keepIds: List<String>)

    @Transaction
    suspend fun replaceChats(chats: List<ChatConversationEntity>) {
        insertChats(chats)
        deleteChatsNotIn(chats.map { it.id })
    }

    @Query("UPDATE chats SET isUnread = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearMessages(chatId: String)

    @Transaction
    suspend fun replaceMessages(
        chatId: String,
        messages: List<ChatMessageEntity>,
    ) {
        clearMessages(chatId)
        insertMessages(messages)
    }
}

@Dao
interface MailDao {
    @Query("SELECT * FROM mail ORDER BY receivedDateTimeEpoch DESC")
    fun observeMail(): Flow<List<MailMessageEntity>>

    @Query("SELECT * FROM mail WHERE folderId = :folderId ORDER BY receivedDateTimeEpoch DESC")
    fun observeMailByFolder(folderId: String): Flow<List<MailMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMail(messages: List<MailMessageEntity>)

    @Query("DELETE FROM mail WHERE folderId = :folderId")
    suspend fun deleteMailByFolder(folderId: String)

    @Transaction
    suspend fun replaceMailInFolder(
        folderId: String,
        messages: List<MailMessageEntity>,
    ) {
        deleteMailByFolder(folderId)
        insertMail(messages)
    }
}
