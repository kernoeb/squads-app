package com.squads.app.data.db

import com.squads.app.data.ChatConversation
import com.squads.app.data.ChatMessage
import com.squads.app.data.MailImportance
import com.squads.app.data.MailMessage
import com.squads.app.data.Reaction
import com.squads.app.data.toEpochMillis
import com.squads.app.data.toLocalDateTime
import org.json.JSONArray

// ─── ChatConversation ───────────────────────────────────────

fun ChatConversation.toEntity(): ChatConversationEntity =
    ChatConversationEntity(
        id = id,
        title = title,
        lastMessage = lastMessage,
        lastMessageTimeEpoch = lastMessageTime.toEpochMillis(),
        isOneOnOne = isOneOnOne,
        isUnread = isUnread,
        memberCount = memberCount,
        memberId = memberId,
        memberIds = JSONArray(memberIds).toString(),
        memberNames = JSONArray(memberNames).toString(),
    )

fun ChatConversationEntity.toDomain(): ChatConversation =
    ChatConversation(
        id = id,
        title = title,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTimeEpoch.toLocalDateTime(),
        isOneOnOne = isOneOnOne,
        isUnread = isUnread,
        memberCount = memberCount,
        memberId = memberId,
        memberIds = jsonArrayToStringList(memberIds),
        memberNames = jsonArrayToStringList(memberNames),
    )

// ─── ChatMessage ────────────────────────────────────────────

fun ChatMessage.toEntity(chatId: String): ChatMessageEntity =
    ChatMessageEntity(
        id = id,
        chatId = chatId,
        content = content,
        contentHtml = contentHtml,
        senderName = senderName,
        senderId = senderId,
        senderObjectId = senderObjectId,
        timestampEpoch = timestamp.toEpochMillis(),
        isFromMe = isFromMe,
        reactionsJson =
            JSONArray()
                .apply {
                    reactions.forEach { r ->
                        put(
                            org.json
                                .JSONObject()
                                .put("emoji", r.emoji)
                                .put("count", r.count)
                                .apply { if (r.imageUrl != null) put("imageUrl", r.imageUrl) },
                        )
                    }
                }.toString(),
        imageUrlsJson = JSONArray(imageUrls).toString(),
        replyToName = replyToName,
        replyToPreview = replyToPreview,
    )

fun ChatMessageEntity.toDomain(): ChatMessage =
    ChatMessage(
        id = id,
        content = content,
        contentHtml = contentHtml,
        senderName = senderName,
        senderId = senderId,
        senderObjectId = senderObjectId,
        timestamp = timestampEpoch.toLocalDateTime(),
        isFromMe = isFromMe,
        reactions = parseReactionsJson(reactionsJson),
        imageUrls = jsonArrayToStringList(imageUrlsJson),
        replyToName = replyToName,
        replyToPreview = replyToPreview,
    )

// ─── MailMessage ────────────────────────────────────────────

fun MailMessage.toEntity(): MailMessageEntity =
    MailMessageEntity(
        id = id,
        subject = subject,
        bodyPreview = bodyPreview,
        body = body,
        fromName = fromName,
        fromAddress = fromAddress,
        toRecipientsJson = JSONArray(toRecipients).toString(),
        receivedDateTimeEpoch = receivedDateTime.toEpochMillis(),
        isRead = isRead,
        isDraft = isDraft,
        hasAttachments = hasAttachments,
        importance = importance.name.lowercase(),
        folderId = folderId,
    )

fun MailMessageEntity.toDomain(): MailMessage =
    MailMessage(
        id = id,
        subject = subject,
        bodyPreview = bodyPreview,
        body = body,
        fromName = fromName,
        fromAddress = fromAddress,
        toRecipients = jsonArrayToStringList(toRecipientsJson),
        receivedDateTime = receivedDateTimeEpoch.toLocalDateTime(),
        isRead = isRead,
        isDraft = isDraft,
        hasAttachments = hasAttachments,
        importance = MailImportance.fromString(importance),
        folderId = folderId,
    )

// ─── Helpers ────────────────────────────────────────────────

private fun jsonArrayToStringList(json: String): List<String> =
    try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

private fun parseReactionsJson(json: String): List<Reaction> =
    try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Reaction(
                emoji = obj.getString("emoji"),
                count = obj.getInt("count"),
                imageUrl = if (obj.has("imageUrl")) obj.getString("imageUrl") else null,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
