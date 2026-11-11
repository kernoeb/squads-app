package com.squads.app.viewmodel

import com.squads.app.data.ChatMessage

internal const val LOCAL_ID_PREFIX = "local-"

// How far the server time of an echo may be from the local copy's time.
private const val ECHO_SLACK_SECONDS = 120L
private val WHITESPACE = Regex("[\\s ]+")

// Returns the server list plus the local copies that no server message has replaced yet.
// One server message replaces at most one local copy, so the same words sent twice stay twice.
internal fun mergeWithOptimistic(
    shown: List<ChatMessage>,
    server: List<ChatMessage>,
): List<ChatMessage> {
    val pending = shown.filter { it.id.startsWith(LOCAL_ID_PREFIX) }
    if (pending.isEmpty()) return server
    val taken = mutableSetOf<String>()
    val stillPending =
        pending.filter { local ->
            val echo = server.firstOrNull { it.id !in taken && isEchoOf(it, local) }
            if (echo != null) taken += echo.id
            echo == null
        }
    return server + stillPending
}

private fun isEchoOf(
    server: ChatMessage,
    local: ChatMessage,
): Boolean {
    // The client id is enough on its own. If the user id failed to load,
    // isFromMe is false for every message.
    if (local.clientMessageId.isNotEmpty() && server.clientMessageId.isNotEmpty()) {
        return server.clientMessageId == local.clientMessageId
    }
    if (!server.isFromMe) return false
    // No id to compare. Teams may change spaces and line breaks, so compare words only,
    // and only with a recent message, not an older one with the same words.
    return !server.timestamp.isBefore(local.timestamp.minusSeconds(ECHO_SLACK_SECONDS)) &&
        words(server.content) == words(local.content)
}

private fun words(text: String): String = text.trim().split(WHITESPACE).joinToString(" ")
