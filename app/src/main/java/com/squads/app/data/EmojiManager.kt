package com.squads.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmojiManager"
private const val EMOJI_METADATA_URL =
    "https://statics.teams.cdn.office.net/evergreen-assets/personal-expressions/v1/metadata/a098bcb732fd7dd80ce11c12ad15767f/en-us.json"

@Singleton
class EmojiManager
    @Inject
    constructor(
        private val context: Context,
        private val httpClient: OkHttpClient,
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
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize emoji mapping", e)
                }
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
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load emoji cache", e)
                false
            }
        }

        private suspend fun downloadAndCache(): Boolean =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(EMOJI_METADATA_URL)
                        .header("User-Agent", USER_AGENT)
                        .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext false

                val data = JSONObject(response.body.string())
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

                try {
                    cacheFile().writeText(result.toString())
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to write emoji cache", e)
                }
                mapping.isNotEmpty()
            }

        private fun cacheFile(): File = File(context.cacheDir, "teams-emoji.json")
    }
