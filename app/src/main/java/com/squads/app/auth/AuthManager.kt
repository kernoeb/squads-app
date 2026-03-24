package com.squads.app.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences("squads_auth", Context.MODE_PRIVATE)

        private val _isAuthenticated = MutableStateFlow(prefs.contains("refresh_token"))
        val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

        private val _userName = MutableStateFlow(prefs.getString("user_name", null))
        val userName: StateFlow<String?> = _userName

        private val _deviceCodeState = MutableStateFlow<DeviceCodeState>(DeviceCodeState.Idle)
        val deviceCodeState: StateFlow<DeviceCodeState> = _deviceCodeState

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
                val url = URL(OAuthConfig.deviceCodeUrl())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("User-Agent", com.squads.app.data.USER_AGENT)
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(OAuthConfig.deviceCodeBody()) }

                val code = conn.responseCode
                val body =
                    if (code in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        throw Exception("Device code request failed ($code): $err")
                    }

                JSONObject(body)
            }

        /**
         * Poll the token endpoint until the user authorizes the device code.
         * Returns the refresh_token on success, null on timeout.
         */
        private suspend fun pollForToken(
            deviceCode: String,
            intervalSec: Int,
            maxAttempts: Int,
        ): String? {
            repeat(maxAttempts) {
                delay(intervalSec * 1000L)

                try {
                    val result = exchangeDeviceCode(deviceCode)
                    val refreshToken = result.optString("refresh_token", "")
                    if (refreshToken.isNotEmpty()) {
                        return refreshToken
                    }
                } catch (_: Exception) {
                    // Not yet authorized — keep polling
                }
            }
            return null
        }

        /**
         * Try to exchange the device code for tokens (same as CLI).
         */
        private suspend fun exchangeDeviceCode(deviceCode: String): JSONObject =
            withContext(Dispatchers.IO) {
                val url = URL(OAuthConfig.tokenUrl())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Origin", "https://teams.microsoft.com")
                conn.setRequestProperty("User-Agent", com.squads.app.data.USER_AGENT)
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(OAuthConfig.tokenPollBody(deviceCode)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    throw Exception("Pending ($code)")
                }

                val body = conn.inputStream.bufferedReader().readText()
                JSONObject(body)
            }

        fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

        /** Mock login for development — simulates a successful auth with demo data. */
        fun mockLogin() {
            prefs
                .edit()
                .putString("refresh_token", "mock_refresh_token")
                .putString("user_name", "You")
                .apply()
            _isAuthenticated.value = true
            _userName.value = "You"
            _deviceCodeState.value = DeviceCodeState.Idle
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
    }
