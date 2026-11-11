package com.squads.app.viewmodel

import com.squads.app.data.ChatMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OptimisticMergeTest {
    private val now = LocalDateTime.of(2026, 11, 11, 15, 0)

    private fun local(
        content: String,
        clientMessageId: String = "",
        at: LocalDateTime = now,
    ) = ChatMessage(
        id = "${LOCAL_ID_PREFIX}$content-$clientMessageId",
        content = content,
        senderName = "You",
        senderId = "me",
        timestamp = at,
        isFromMe = true,
        clientMessageId = clientMessageId,
    )

    private fun server(
        id: String,
        content: String,
        clientMessageId: String = "",
        at: LocalDateTime = now,
        fromMe: Boolean = true,
    ) = ChatMessage(
        id = id,
        content = content,
        senderName = "You",
        senderId = "8:orgid:me",
        timestamp = at,
        isFromMe = fromMe,
        clientMessageId = clientMessageId,
    )

    private fun ids(messages: List<ChatMessage>) = messages.map { it.id }

    @Test
    fun `echo with the same client id replaces the local copy even when Teams changed the text`() {
        val pending = local("hello :)", clientMessageId = "42")
        val echo = server("s1", "hello 🙂", clientMessageId = "42")
        assertEquals(listOf("s1"), ids(mergeWithOptimistic(listOf(pending), listOf(echo))))
    }

    @Test
    fun `echo without a client id matches when only the whitespace differs`() {
        val pending = local("first  line\n\n\n\nsecond line ")
        val echo = server("s1", "first line\n\nsecond line")
        assertEquals(listOf("s1"), ids(mergeWithOptimistic(listOf(pending), listOf(echo))))
    }

    @Test
    fun `an older message with the same words does not replace the local copy`() {
        val pending = local("ok", clientMessageId = "42")
        val old = server("s0", "ok", at = now.minusHours(1))
        assertEquals(listOf("s0", pending.id), ids(mergeWithOptimistic(listOf(pending), listOf(old))))
    }

    @Test
    fun `a message with another client id is not the echo`() {
        val pending = local("ok", clientMessageId = "42")
        val other = server("s1", "ok", clientMessageId = "7")
        assertEquals(listOf("s1", pending.id), ids(mergeWithOptimistic(listOf(pending), listOf(other))))
    }

    @Test
    fun `one echo replaces only one of two identical local copies`() {
        val first = local("ok", clientMessageId = "1")
        val second = local("ok", clientMessageId = "2")
        val echo = server("s1", "ok", clientMessageId = "1")
        assertEquals(listOf("s1", second.id), ids(mergeWithOptimistic(listOf(first, second), listOf(echo))))
    }

    @Test
    fun `a message from someone else never replaces the local copy`() {
        val pending = local("ok")
        val theirs = server("s1", "ok", fromMe = false)
        assertEquals(listOf("s1", pending.id), ids(mergeWithOptimistic(listOf(pending), listOf(theirs))))
    }

    @Test
    fun `echo with the same client id matches even when the user id is unknown`() {
        val pending = local("ok", clientMessageId = "42")
        val echo = server("s1", "ok", clientMessageId = "42", fromMe = false)
        assertEquals(listOf("s1"), ids(mergeWithOptimistic(listOf(pending), listOf(echo))))
    }
}
