package com.squads.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val EMOJI_METADATA_URL =
    "https://statics.teams.cdn.office.net/evergreen-assets/personal-expressions/v1/metadata/a098bcb732fd7dd80ce11c12ad15767f/en-us.json"

@Singleton
class EmojiManager @Inject constructor(
    private val context: Context,
) {
    private val mapping = ConcurrentHashMap<String, String>()
    private val initMutex = Mutex()
    @Volatile private var initialized = false

    suspend fun init() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            try {
                loadFromCache() || downloadAndCache()
            } catch (_: Exception) { }
            initialized = true
        }
    }

    fun getEmoji(key: String): String = mapping[key.lowercase()] ?: key

    private fun loadFromCache(): Boolean {
        val file = cacheFile()
        if (!file.exists()) return false
        return try {
            val json = JSONObject(file.readText())
            json.keys().forEach { mapping[it] = json.getString(it) }
            mapping.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun downloadAndCache(): Boolean = withContext(Dispatchers.IO) {
        val conn = URL(EMOJI_METADATA_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", USER_AGENT)

        if (conn.responseCode != 200) return@withContext false

        val data = JSONObject(conn.inputStream.bufferedReader().readText())
        val categories = data.optJSONArray("categories") ?: return@withContext false

        val result = JSONObject()
        for (i in 0 until categories.length()) {
            val emoticons = categories.getJSONObject(i).optJSONArray("emoticons") ?: continue
            for (j in 0 until emoticons.length()) {
                val emo = emoticons.getJSONObject(j)
                val id = emo.optString("id", "")
                val unicode = emo.optString("unicode", "")
                if (id.isNotEmpty() && unicode.isNotEmpty()) {
                    mapping.putIfAbsent(id, unicode)
                    result.put(id, unicode)
                }
            }
        }

        try { cacheFile().writeText(result.toString()) } catch (_: Exception) { }
        mapping.isNotEmpty()
    }

    private fun cacheFile(): File = File(context.cacheDir, "teams-emoji.json")
}
