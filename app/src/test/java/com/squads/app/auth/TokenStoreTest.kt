package com.squads.app.auth

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenStoreTest {
    private val prefs = FakeSharedPreferences()
    private val cipher = FakeCipher()
    private val warnings = mutableListOf<String>()

    private fun store() = TokenStore(prefs, cipher) { warnings += it }

    @Test
    fun `get returns null when nothing is stored`() {
        val store = store()
        assertFalse(store.hasStoredToken())
        assertNull(store.get())
    }

    @Test
    fun `legacy plaintext token is migrated on first read`() {
        prefs.values[TokenStore.KEY_LEGACY] = SECRET
        val store = store()

        assertTrue(store.hasStoredToken())
        assertEquals(SECRET, store.get())
        assertFalse(prefs.values.containsKey(TokenStore.KEY_LEGACY))
        assertEquals(cipher.encrypt(SECRET), prefs.values[TokenStore.KEY_ENCRYPTED])
    }

    @Test
    fun `migrated token is readable by a fresh store`() {
        prefs.values[TokenStore.KEY_LEGACY] = SECRET
        store().get()

        assertEquals(SECRET, store().get())
    }

    @Test
    fun `migration failure clears storage and treats user as logged out`() {
        prefs.values[TokenStore.KEY_LEGACY] = SECRET
        cipher.failEncrypt = true
        val store = store()

        assertNull(store.get())
        assertFalse(store.hasStoredToken())
        assertWarningsDoNotLeak()
    }

    @Test
    fun `decrypt failure clears storage and treats user as logged out`() {
        prefs.values[TokenStore.KEY_ENCRYPTED] = cipher.encrypt(SECRET)
        cipher.failDecrypt = true
        val store = store()

        assertNull(store.get())
        assertFalse(store.hasStoredToken())
        assertEquals(1, warnings.size)
        assertWarningsDoNotLeak()
    }

    @Test
    fun `decrypted token is cached after first read`() {
        prefs.values[TokenStore.KEY_ENCRYPTED] = cipher.encrypt(SECRET)
        val store = store()

        repeat(5) { assertEquals(SECRET, store.get()) }
        assertEquals(1, cipher.decryptCalls)
    }

    @Test
    fun `set persists only ciphertext and drops legacy key`() {
        prefs.values[TokenStore.KEY_LEGACY] = "old"
        val store = store()

        assertTrue(store.set(SECRET))
        assertEquals(SECRET, store.get())
        assertFalse(prefs.values.containsKey(TokenStore.KEY_LEGACY))
        assertFalse(prefs.values.values.any { it == SECRET })
        assertEquals(SECRET, store().get())
    }

    @Test
    fun `set keeps token in memory only when encryption fails`() {
        cipher.failEncrypt = true
        val store = store()

        assertFalse(store.set(SECRET))
        assertEquals(SECRET, store.get())
        assertFalse(store.hasStoredToken())
        assertWarningsDoNotLeak()
    }

    @Test
    fun `demo sentinel round-trips through the store`() {
        store().set(AuthManager.MOCK_REFRESH_TOKEN)

        assertEquals(AuthManager.MOCK_REFRESH_TOKEN, store().get())
    }

    @Test
    fun `clear removes stored and cached token`() {
        val store = store()
        store.set(SECRET)

        store.clear()

        assertNull(store.get())
        assertFalse(store.hasStoredToken())
    }

    private fun assertWarningsDoNotLeak() {
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.none { it.contains(SECRET) })
    }

    private companion object {
        const val SECRET = "secret-refresh-token"
    }
}

/** Reversible, obviously-not-crypto cipher for exercising [TokenStore] logic. */
private class FakeCipher : TokenCipher {
    var failEncrypt = false
    var failDecrypt = false
    var decryptCalls = 0

    override fun encrypt(plaintext: String): String {
        if (failEncrypt) throw IllegalStateException("keystore unavailable")
        return "fake:" + plaintext.reversed()
    }

    override fun decrypt(encoded: String): String {
        decryptCalls++
        if (failDecrypt) throw IllegalStateException("key invalidated")
        return encoded.removePrefix("fake:").reversed()
    }
}

/** In-memory SharedPreferences supporting only what [TokenStore] uses. */
private class FakeSharedPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = throw UnsupportedOperationException()

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = throw UnsupportedOperationException()

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = throw UnsupportedOperationException()

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = throw UnsupportedOperationException()

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = throw UnsupportedOperationException()

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            puts[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            // Simplified: clear, then removals, then puts. TokenStore never removes and puts
            // the same key in one edit, so the platform's last-op-wins nuance is irrelevant.
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            puts.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
