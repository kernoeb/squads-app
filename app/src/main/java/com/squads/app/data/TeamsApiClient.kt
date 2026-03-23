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
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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

private val HTML_TAG_REGEX = Regex("<[^>]*>")

@Singleton
class TeamsApiClient @Inject constructor(
    private val authManager: AuthManager,
) {
    private val tokenCache = mutableMapOf<String, Pair<String, Long>>()
    private val tokenMutex = Mutex()
    private val userNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val NO_PHOTO = ByteArray(0) // sentinel for "tried, no photo available"
    private var cachedMyUserId: String? = null

    // Cached getUserDetails result to avoid duplicate API calls
    private var cachedUserDetails: Pair<List<ChatConversation>, List<Team>>? = null
    private var cacheTimestamp = 0L

    private suspend fun getToken(scope: String): String = tokenMutex.withLock {
        val cached = tokenCache[scope]
        val now = System.currentTimeMillis() / 1000
        if (cached != null && cached.second > now + 60) {
            return@withLock cached.first
        }

        val refreshToken = authManager.getRefreshToken()
            ?: throw Exception("Not authenticated")

        genToken(refreshToken, scope)
    }

    private suspend fun genToken(refreshToken: String, scope: String): String = withContext(Dispatchers.IO) {
        val url = URL(OAuthConfig.tokenV2Url())
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Origin", "https://teams.microsoft.com")
        conn.doOutput = true

        val body = buildString {
            append("client_id=${OAuthConfig.CLIENT_ID}")
            append("&scope=${Uri.encode("$scope openid profile offline_access")}")
            append("&grant_type=refresh_token")
            append("&client_info=1")
            append("&x-client-SKU=msal.js.browser")
            append("&x-client-VER=3.7.1")
            append("&refresh_token=$refreshToken")
            append("&claims=${Uri.encode("{\"access_token\":{\"xms_cc\":{\"values\":[\"CP1\"]}}}")}")
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("Token exchange failed ($code): $err")
        }

        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val accessToken = json.getString("access_token")
        val expiresIn = json.optLong("expires_in", 3600)

        tokenCache[scope] = accessToken to (System.currentTimeMillis() / 1000 + expiresIn)
        accessToken
    }

    private suspend fun authenticatedGet(path: String, scope: String): String = withContext(Dispatchers.IO) {
        val token = getToken(scope)
        val conn = URL(path).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("User-Agent", USER_AGENT)

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("API error (${conn.responseCode}): $err")
        }
        conn.inputStream.bufferedReader().readText()
    }

    private suspend fun graphGet(path: String) = authenticatedGet(path, SCOPE_GRAPH)

    suspend fun getMe(): UserProfile {
        val body = graphGet("https://graph.microsoft.com/v1.0/me?\$select=id,displayName,mail,jobTitle")
        val json = JSONObject(body)
        val id = json.optString("id")
        cachedMyUserId = id
        return UserProfile(
            id = id,
            displayName = json.optString("displayName", "User"),
            email = json.optString("mail", ""),
            jobTitle = json.optString("jobTitle", "").ifEmpty { null },
        )
    }

    suspend fun getProfilePhoto(userId: String): ByteArray? {
        if (userId.isBlank()) return null
        photoCache[userId]?.let { cached ->
            return if (cached === NO_PHOTO) null else cached
        }
        return try {
            val token = getToken(SCOPE_GRAPH)
            withContext(Dispatchers.IO) {
                val conn = URL("https://graph.microsoft.com/v1.0/users/$userId/photo/\$value")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("User-Agent", USER_AGENT)
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.readBytes()
                    photoCache[userId] = bytes
                    bytes
                } else {
                    photoCache[userId] = NO_PHOTO
                    null
                }
            }
        } catch (_: Exception) {
            photoCache[userId] = NO_PHOTO
            null
        }
    }

    private suspend fun getMyUserId(): String {
        cachedMyUserId?.let { return it }
        return getMe().id.also { cachedMyUserId = it }
    }

    private suspend fun resolveUserName(userId: String): String? {
        userNameCache[userId]?.let { return it }
        return try {
            val body = graphGet("https://graph.microsoft.com/v1.0/users/$userId?\$select=displayName")
            val name = JSONObject(body).optString("displayName", "")
            if (name.isNotEmpty()) {
                userNameCache[userId] = name
                name
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * Fetch user details (teams + chats). Cached for 30 seconds
     * so ChatsViewModel and TeamsViewModel don't duplicate the call.
     */
    suspend fun getUserDetails(): Pair<List<ChatConversation>, List<Team>> {
        val now = System.currentTimeMillis()
        cachedUserDetails?.let { cached ->
            if (now - cacheTimestamp < 30_000) return cached
        }

        val body = authenticatedGet(
            "https://teams.microsoft.com/api/csa/emea/api/v2/teams/users/me",
            SCOPE_CHATSVCAGG,
        )
        val json = JSONObject(body)

        val rawChats = json.optJSONArray("chats") ?: JSONArray()
        val teams = parseTeamList(json.optJSONArray("teams") ?: JSONArray())

        val myUserId = try { getMyUserId() } catch (_: Exception) { "" }

        // Collect unique member objectIds that need name resolution
        val idsToResolve = mutableSetOf<String>()
        for (i in 0 until rawChats.length()) {
            val c = rawChats.getJSONObject(i)
            if (c.optBoolean("isConversationDeleted", false)) continue

            val title = c.safeString("title")
            val needsNameResolution = title.isEmpty()
                || title == "Direct Chat" || title == "Group Chat"
                || title.startsWith("Group (")

            if (needsNameResolution) {
                val members = c.optJSONArray("members") ?: JSONArray()
                for (j in 0 until members.length()) {
                    val objId = members.getJSONObject(j).safeString("objectId")
                    if (objId.isNotEmpty() && objId != myUserId && !userNameCache.containsKey(objId)) {
                        idsToResolve.add(objId)
                    }
                }
            }
        }

        // Resolve user names in parallel (up to 30)
        coroutineScope {
            idsToResolve.take(30).map { userId ->
                async { resolveUserName(userId) }
            }.awaitAll()
        }

        val chats = parseChatList(rawChats, myUserId)
        val result = chats to teams
        cachedUserDetails = result
        cacheTimestamp = now
        return result
    }

    fun invalidateCache() {
        cachedUserDetails = null
    }

    private fun parseChatList(arr: JSONArray, myUserId: String): List<ChatConversation> {
        val result = mutableListOf<ChatConversation>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            if (c.optBoolean("isConversationDeleted", false)) continue

            val members = c.optJSONArray("members") ?: JSONArray()
            val title = getChatDisplayName(c, members, myUserId)

            val lastMsg = c.optJSONObject("lastMessage")
            val lastMsgContent = if (lastMsg != null) stripHtml(lastMsg.safeString("content")) else ""
            // Try all possible timestamp field names (API uses inconsistent casing)
            val lastMsgTime = if (lastMsg != null) {
                val ts = lastMsg.safeString("composeTime")
                    .ifEmpty { lastMsg.safeString("composetime") }
                    .ifEmpty { lastMsg.safeString("originalArrivalTime") }
                    .ifEmpty { lastMsg.safeString("originalarrivaltime") }
                parseTimestamp(ts)
            } else {
                // Fallback: use chat-level timestamps
                val joinedAt = c.safeString("lastJoinAt")
                    .ifEmpty { c.safeString("createdAt") }
                parseTimestamp(joinedAt)
            }

            val isOneOnOne = c.optBoolean("isOneOnOne", false)
            val memberId = if (isOneOnOne) {
                (0 until members.length())
                    .map { members.getJSONObject(it).safeString("objectId") }
                    .firstOrNull { it.isNotEmpty() && it != myUserId }
            } else null

            result.add(ChatConversation(
                id = c.optString("id"),
                title = title,
                lastMessage = lastMsgContent,
                lastMessageTime = lastMsgTime,
                isOneOnOne = isOneOnOne,
                isUnread = !c.optBoolean("isRead", true),
                memberCount = members.length(),
                memberId = memberId,
            ))
        }
        return result.sortedByDescending { it.lastMessageTime }
    }

    private fun getChatDisplayName(chat: JSONObject, members: JSONArray, myUserId: String): String {
        val rawTitle = chat.safeString("title")
        if (rawTitle.isNotEmpty() && rawTitle != "Direct Chat"
            && rawTitle != "Group Chat" && !rawTitle.startsWith("Group (")) {
            return rawTitle
        }

        val memberNames = mutableListOf<String>()
        for (i in 0 until members.length()) {
            val objId = members.getJSONObject(i).safeString("objectId")
            if (objId.isNotEmpty() && objId != myUserId) {
                userNameCache[objId]?.let { memberNames.add(it) }
            }
        }

        if (memberNames.isNotEmpty()) {
            return if (memberNames.size <= 3) memberNames.joinToString(" & ")
            else "${memberNames[0]} & ${memberNames[1]} +${memberNames.size - 2}"
        }

        val lastMsg = chat.optJSONObject("lastMessage")
        if (lastMsg != null && !chat.optBoolean("isLastMessageFromMe", false)) {
            val senderName = lastMsg.safeString("imDisplayName")
            if (senderName.isNotEmpty()) return senderName
        }

        return if (chat.optBoolean("isOneOnOne", false)) "1:1 Chat"
        else "Group (${members.length()} members)"
    }

    suspend fun getChatMessages(threadId: String): List<ChatMessage> {
        val url = "https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations/$threadId/messages?pageSize=200"
        val body = authenticatedGet(url, SCOPE_IC3)
        val json = JSONObject(body)
        val messages = json.optJSONArray("messages") ?: return emptyList()

        val myUserId = try { getMyUserId() } catch (_: Exception) { "" }

        val result = mutableListOf<ChatMessage>()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val msgType = m.safeString("messagetype")
            if (msgType != "RichText/Html" && msgType != "Text") continue

            val rawHtml = m.safeString("content")
            val content = stripHtml(rawHtml)
            if (content.isBlank()) continue

            val rawFrom = m.safeString("from")
            val senderMri = stripSenderUrl(rawFrom)
            val senderObjId = senderMri.removePrefix("8:orgid:").removePrefix("8:lync:")

            result.add(ChatMessage(
                id = m.optString("id", "$i"),
                content = content,
                contentHtml = rawHtml,
                senderName = m.safeString("imdisplayname", "Unknown"),
                senderId = senderMri,
                senderObjectId = senderObjId,
                timestamp = parseTimestamp(m.safeString("composetime", m.safeString("originalarrivaltime"))),
                isFromMe = senderObjId == myUserId,
                reactions = parseReactions(m.optJSONObject("properties")?.optJSONArray("emotions")),
            ))
        }
        return result.reversed()
    }

    /** Strip Teams API URL prefixes from sender ID */
    private fun stripSenderUrl(url: String): String {
        return url
            .removePrefix("https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/contacts/")
            .removePrefix("https://notifications.skype.net/v1/users/ME/contacts/")
    }

    suspend fun getMail(limit: Int = 25): List<MailMessage> {
        val url = "https://graph.microsoft.com/v1.0/me/messages?\$top=$limit&\$orderby=receivedDateTime desc"
        val body = graphGet(url)
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { parseMailMessage(arr.getJSONObject(it)) }
    }

    suspend fun getMailDetail(messageId: String): MailMessage {
        val body = graphGet("https://graph.microsoft.com/v1.0/me/messages/$messageId")
        return parseMailMessage(JSONObject(body))
    }

    private fun parseMailMessage(m: JSONObject): MailMessage {
        val from = m.optJSONObject("from")?.optJSONObject("emailAddress")
        val toArr = m.optJSONArray("toRecipients") ?: JSONArray()
        val toList = (0 until toArr.length()).map {
            toArr.getJSONObject(it).optJSONObject("emailAddress")?.optString("address", "") ?: ""
        }
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

    suspend fun getEvents(days: Int): List<CalendarEvent> {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        val start = now.toLocalDate().atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
        val end = now.toLocalDate().plusDays(days.toLong()).atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)

        val url = "https://graph.microsoft.com/v1.0/me/calendarView?startDateTime=$start&endDateTime=$end&\$orderby=start/dateTime&\$top=50"
        val body = graphGet(url)
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()

        return (0 until arr.length()).map { idx ->
            val e = arr.getJSONObject(idx)
            val organizer = e.optJSONObject("organizer")?.optJSONObject("emailAddress")
            val location: String? = e.optJSONObject("location")?.optString("displayName", "")

            val attendeesArr = e.optJSONArray("attendees") ?: JSONArray()
            val attendees = (0 until attendeesArr.length()).map { i ->
                val a = attendeesArr.getJSONObject(i)
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
                location = location?.ifEmpty { null },
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

    suspend fun getChannelMessages(teamId: String, channelId: String): List<ChannelMessage> {
        val url = "https://teams.microsoft.com/api/csa/emea/api/v3/teams/$teamId/channels/$channelId/messages"
        val body = authenticatedGet(url, SCOPE_CHATSVCAGG)
        val json = JSONObject(body)
        val arr = json.optJSONArray("messages") ?: json.optJSONArray("replyChains") ?: return emptyList()

        val result = mutableListOf<ChannelMessage>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val content = stripHtml(m.safeString("content"))
            if (content.isBlank()) continue

            result.add(ChannelMessage(
                id = m.optString("id", "$i"),
                content = content,
                senderName = m.safeString("imdisplayname", m.safeString("imDisplayName", "Unknown")),
                timestamp = parseTimestamp(m.safeString("composeTime", m.safeString("composetime"))),
                reactions = parseReactions(m.optJSONObject("properties")?.optJSONArray("emotions")),
            ))
        }
        return result
    }

    private fun parseTeamList(arr: JSONArray): List<Team> {
        return (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            val channelsArr = t.optJSONArray("channels") ?: JSONArray()
            Team(
                id = t.optString("id"),
                displayName = t.optString("displayName", "Team"),
                channels = (0 until channelsArr.length()).map { idx ->
                    val c = channelsArr.getJSONObject(idx)
                    Channel(id = c.optString("id"), displayName = c.optString("displayName", "Channel"))
                },
            )
        }
    }

    private fun parseReactions(emotions: JSONArray?): List<Reaction> {
        if (emotions == null) return emptyList()
        return (0 until emotions.length()).map { i ->
            val e = emotions.getJSONObject(i)
            Reaction(emoji = e.optString("key", ""), count = e.optJSONArray("users")?.length() ?: 0)
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
                try {
                    // Try epoch millis (some Teams APIs return numeric timestamps)
                    val millis = ts.toLong()
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                } catch (_: Exception) {
                    LocalDateTime.MIN
                }
            }
        }
    }

    /** Android's JSONObject.optString() returns "null" for JSON null values */
    private fun JSONObject.safeString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        return optString(key, fallback)
    }

    suspend fun sendMessage(conversationId: String, content: String) = withContext(Dispatchers.IO) {
        val token = getToken(SCOPE_IC3)
        val me = getMe()
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())
        val messageId = (Math.random() * Long.MAX_VALUE).toLong().toString()

        val body = JSONObject().apply {
            put("id", "-1")
            put("type", "Message")
            put("conversationid", conversationId)
            put("conversation_link", "blah/$conversationId")
            put("from", "8:orgid:${me.id}")
            put("composetime", now)
            put("originalarrivaltime", now)
            put("content", content)
            put("messagetype", "RichText/Html")
            put("contenttype", "Html")
            put("imdisplayname", me.displayName)
            put("clientmessageid", messageId)
            put("call_id", "")
            put("state", 0)
            put("version", "0")
            put("amsreferences", JSONArray())
            put("properties", JSONObject().apply {
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
            })
            put("post_type", "Standard")
            put("cross_post_channels", JSONArray())
        }

        val url = URL("https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations/$conversationId/messages")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.doOutput = true
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("Failed to send message (${conn.responseCode}): $err")
        }
    }

    private fun stripHtml(html: String): String {
        return html
            // Line breaks before stripping tags
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</?(div|p|li)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            // Strip remaining tags
            .replace(HTML_TAG_REGEX, "")
            // HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            // Clean up extra blank lines
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
