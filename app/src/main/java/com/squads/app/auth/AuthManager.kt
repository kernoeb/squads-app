package com.squads.app.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
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

        private val _isAuthenticated = MutableStateFlow(prefs.contains("refresh_token"))
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
                _deviceCodeState.value = DeviceCodeState.Error(e.message ?: "Failed to get device code")
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
                Intent(Intent.ACTION_VIEW, Uri.parse(state.verificationUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            activity.startActivity(intent)

            _deviceCodeState.value = DeviceCodeState.Polling(state.userCode)

            try {
                val refreshToken = pollForToken(deviceCode, pendingInterval, maxAttempts = 60)

                if (refreshToken != null) {
                    prefs
                        .edit()
                        .putString("refresh_token", refreshToken)
                        .putString("user_name", "User")
                        .apply()

                    _isAuthenticated.value = true
                    _userName.value = "User"
                    _deviceCodeState.value = DeviceCodeState.Success
                } else {
                    _deviceCodeState.value = DeviceCodeState.Error("Login timed out. Please try again.")
                }
            } catch (e: Exception) {
                _deviceCodeState.value = DeviceCodeState.Error(e.message ?: "Authentication failed")
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
                        throw Exception("Device code request failed (${response.code}): $body")
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
                    // Not yet authorized — keep polling
                    Log.d("AuthManager", "Device code poll: ${e.message}")
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
                        throw Exception("Pending (${response.code})")
                    }
                    JSONObject(body)
                }
            }

        fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

        val isDemoMode: Boolean
            get() = getRefreshToken() == MOCK_REFRESH_TOKEN

        /** Mock login for development — simulates a successful auth with demo data. */
        fun mockLogin() {
            prefs
                .edit()
                .putString("refresh_token", MOCK_REFRESH_TOKEN)
                .putString("user_name", "You")
                .apply()
            _isAuthenticated.value = true
            _userName.value = "You"
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        fun updateUserName(name: String) {
            prefs.edit().putString("user_name", name).apply()
            _userName.value = name
        }

        fun logout() {
            prefs.edit().clear().apply()
            _isAuthenticated.value = false
            _userName.value = null
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        fun resetState() {
            _deviceCodeState.value = DeviceCodeState.Idle
        }

        companion object {
            const val MOCK_REFRESH_TOKEN = "mock_refresh_token"
        }
    }
