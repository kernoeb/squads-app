package com.squads.app.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ─── Chat models ────────────────────────────────────────────────

data class ChatConversation(
    val id: String,
    val title: String,
    val lastMessage: String,
    val lastMessageTime: LocalDateTime,
    val isOneOnOne: Boolean = true,
    val isUnread: Boolean = false,
    val avatarUrl: String? = null,
    val memberCount: Int = 2,
    val memberId: String? = null, // Other person's objectId for 1:1 chats (used for photo lookup)
    val memberIds: List<String> = emptyList(), // First 2 other members for group avatar
    val memberNames: List<String> = emptyList(), // Names matching memberIds for fallback initials
)

data class ChatMessage(
    val id: String,
    val content: String,
    val contentHtml: String = "",
    val senderName: String,
    val senderId: String,
    val senderObjectId: String = "",
    val timestamp: LocalDateTime,
    val isFromMe: Boolean = false,
    val reactions: List<Reaction> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val replyToName: String? = null,
    val replyToPreview: String? = null,
)

data class Reaction(
    val emoji: String,
    val count: Int,
)

// ─── Mail models ────────────────────────────────────────────────

data class MailFolder(
    val id: String,
    val displayName: String,
    val unreadItemCount: Int = 0,
)

data class MailMessage(
    val id: String,
    val subject: String,
    val bodyPreview: String,
    val body: String = "",
    val fromName: String,
    val fromAddress: String,
    val toRecipients: List<String> = emptyList(),
    val receivedDateTime: LocalDateTime,
    val isRead: Boolean = true,
    val isDraft: Boolean = false,
    val hasAttachments: Boolean = false,
    val importance: String = "normal",
    val folderId: String = "",
)

// ─── Calendar models ────────────────────────────────────────────

data class CalendarEvent(
    val id: String,
    val subject: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String? = null,
    val organizerName: String,
    val isOnlineMeeting: Boolean = false,
    val meetingUrl: String? = null,
    val isAllDay: Boolean = false,
    val isCancelled: Boolean = false,
    val responseStatus: String = "none", // accepted, declined, tentative, none
    val attendees: List<EventAttendee> = emptyList(),
)

data class EventAttendee(
    val name: String,
    val email: String,
    val response: String = "none",
)

// ─── Teams models ───────────────────────────────────────────────

data class Team(
    val id: String,
    val displayName: String,
    val channels: List<Channel> = emptyList(),
)

data class Channel(
    val id: String,
    val displayName: String,
)

data class ChannelMessage(
    val id: String,
    val content: String,
    val senderName: String,
    val senderObjectId: String = "",
    val timestamp: LocalDateTime,
    val replyCount: Int = 0,
    val reactions: List<Reaction> = emptyList(),
)

// ─── User models ────────────────────────────────────────────────

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val jobTitle: String? = null,
)

enum class PresenceAvailability(
    val displayName: String,
) {
    Available("Available"),
    AvailableIdle("Available"),
    Busy("Busy"),
    BusyIdle("Busy"),
    DoNotDisturb("Do not disturb"),
    Away("Away"),
    BeRightBack("Away"),
    Offline("Offline"),
    Unknown("Unknown"),
    ;

    val isOnline: Boolean get() = this != Offline && this != Unknown

    companion object {
        fun fromString(value: String): PresenceAvailability = entries.find { it.name.equals(value, ignoreCase = true) } ?: Unknown
    }
}

// ─── Search models ──────────────────────────────────────────────

data class SearchResult(
    val type: SearchResultType,
    val title: String,
    val subtitle: String,
    val preview: String,
    val id: String,
)

enum class SearchResultType { CHAT, MAIL, CALENDAR }

// ─── Helpers ────────────────────────────────────────────────────

fun LocalDateTime.toRelativeTime(): String {
    val now = LocalDateTime.now()
    val diffMinutes =
        java.time.Duration
            .between(this, now)
            .toMinutes()
    return when {
        diffMinutes < 1 -> "now"
        diffMinutes < 60 -> "${diffMinutes}m"
        diffMinutes < 1440 -> "${diffMinutes / 60}h"
        diffMinutes < 10080 -> "${diffMinutes / 1440}d"
        else -> format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

fun LocalDateTime.toTimeString(): String = format(DateTimeFormatter.ofPattern("HH:mm"))

fun LocalDateTime.toDateTimeString(): String = format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

fun graphProfilePhotoUrl(userId: String): String? =
    userId.takeIf { it.isNotEmpty() }?.let { "https://graph.microsoft.com/v1.0/users/$it/photo/\$value" }

fun graphGroupPhotoUrl(groupId: String): String? =
    groupId.takeIf { it.isNotEmpty() }?.let { "https://graph.microsoft.com/v1.0/groups/$it/photo/\$value" }

fun String.mriToObjectId(): String = removePrefix("8:orgid:").removePrefix("8:lync:")

fun LocalDateTime.toEpochMillis(): Long =
    try {
        atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: ArithmeticException) {
        0L
    }

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), java.time.ZoneId.systemDefault())
