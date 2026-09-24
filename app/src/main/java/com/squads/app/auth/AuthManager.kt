package com.squads.app.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.squads.app.data.HttpException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication state for device code login flow.
 */
sealed class DeviceCodeState {
    data object Idle : DeviceCodeState()

    /** Code ready — user needs to copy it then open the browser */
    data class CodeReady(
        val userCode: String,
        val verificationUrl: String,
    ) : DeviceCodeState()

    /** Browser opened, polling for authorization */
    data class Polling(
        val userCode: String,
    ) : DeviceCodeState()

    data object Success : DeviceCodeState()

    data class Error(
        val message: String,
    ) : DeviceCodeState()
}

@Singleton
class AuthManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val httpClient: OkHttpClient,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences("squads_auth", Context.MODE_PRIVATE)

        private val tokenStore = TokenStore(prefs, KeystoreTokenCipher())

        private val _isAuthenticated = MutableStateFlow(tokenStore.hasStoredToken())
        val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

        private val _userName = MutableStateFlow(prefs.getString("user_name", null))
        val userName: StateFlow<String?> = _userName

        private val _deviceCodeState = MutableStateFlow<DeviceCodeState>(DeviceCodeState.Idle)
        val deviceCodeState: StateFlow<DeviceCodeState> = _deviceCodeState

        /** Emits each time a new session starts (login after logout). */
        fun onSessionStart(): Flow<Boolean> = isAuthenticated.drop(1).filter { it }

        // Stored between steps so openBrowserAndPoll can use it
        private var pendingDeviceCode: String? = null
        private var pendingInterval: Int = 5

        /**
         * Step 1: Request a device code and show it to the user.
         * Does NOT open the browser yet — the user copies the code first.
         */
        suspend fun requestDeviceCodeLogin() {
            _deviceCodeState.value = DeviceCodeState.Idle

            try {
                val deviceCodeInfo = requestDeviceCode()
                val userCode = deviceCodeInfo.getString("user_code")
                pendingDeviceCode = deviceCodeInfo.getString("device_code")
                pendingInterval = deviceCodeInfo.optInt("interval", 5)
                val verificationUrl = deviceCodeInfo.optString("verification_url", "https://microsoft.com/devicelogin")

                _deviceCodeState.value = DeviceCodeState.CodeReady(userCode, verificationUrl)
            } catch (e: Exception) {
                Log.w(TAG, "Device code request failed: ${logReason(e)}")
                _deviceCodeState.value =
                    DeviceCodeState.Error(loginErrorMessage(e, "Failed to get device code"))
            }
        }

        /**
         * Step 2: User has copied the code — open browser and start polling.
         */
        suspend fun openBrowserAndPoll(activity: android.app.Activity) {
            val state = _deviceCodeState.value
            if (state !is DeviceCodeState.CodeReady) return
            val deviceCode = pendingDeviceCode ?: return

            // Open browser
            val intent =
                Intent(Intent.ACTION_VIEW, state.verificationUrl.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            activity.startActivity(intent)

            _deviceCodeState.value = DeviceCodeState.Polling(state.userCode)

            try {
                val refreshToken = pollForToken(deviceCode, pendingInterval, maxAttempts = 60)

                if (refreshToken != null) {
                    tokenStore.set(refreshToken)
                    prefs.edit { putString("user_name", "User") }

                    _isAuthenticated.value = true
                    _userName.value = "User"
                    _deviceCodeState.value = DeviceCodeState.Success
                } else {
                    _deviceCodeState.value = DeviceCodeState.Error("Login timed out. Please try again.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Device code login failed: ${logReason(e)}")
                _deviceCodeState.value =
                    DeviceCodeState.Error(loginErrorMessage(e, "Authentication failed"))
            }
        }

        /**
         * Request a device code from Microsoft (same endpoint as CLI).
         */
        private suspend fun requestDeviceCode(): JSONObject =
            withContext(Dispatchers.IO) {
                val requestBody =
                    OAuthConfig
                        .deviceCodeBody()
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url(OAuthConfig.deviceCodeUrl())
                        .post(requestBody)
                        .header("User-Agent", com.squads.app.data.USER_AGENT)
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw HttpException.fromResponse(response.code, body)
                    }
                    JSONObject(body)
                }
            }

        private suspend fun pollForToken(
            deviceCode: String,
            intervalSec: Int,
            maxAttempts: Int,
        ): String? {
            repeat(maxAttempts) {
                try {
                    val result = exchangeDeviceCode(deviceCode)
                    val refreshToken = result.optString("refresh_token", "")
                    if (refreshToken.isNotEmpty()) {
                        return refreshToken
                    }
                } catch (e: Exception) {
                    // Not yet authorized — keep polling. Log the summary, never the body.
                    Log.d(TAG, "Device code poll: ${logReason(e)}")
                }
                delay(intervalSec * 1000L)
            }
            return null
        }

        private suspend fun exchangeDeviceCode(deviceCode: String): JSONObject =
            withContext(Dispatchers.IO) {
                val requestBody =
                    OAuthConfig
                        .tokenPollBody(deviceCode)
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url(OAuthConfig.tokenUrl())
                        .post(requestBody)
                        .header("Origin", "https://teams.microsoft.com")
                        .header("User-Agent", com.squads.app.data.USER_AGENT)
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        // Typically authorization_pending; errorCode carries it for the poll log.
                        throw HttpException.fromResponse(response.code, body)
                    }
                    JSONObject(body)
                }
            }

        /**
         * Decrypted refresh token, cached in memory after the first read. If the stored token
         * cannot be decrypted it is cleared and the session is marked as logged out.
         */
        fun getRefreshToken(): String? {
            val token = tokenStore.get()
            if (token == null && _isAuthenticated.value) {
                _isAuthenticated.value = false
            }
            return token
        }

        val isDemoMode: Boolean
            get() = getRefreshToken() == MOCK_REFRESH_TOKEN

        /** Mock login for development — simulates a successful auth with demo data. */
        fun mockLogin() {
            tokenStore.set(MOCK_REFRESH_TOKEN)
            prefs.edit { putString("user_name", "You") }
            _isAuthenticated.value = true
            _userName.value = "You"
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        fun updateUserName(name: String) {
            prefs.edit { putString("user_name", name) }
            _userName.value = name
        }

        fun logout() {
            tokenStore.clear()
            prefs.edit { clear() }
            _isAuthenticated.value = false
            _userName.value = null
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        fun resetState() {
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        companion object {
            const val MOCK_REFRESH_TOKEN = "mock_refresh_token"
            private const val TAG = "AuthManager"
            private const val MAX_UI_ERROR_CHARS = 120

            /**
             * Short, user-oriented error text for [DeviceCodeState.Error]. Never includes the
             * raw response body or a raw exception message.
             */
            internal fun loginErrorMessage(
                e: Exception,
                fallback: String,
            ): String {
                val text =
                    when (e) {
                        is HttpException -> "Login failed (${e.summary})"
                        is IOException -> "Login failed: network error. Check your connection."
                        else -> fallback
                    }
                return text.take(MAX_UI_ERROR_CHARS)
            }

            /**
             * Bounded log text. Other exception messages (e.g. JSONException) can embed the whole
             * response body, so only the class name is logged for them.
             */
            private fun logReason(e: Exception): String =
                (e as? HttpException)?.summary ?: e.javaClass.simpleName
        }
    }
