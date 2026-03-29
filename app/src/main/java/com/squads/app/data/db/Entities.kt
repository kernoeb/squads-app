package com.squads.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lastMessage: String,
    val lastMessageTimeEpoch: Long,
    val isOneOnOne: Boolean,
    val isUnread: Boolean,
    val memberCount: Int,
    val memberId: String?,
    val memberIds: String, // JSON array
    val memberNames: String, // JSON array
)

@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val content: String,
    val contentHtml: String,
    val senderName: String,
    val senderId: String,
    val senderObjectId: String,
    val timestampEpoch: Long,
    val isFromMe: Boolean,
    val reactionsJson: String, // JSON array
    val imageUrlsJson: String, // JSON array
    val replyToName: String?,
    val replyToPreview: String?,
)

@Entity(tableName = "mail")
data class MailMessageEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val bodyPreview: String,
    val body: String,
    val fromName: String,
    val fromAddress: String,
    val toRecipientsJson: String, // JSON array
    val receivedDateTimeEpoch: Long,
    val isRead: Boolean,
    val isDraft: Boolean,
    val hasAttachments: Boolean,
    val importance: String,
    val folderId: String = "",
)
