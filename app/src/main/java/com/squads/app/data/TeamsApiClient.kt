package com.squads.app.data

import android.net.Uri
import com.squads.app.auth.AuthManager
import com.squads.app.auth.OAuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val SCOPE_GRAPH = "https://graph.microsoft.com/.default"
private const val SCOPE_CHATSVCAGG = "https://chatsvcagg.teams.microsoft.com/.default"
private const val SCOPE_IC3 = "https://ic3.teams.office.com/.default"

const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"

@Singleton
class TeamsApiClient
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val emojiManager: EmojiManager,
        private val httpClient: OkHttpClient,
    ) {
        private val tokenCache = mutableMapOf<String, Pair<String, Long>>()
        private val tokenMutex = Mutex()
        private val userNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        @Volatile private var cachedMyUserId: String? = null

        private var cachedUserDetails: Pair<List<ChatConversation>, List<Team>>? = null
        private var cacheTimestamp = 0L

        // ─── Token management ────────────────────────────────────────

        private suspend fun getToken(scope: String): String =
            tokenMutex.withLock {
                val cached = tokenCache[scope]
                val now = System.currentTimeMillis() / 1000
                if (cached != null && cached.second > now + 60) {
                    return@withLock cached.first
                }

                val refreshToken =
                    authManager.getRefreshToken()
                        ?: throw Exception("Not authenticated")

                refreshAccessToken(refreshToken, scope)
            }

        private suspend fun refreshAccessToken(
            refreshToken: String,
            scope: String,
        ): String {
            val body =
                buildString {
                    append("client_id=${OAuthConfig.CLIENT_ID}")
                    append("&scope=${Uri.encode("$scope openid profile offline_access")}")
                    append("&grant_type=refresh_token")
                    append("&client_info=1")
                    append("&x-client-SKU=msal.js.browser")
                    append("&x-client-VER=3.7.1")
                    append("&refresh_token=$refreshToken")
                    append("&claims=${Uri.encode("{\"access_token\":{\"xms_cc\":{\"values\":[\"CP1\"]}}}")}")
                }

            val json =
                httpPost(
                    url = OAuthConfig.tokenV2Url(),
                    body = body,
                    contentType = "application/x-www-form-urlencoded",
                    headers = mapOf("Origin" to "https://teams.microsoft.com"),
                )

            val accessToken = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600)
            tokenCache[scope] = accessToken to (System.currentTimeMillis() / 1000 + expiresIn)
            return accessToken
        }

        // ─── HTTP helpers ────────────────────────────────────────────

        private suspend fun httpGet(
            url: String,
            token: String,
        ): String =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", USER_AGENT)
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw Exception("API error (${response.code}): $body")
                    }
                    body
                }
            }

        private suspend fun httpPost(
            url: String,
            body: String,
            contentType: String,
            headers: Map<String, String> = emptyMap(),
            token: String? = null,
        ): JSONObject =
            withContext(Dispatchers.IO) {
                val requestBody = body.toRequestBody(contentType.toMediaType())
                val builder =
                    Request
                        .Builder()
                        .url(url)
                        .post(requestBody)
                        .header("User-Agent", USER_AGENT)
                token?.let { builder.header("Authorization", "Bearer $it") }
                headers.forEach { (k, v) -> builder.header(k, v) }

                httpClient.newCall(builder.build()).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: $responseBody")
                    }
                    JSONObject(responseBody)
                }
            }

        private suspend fun authenticatedGet(
            url: String,
            scope: String,
        ): String {
            val token = getToken(scope)
            return httpGet(url, token)
        }

        private suspend fun graphGet(path: String) = authenticatedGet(path, SCOPE_GRAPH)

        /** IC3 token for Trouter auth + registrar. */
        suspend fun getIc3Token(): String = getToken(SCOPE_IC3)

        /** Public token accessor for Coil auth interceptor. */
        suspend fun getTokenForUrl(url: String): String? =
            when {
                "graph.microsoft.com" in url -> getToken(SCOPE_GRAPH)
                "teams.microsoft.com" in url || "asm.skype.com" in url -> getToken(SCOPE_IC3)
                else -> null
            }

        // ─── User / profile ──────────────────────────────────────────

        suspend fun getMe(): UserProfile {
            emojiManager.init()
            val json = JSONObject(graphGet("https://graph.microsoft.com/v1.0/me?\$select=id,displayName,mail,jobTitle"))
            val id = json.str("id")
            cachedMyUserId = id
            return UserProfile(
                id = id,
                displayName = json.str("displayName", "User"),
                email = json.str("mail"),
                jobTitle = json.str("jobTitle").ifEmpty { null },
            )
        }

        private suspend fun getMyUserId(): String {
            cachedMyUserId?.let { return it }
            return getMe().id.also { cachedMyUserId = it }
        }

        private suspend fun resolveUserName(userId: String): String? {
            userNameCache[userId]?.let { return it }
            return try {
                val json = JSONObject(graphGet("https://graph.microsoft.com/v1.0/users/$userId?\$select=displayName"))
                json.str("displayName").ifEmpty { null }?.also { userNameCache[userId] = it }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Fallback for external/cross-tenant users: Graph `/users/{id}` fails (404)
         * because the objectId belongs to another tenant. Fetch a few messages from
         * the chat to extract `imdisplayname` for the unresolved user.
         */
        private suspend fun resolveViaMessages(
            rawChats: JSONArray,
            unresolved: Set<String>,
        ) {
            // Map each unresolved userId → chatId it belongs to
            val targets = mutableListOf<Pair<String, String>>() // chatId to userId
            for (chat in rawChats.objects()) {
                if (chat.optBoolean("isConversationDeleted", false)) continue
                val members = chat.optJSONArray("members") ?: continue
                for (m in members.objects()) {
                    val objId = m.str("objectId")
                    if (objId in unresolved && !targets.any { it.second == objId }) {
                        targets += chat.optString("id") to objId
                    }
                }
            }
            coroutineScope {
                targets.take(10).map { (chatId, userId) ->
                    async {
                        try {
                            val url = "https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations/$chatId/messages?pageSize=5"
                            val json = JSONObject(authenticatedGet(url, SCOPE_IC3))
                            val messages = json.optJSONArray("messages") ?: return@async
                            for (msg in messages.objects()) {
                                val from = msg.optString("from", "")
                                if (userId in from) {
                                    val name = msg.str("imdisplayname")
                                    if (name.isNotEmpty()) {
                                        userNameCache.putIfAbsent(userId, name)
                                        return@async
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }.awaitAll()
            }
        }

        // ─── User details (chats + teams) ────────────────────────────

        /**
         * Fetch user details (teams + chats). Cached for 30s
         * so ChatsViewModel and TeamsViewModel don't duplicate the call.
         */
        suspend fun getUserDetails(): Pair<List<ChatConversation>, List<Team>> {
            val now = System.currentTimeMillis()
            cachedUserDetails?.let { if (now - cacheTimestamp < 30_000) return it }

            val body =
                authenticatedGet(
                    "https://teams.microsoft.com/api/csa/emea/api/v2/teams/users/me",
                    SCOPE_CHATSVCAGG,
                )
            val json = JSONObject(body)

            val rawChats = json.optJSONArray("chats") ?: JSONArray()
            val teams = parseTeamList(json.optJSONArray("teams") ?: JSONArray())
            val myUserId =
                try {
                    getMyUserId()
                } catch (_: Exception) {
                    ""
                }

            // Resolve unknown member names in parallel (up to 30)
            val idsToResolve = collectUnresolvedMemberIds(rawChats, myUserId)
            if (idsToResolve.isNotEmpty()) {
                coroutineScope {
                    idsToResolve.take(30).map { async { resolveUserName(it) } }.awaitAll()
                }
            }

            // Fallback: resolve remaining via chat messages (cross-tenant users)
            val stillUnresolved = idsToResolve.filterTo(mutableSetOf()) { !userNameCache.containsKey(it) }
            if (stillUnresolved.isNotEmpty()) {
                resolveViaMessages(rawChats, stillUnresolved)
            }

            val chats = parseChatList(rawChats, myUserId)
            return (chats to teams).also {
                cachedUserDetails = it
                cacheTimestamp = now
            }
        }

        fun invalidateCache() {
            cachedUserDetails = null
        }

        private fun collectUnresolvedMemberIds(
            rawChats: JSONArray,
            myUserId: String,
        ): Set<String> {
            val ids = mutableSetOf<String>()
            for (chat in rawChats.objects()) {
                if (chat.optBoolean("isConversationDeleted", false)) continue
                val title = chat.str("title")
                val needsResolution =
                    title.isEmpty() ||
                        title == "Direct Chat" ||
                        title == "Group Chat" ||
                        title.startsWith("Group (")
                if (!needsResolution) continue

                for (member in (chat.optJSONArray("members") ?: JSONArray()).objects()) {
                    val objId = member.str("objectId")
                    if (objId.isNotEmpty() && objId != myUserId && !userNameCache.containsKey(objId)) {
                        ids += objId
                    }
                }
            }
            return ids
        }

        // ─── Chat parsing ────────────────────────────────────────────

        private fun parseChatList(
            arr: JSONArray,
            myUserId: String,
        ): List<ChatConversation> =
            arr
                .objects()
                .filter { !it.optBoolean("isConversationDeleted", false) }
                .map { c ->
                    val members = c.optJSONArray("members") ?: JSONArray()
                    val isOneOnOne = c.optBoolean("isOneOnOne", false)
                    val lastMsg = c.optJSONObject("lastMessage")
                    val otherMemberIds =
                        if (!isOneOnOne) {
                            findOtherMemberIds(members, myUserId, 2)
                        } else {
                            emptyList()
                        }

                    ChatConversation(
                        id = c.optString("id"),
                        title = getChatDisplayName(c, members, myUserId),
                        lastMessage = lastMsg?.let { formatLastMessage(it) } ?: "",
                        lastMessageTime = parseLastMessageTime(c, lastMsg),
                        isOneOnOne = isOneOnOne,
                        isUnread = !c.optBoolean("isRead", true),
                        memberCount = members.length(),
                        memberId = if (isOneOnOne) findOtherMemberId(members, myUserId) else null,
                        memberIds = otherMemberIds,
                        memberNames = otherMemberIds.map { userNameCache[it] ?: "?" },
                    )
                }.sortedByDescending { it.lastMessageTime }

        private fun formatLastMessage(msg: JSONObject): String {
            val parsed = HtmlParser.parseMessage(msg.str("content"))
            if (parsed.text.isNotBlank()) return parsed.text
            if (parsed.imageUrls.isNotEmpty()) return "Sent a photo"
            return ""
        }

        private fun parseLastMessageTime(
            chat: JSONObject,
            lastMsg: JSONObject?,
        ): LocalDateTime {
            if (lastMsg != null) {
                val ts =
                    lastMsg
                        .str("composeTime")
                        .ifEmpty { lastMsg.str("composetime") }
                        .ifEmpty { lastMsg.str("originalArrivalTime") }
                        .ifEmpty { lastMsg.str("originalarrivaltime") }
                return parseTimestamp(ts)
            }
            return parseTimestamp(chat.str("lastJoinAt").ifEmpty { chat.str("createdAt") })
        }

        private fun findOtherMemberIds(
            members: JSONArray,
            myUserId: String,
            limit: Int,
        ): List<String> =
            members
                .objects()
                .map { it.str("objectId") }
                .filter { it.isNotEmpty() && it != myUserId }
                .take(limit)

        private fun findOtherMemberId(
            members: JSONArray,
            myUserId: String,
        ): String? = findOtherMemberIds(members, myUserId, 1).firstOrNull()

        private fun getChatDisplayName(
            chat: JSONObject,
            members: JSONArray,
            myUserId: String,
        ): String {
            val rawTitle = chat.str("title")
            if (rawTitle.isNotEmpty() &&
                rawTitle != "Direct Chat" &&
                rawTitle != "Group Chat" &&
                !rawTitle.startsWith("Group (")
            ) {
                return rawTitle
            }

            val memberNames =
                members
                    .objects()
                    .filter { it.str("objectId").let { id -> id.isNotEmpty() && id != myUserId } }
                    .mapNotNull { m ->
                        userNameCache[m.str("objectId")]
                            ?: m.str("displayName").ifEmpty { null }
                    }

            if (memberNames.isNotEmpty()) {
                return when {
                    memberNames.size <= 3 -> memberNames.joinToString(" & ")
                    else -> "${memberNames[0]} & ${memberNames[1]} +${memberNames.size - 2}"
                }
            }

            val lastMsg = chat.optJSONObject("lastMessage")
            if (lastMsg != null && !chat.optBoolean("isLastMessageFromMe", false)) {
                val senderName = lastMsg.str("imDisplayName")
                if (senderName.isNotEmpty()) return senderName
            }

            return if (chat.optBoolean("isOneOnOne", false)) {
                "1:1 Chat"
            } else {
                "Group (${members.length()} members)"
            }
        }

        // ─── Chat messages ───────────────────────────────────────────

        suspend fun getChatMessages(threadId: String): List<ChatMessage> {
            val url = "https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations/$threadId/messages?pageSize=200"
            val json = JSONObject(authenticatedGet(url, SCOPE_IC3))
            val messages = json.optJSONArray("messages") ?: return emptyList()
            val myUserId =
                try {
                    getMyUserId()
                } catch (_: Exception) {
                    ""
                }

            return messages
                .objects()
                .filter { it.str("messagetype") in setOf("RichText/Html", "Text") }
                .mapNotNull { m ->
                    val rawHtml = m.str("content")
                    val parsed = HtmlParser.parseMessage(rawHtml)
                    if (parsed.text.isBlank() && parsed.imageUrls.isEmpty()) return@mapNotNull null

                    val senderMri = stripSenderUrl(m.str("from"))
                    val senderObjId = senderMri.removePrefix("8:orgid:").removePrefix("8:lync:")

                    ChatMessage(
                        id = m.optString("id", ""),
                        content = parsed.text,
                        contentHtml = rawHtml,
                        senderName = m.str("imdisplayname", "Unknown"),
                        senderId = senderMri,
                        senderObjectId = senderObjId,
                        timestamp = parseTimestamp(m.str("composetime").ifEmpty { m.str("originalarrivaltime") }),
                        isFromMe = senderObjId == myUserId,
                        reactions = parseReactions(m.optJSONObject("properties")?.optJSONArray("emotions")),
                        imageUrls = parsed.imageUrls,
                        replyToName = parsed.replyToName,
                        replyToPreview = parsed.replyToPreview,
                    )
                }.reversed()
        }

        private fun stripSenderUrl(url: String): String =
            url
                .removePrefix("https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/contacts/")
                .removePrefix("https://notifications.skype.net/v1/users/ME/contacts/")

        // ─── Mail ────────────────────────────────────────────────────

        suspend fun getMail(limit: Int = 25): List<MailMessage> {
            val url = "https://graph.microsoft.com/v1.0/me/messages?\$top=$limit&\$orderby=receivedDateTime desc"
            val arr = JSONObject(graphGet(url)).optJSONArray("value") ?: return emptyList()
            return arr.objects().map(::parseMailMessage)
        }

        suspend fun getMailDetail(messageId: String): MailMessage =
            parseMailMessage(JSONObject(graphGet("https://graph.microsoft.com/v1.0/me/messages/$messageId")))

        private fun parseMailMessage(m: JSONObject): MailMessage {
            val from = m.optJSONObject("from")?.optJSONObject("emailAddress")
            val toList =
                (m.optJSONArray("toRecipients") ?: JSONArray())
                    .objects()
                    .map { it.optJSONObject("emailAddress")?.optString("address", "") ?: "" }

            return MailMessage(
                id = m.optString("id"),
                subject = m.optString("subject", "(No subject)"),
                bodyPreview = m.optString("bodyPreview", ""),
                body = m.optJSONObject("body")?.optString("content", "") ?: "",
                fromName = from?.optString("name", "Unknown") ?: "Unknown",
                fromAddress = from?.optString("address", "") ?: "",
                toRecipients = toList,
                receivedDateTime = parseTimestamp(m.optString("receivedDateTime", "")),
                isRead = m.optBoolean("isRead", true),
                isDraft = m.optBoolean("isDraft", false),
                hasAttachments = m.optBoolean("hasAttachments", false),
                importance = m.optString("importance", "normal"),
            )
        }

        // ─── Calendar ────────────────────────────────────────────────

        suspend fun getEvents(days: Int): List<CalendarEvent> {
            val now = ZonedDateTime.now(ZoneId.of("UTC"))
            val start = now.toLocalDate().atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
            val end =
                now
                    .toLocalDate()
                    .plusDays(days.toLong())
                    .atStartOfDay(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ISO_INSTANT)

            val url =
                "https://graph.microsoft.com/v1.0/me/calendarView" +
                    "?startDateTime=$start&endDateTime=$end&\$orderby=start/dateTime&\$top=50"
            val arr = JSONObject(graphGet(url)).optJSONArray("value") ?: return emptyList()

            return arr.objects().map { e ->
                val organizer = e.optJSONObject("organizer")?.optJSONObject("emailAddress")
                val attendees =
                    (e.optJSONArray("attendees") ?: JSONArray()).objects().map { a ->
                        val email = a.optJSONObject("emailAddress")
                        EventAttendee(
                            name = email?.optString("name", "") ?: "",
                            email = email?.optString("address", "") ?: "",
                            response = a.optJSONObject("status")?.optString("response", "none") ?: "none",
                        )
                    }

                CalendarEvent(
                    id = e.optString("id"),
                    subject = e.optString("subject", "(No subject)"),
                    startTime = parseTimestamp(e.optJSONObject("start")?.optString("dateTime", "") ?: ""),
                    endTime = parseTimestamp(e.optJSONObject("end")?.optString("dateTime", "") ?: ""),
                    location = e.optJSONObject("location")?.optString("displayName", "")?.ifEmpty { null },
                    organizerName = organizer?.optString("name", "Unknown") ?: "Unknown",
                    isOnlineMeeting = e.optBoolean("isOnlineMeeting", false),
                    meetingUrl = e.optJSONObject("onlineMeeting")?.optString("joinUrl", ""),
                    isAllDay = e.optBoolean("isAllDay", false),
                    isCancelled = e.optBoolean("isCancelled", false),
                    responseStatus = e.optJSONObject("responseStatus")?.optString("response", "none") ?: "none",
                    attendees = attendees,
                )
            }
        }

        // ─── Teams / channels ────────────────────────────────────────

        suspend fun getChannelMessages(
            teamId: String,
            channelId: String,
        ): List<ChannelMessage> {
            val url = "https://teams.microsoft.com/api/csa/emea/api/v3/teams/$teamId/channels/$channelId/messages"
            val json = JSONObject(authenticatedGet(url, SCOPE_CHATSVCAGG))
            val arr = json.optJSONArray("messages") ?: json.optJSONArray("replyChains") ?: return emptyList()

            return arr.objects().mapNotNull { m ->
                val content = HtmlParser.parseMessage(m.str("content")).text
                if (content.isBlank()) return@mapNotNull null

                ChannelMessage(
                    id = m.optString("id", ""),
                    content = content,
                    senderName = m.str("imdisplayname").ifEmpty { m.str("imDisplayName", "Unknown") },
                    timestamp = parseTimestamp(m.str("composeTime").ifEmpty { m.str("composetime") }),
                    reactions = parseReactions(m.optJSONObject("properties")?.optJSONArray("emotions")),
                )
            }
        }

        private fun parseTeamList(arr: JSONArray): List<Team> =
            arr.objects().map { t ->
                Team(
                    id = t.optString("id"),
                    displayName = t.optString("displayName", "Team"),
                    channels =
                        (t.optJSONArray("channels") ?: JSONArray()).objects().map { c ->
                            Channel(id = c.optString("id"), displayName = c.optString("displayName", "Channel"))
                        },
                )
            }

        // ─── Send message ────────────────────────────────────────────

        suspend fun sendMessage(
            conversationId: String,
            content: String,
        ) {
            val token = getToken(SCOPE_IC3)
            val me = getMe()
            val now = Instant.now().toString()
            val messageId = (Math.random() * Long.MAX_VALUE).toLong().toString()
            val htmlContent =
                content
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>")

            val body =
                JSONObject().apply {
                    put("id", "-1")
                    put("type", "Message")
                    put("conversationid", conversationId)
                    put("conversation_link", "blah/$conversationId")
                    put("from", "8:orgid:${me.id}")
                    put("composetime", now)
                    put("originalarrivaltime", now)
                    put("content", htmlContent)
                    put("messagetype", "RichText/Html")
                    put("contenttype", "Html")
                    put("imdisplayname", me.displayName)
                    put("clientmessageid", messageId)
                    put("call_id", "")
                    put("state", 0)
                    put("version", "0")
                    put("amsreferences", JSONArray())
                    put(
                        "properties",
                        JSONObject().apply {
                            put("importance", "")
                            put("subject", JSONObject.NULL)
                            put("title", "")
                            put("cards", "[]")
                            put("links", "[]")
                            put("mentions", "[]")
                            put("onbehalfof", JSONObject.NULL)
                            put("files", "[]")
                            put("policy_violation", JSONObject.NULL)
                            put("format_variant", "TEAMS")
                        },
                    )
                    put("post_type", "Standard")
                    put("cross_post_channels", JSONArray())
                }

            httpPost(
                url = "https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations/$conversationId/messages",
                body = body.toString(),
                contentType = "application/json",
                token = token,
            )
        }

        // ─── Parsing helpers ─────────────────────────────────────────

        private fun parseReactions(emotions: JSONArray?): List<Reaction> {
            if (emotions == null) return emptyList()
            return emotions.objects().map { e ->
                val key = e.optString("key", "")
                Reaction(emoji = emojiManager.getEmoji(key), count = e.optJSONArray("users")?.length() ?: 0)
            }
        }

        private fun parseTimestamp(ts: String): LocalDateTime {
            if (ts.isBlank()) return LocalDateTime.MIN
            return try {
                LocalDateTime.ofInstant(Instant.parse(ts), ZoneId.systemDefault())
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(ts.removeSuffix("Z"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (_: Exception) {
                    ts
                        .toLongOrNull()
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                        ?: LocalDateTime.MIN
                }
            }
        }
    }

// ─── JSON extension helpers ──────────────────────────────────────

/** Safe string extraction — returns [fallback] for null and JSON-null values. */
private fun JSONObject.str(
    key: String,
    fallback: String = "",
): String {
    if (isNull(key)) return fallback
    return optString(key, fallback)
}

/** Iterate a JSONArray as a sequence of JSONObjects. */
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
