package com.squads.app.data

import android.util.Log
import com.squads.app.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Trouter"

@Singleton
class TrouterClient
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        private val api: TeamsApiClient,
        private val networkMonitor: NetworkMonitor,
        private val authManager: AuthManager,
    ) {
        private val isDemoMode: Boolean
            get() = authManager.isDemoMode

        sealed class Event {
            data class NewMessage(
                val chatId: String,
                val messageId: String,
                val senderName: String,
            ) : Event()

            data class Typing(
                val chatId: String,
                val senderName: String,
            ) : Event()

            data class PresenceChanged(
                val userId: String,
                val availability: String,
            ) : Event()

            data class ReadHorizonUpdate(
                val chatId: String,
            ) : Event()

            data object Connected : Event()

            data object Disconnected : Event()
        }

        private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
        val events: SharedFlow<Event> = _events

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        @Volatile
        private var ws: WebSocket? = null
        private var epid = UUID.randomUUID().toString()

        @Volatile
        private var reconnectUrl: String? = null

        @Volatile
        private var trouterSurl: String? = null

        @Volatile
        private var scope: CoroutineScope? = null

        @Volatile
        private var backoffMs = 1000L
        private var reconnectJob: Job? = null
        private var heartbeatJob: Job? = null

        private val wsClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }

        fun start() {
            if (isDemoMode || scope != null) return
            reconnectUrl = null
            backoffMs = 1000L
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            connect()
        }

        fun stop() {
            heartbeatJob?.cancel()
            reconnectJob?.cancel()
            reconnectJob = null
            ws?.close(1000, null)
            ws = null
            trouterSurl = null
            _isConnected.value = false
            scope?.cancel()
            scope = null
        }

        private fun connect() {
            val baseUrl = reconnectUrl ?: "wss://go-eu.trouter.teams.microsoft.com/v4/c"
            val tc =
                JSONObject().apply {
                    put("cv", "2026.07.01.1")
                    put("ua", "TeamsCDL")
                    put("hr", "")
                    put("v", "0.1.0")
                }
            val params =
                buildString {
                    append("tc=").append(URLEncoder.encode(tc.toString(), "UTF-8"))
                    append("&timeout=40")
                    append("&epid=").append(epid)
                    append("&ccid=")
                    append("&cor_id=").append(UUID.randomUUID())
                    append("&con_num=").append(System.currentTimeMillis()).append("_0")
                }

            val request =
                Request
                    .Builder()
                    .url("$baseUrl?$params")
                    .header("Origin", "https://teams.cloud.microsoft")
                    .build()

            Log.d(TAG, "Connecting to $baseUrl")
            wsClient.newWebSocket(request, TrouterListener())
        }

        private fun scheduleReconnect() {
            reconnectJob?.cancel()
            if (backoffMs >= 2000L) reconnectUrl = null
            reconnectJob =
                scope?.launch {
                    delay(backoffMs)
                    // Wait for network to be available
                    while (!networkMonitor.isOnline.value) delay(1000)
                    backoffMs = (backoffMs * 2).coerceAtMost(60_000)
                    connect()
                }
        }

        // ─── Frame parsing (Socket.IO v0.9) ───────────────────────

        private fun frameData(frame: String): String {
            var colons = 0
            for (i in frame.indices) {
                if (frame[i] == ':' && ++colons == 3) return frame.substring(i + 1)
            }
            return ""
        }

        private fun frameSeq(frame: String): String? {
            val parts = frame.split(":", limit = 3)
            return if (parts.size >= 2) parts[1].replace("+", "") else null
        }

        private fun needsAck(frame: String): Boolean {
            val parts = frame.split(":", limit = 3)
            return parts.size >= 2 && "+" in parts[1]
        }

        // ─── WebSocket listener ────────────────────────────────────

        private inner class TrouterListener : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                Log.d(TAG, "WebSocket opened")
                ws = webSocket
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                when {
                    // Server connected → authenticate
                    text.startsWith("1::") -> authenticate(webSocket)

                    // trouter.connected → extract surl, register
                    "trouter.connected" in text -> handleConnected(webSocket, text)

                    // Ping → pong
                    "\"name\":\"ping\"" in text -> {
                        frameSeq(text)?.let { webSocket.send("6:::$it+[\"pong\"]") }
                    }

                    // Notification payload (type 3)
                    text.startsWith("3:::") -> handleNotification(webSocket, text.substring(4))

                    // message_loss → ACK
                    "message_loss" in text -> {
                        if (needsAck(text)) {
                            webSocket.send("6:::${frameSeq(text)}+[]")
                        }
                    }
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                if (ws != null && ws !== webSocket) return
                val isDns = t is java.net.UnknownHostException
                Log.e(TAG, "WebSocket failure: ${t.javaClass.simpleName}: ${t.message}")
                // DNS failure → wait longer before retrying
                if (isDns) backoffMs = backoffMs.coerceAtLeast(5000L)
                handleDisconnect(shouldReconnect = true)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                if (ws != null && ws !== webSocket) return
                handleDisconnect(shouldReconnect = code != 1000)
            }
        }

        private fun handleDisconnect(shouldReconnect: Boolean) {
            heartbeatJob?.cancel()
            ws = null
            if (_isConnected.compareAndSet(expect = true, update = false)) {
                Log.d(TAG, "Disconnected (reconnect=$shouldReconnect)")
                _events.tryEmit(Event.Disconnected)
            }
            if (shouldReconnect) scheduleReconnect()
        }

        // ─── Auth ──────────────────────────────────────────────────

        private fun authenticate(webSocket: WebSocket) {
            scope?.launch {
                try {
                    val token = api.getIc3Token()
                    val auth =
                        JSONObject().apply {
                            put("name", "user.authenticate")
                            put(
                                "args",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put(
                                            "headers",
                                            JSONObject().apply {
                                                put("Authorization", "Bearer $token")
                                                put("X-MS-Migration", "True")
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                    webSocket.send("5:::$auth")
                    Log.d(TAG, "Auth sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Auth failed", e)
                    webSocket.close(1000, null)
                }
            }
        }

        private fun handleConnected(
            webSocket: WebSocket,
            frame: String,
        ) {
            if (needsAck(frame)) {
                webSocket.send("6:::${frameSeq(frame)}+[]")
            }

            val data = JSONObject(frameData(frame))
            val args = data.getJSONArray("args").getJSONObject(0)
            val surl = args.getString("surl")
            trouterSurl = surl
            reconnectUrl = args.optString("reconnectUrl").ifEmpty { null }

            scope?.launch {
                try {
                    register(surl)
                    _isConnected.value = true
                    backoffMs = 1000L
                    _events.emit(Event.Connected)
                    Log.d(TAG, "Connected & registered")
                    startHeartbeat(webSocket)
                } catch (e: Exception) {
                    Log.e(TAG, "Register failed", e)
                    webSocket.close(1000, null)
                }
            }
        }

        // ─── Heartbeat ─────────────────────────────────────────────

        private fun startHeartbeat(webSocket: WebSocket) {
            heartbeatJob?.cancel()
            heartbeatJob =
                scope?.launch {
                    while (true) {
                        delay(15_000)
                        try {
                            webSocket.send("2::")
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
        }

        // ─── Notification handling ─────────────────────────────────

        private fun handleNotification(
            webSocket: WebSocket,
            json: String,
        ) {
            try {
                val payload = JSONObject(json)

                // ACK
                val ack =
                    JSONObject().apply {
                        put("id", payload.getInt("id"))
                        put("status", 200)
                        put("headers", JSONObject())
                        put("body", "")
                    }
                webSocket.send("3:::$ack")

                // Presence notification
                val url = payload.optString("url", "")
                if ("unifiedPresenceService" in url) {
                    handlePresenceNotification(payload.optString("body", ""))
                    return
                }

                val body = JSONObject(payload.optString("body", "{}"))
                val resourceType = body.optString("resourceType", "")
                val resource = body.optJSONObject("resource") ?: return
                val messageType = resource.optString("messagetype", "")

                val convLink = resource.optString("conversationLink", "")
                val chatId = URLDecoder.decode(convLink.substringAfterLast("/"), "UTF-8")
                if (chatId.isEmpty()) return

                when {
                    messageType == "ThreadActivity/MemberConsumptionHorizonUpdate" -> {
                        _events.tryEmit(Event.ReadHorizonUpdate(chatId))
                    }
                    messageType == "Control/Typing" -> {
                        val sender = resource.optString("imdisplayname", "")
                        if (sender.isNotEmpty()) {
                            _events.tryEmit(Event.Typing(chatId, sender))
                        }
                    }
                    resourceType == "NewMessage" && messageType in setOf("RichText/Html", "Text") -> {
                        _events.tryEmit(
                            Event.NewMessage(
                                chatId = chatId,
                                messageId = resource.optString("id", ""),
                                senderName = resource.optString("imdisplayname", ""),
                            ),
                        )
                    }
                    resourceType == "MessageUpdate" -> {
                        _events.tryEmit(
                            Event.NewMessage(
                                chatId = chatId,
                                messageId = resource.optString("id", ""),
                                senderName = "",
                            ),
                        )
                    }
                    else -> Log.d(TAG, "Unhandled notification: resourceType=$resourceType body=${body.toString().take(500)}")
                }
            } catch (_: Exception) {
            }
        }

        private fun handlePresenceNotification(bodyStr: String) {
            try {
                val body = JSONObject(bodyStr)
                val presenceArr = body.optJSONArray("presence") ?: return
                for (i in 0 until presenceArr.length()) {
                    val item = presenceArr.getJSONObject(i)
                    val mri = item.optString("mri", "")
                    val userId = mri.removePrefix("8:orgid:")
                    val presence = item.optJSONObject("presence") ?: continue
                    val availability = presence.optString("availability", "")
                    if (userId.isNotEmpty() && availability.isNotEmpty()) {
                        _events.tryEmit(Event.PresenceChanged(userId, availability))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Presence parse error", e)
            }
        }

        // ─── Presence subscription ────────────────────────────────

        suspend fun subscribePresence(userIds: List<String>) {
            val surl = trouterSurl ?: return
            if (userIds.isEmpty()) return
            try {
                api.subscribePresence(epid, "$surl/unifiedPresenceService", userIds)
                Log.d(TAG, "Subscribed presence for ${userIds.size} users")
            } catch (e: Exception) {
                Log.e(TAG, "Presence subscription failed", e)
            }
        }

        // ─── Registrar ─────────────────────────────────────────────

        private suspend fun register(surl: String) =
            withContext(Dispatchers.IO) {
                val token = api.getIc3Token()
                val body =
                    JSONObject().apply {
                        put(
                            "clientDescription",
                            JSONObject().apply {
                                put("appId", "TeamsCDLWebWorker")
                                put("aesKey", "")
                                put("languageId", "en-US")
                                put("platform", "chrome")
                                put("templateKey", "TeamsCDLWebWorker_2.6")
                                put("platformUIVersion", "1415/26022704215")
                            },
                        )
                        put("registrationId", epid)
                        put("nodeId", "")
                        put(
                            "transports",
                            JSONObject().apply {
                                put(
                                    "TROUTER",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("context", "")
                                            put("path", surl)
                                            put("ttl", 3600)
                                        },
                                    ),
                                )
                            },
                        )
                    }

                val request =
                    Request
                        .Builder()
                        .url("https://teams.cloud.microsoft/registrar/prod/V2/registrations")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", "Bearer $token")
                        .header("X-MS-Migration", "True")
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Registrar ${response.code}")
                    }
                }
            }
    }
