package com.squads.app.auth

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the OAuth refresh token encrypted at rest.
 *
 * The ciphertext lives in [prefs] under [KEY_ENCRYPTED]. A plaintext value left under
 * [KEY_LEGACY] by older app versions is migrated on first read. The decrypted token is
 * cached in memory so repeated reads (every access-token refresh) skip the Keystore.
 *
 * Failures never surface token material: if the Keystore is unavailable or decryption
 * fails, the stored value is cleared and the user is treated as logged out.
 */
class TokenStore(
    private val prefs: SharedPreferences,
    private val cipher: TokenCipher,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val lock = Any()

    @Volatile private var loaded = false

    @Volatile private var cached: String? = null

    /** True if a token (encrypted or legacy) is persisted. Does not touch the Keystore. */
    fun hasStoredToken(): Boolean = prefs.contains(KEY_ENCRYPTED) || prefs.contains(KEY_LEGACY)

    fun get(): String? {
        if (loaded) return cached
        synchronized(lock) {
            if (!loaded) {
                cached = load()
                loaded = true
            }
            return cached
        }
    }

    /**
     * Encrypts and persists [token], and caches it in memory. Returns false if encryption
     * failed; the token is then kept for this process only and nothing is written to disk.
     */
    fun set(token: String): Boolean =
        synchronized(lock) {
            cached = token
            loaded = true
            val encrypted =
                try {
                    cipher.encrypt(token)
                } catch (e: Exception) {
                    warn("Could not encrypt refresh token (${e.javaClass.simpleName})")
                    null
                }
            prefs.edit {
                remove(KEY_LEGACY)
                if (encrypted != null) {
                    putString(KEY_ENCRYPTED, encrypted)
                } else {
                    remove(KEY_ENCRYPTED)
                }
            }
            encrypted != null
        }

    fun clear() {
        synchronized(lock) {
            cached = null
            loaded = true
            removeStored()
        }
    }

    private fun load(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED, null)
        if (encrypted != null) {
            return try {
                cipher.decrypt(encrypted)
            } catch (e: Exception) {
                warn("Stored refresh token unreadable (${e.javaClass.simpleName}), clearing")
                removeStored()
                null
            }
        }

        val legacy = prefs.getString(KEY_LEGACY, null) ?: return null
        return try {
            val migrated = cipher.encrypt(legacy)
            prefs.edit {
                putString(KEY_ENCRYPTED, migrated)
                remove(KEY_LEGACY)
            }
            legacy
        } catch (e: Exception) {
            warn("Could not migrate refresh token (${e.javaClass.simpleName}), clearing")
            removeStored()
            null
        }
    }

    private fun removeStored() {
        prefs.edit {
            remove(KEY_ENCRYPTED)
            remove(KEY_LEGACY)
        }
    }

    companion object {
        const val KEY_ENCRYPTED = "refresh_token_enc"
        const val KEY_LEGACY = "refresh_token"
        private const val TAG = "TokenStore"
    }
}
