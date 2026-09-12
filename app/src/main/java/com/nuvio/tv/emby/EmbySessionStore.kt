package com.nuvio.tv.emby

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EmbySessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("luma_emby_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "luma_emby_access_token"

    fun save(session: EmbySession) {
        val (iv, encrypted) = encrypt(session.accessToken)
        prefs.edit()
            .putString("server", session.serverUrl)
            .putString("user_id", session.userId)
            .putString("username", session.username)
            .putString("token_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): EmbySession? = runCatching {
        val server = prefs.getString("server", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        val username = prefs.getString("username", "").orEmpty()
        val ivRaw = prefs.getString("token_iv", null) ?: return null
        val tokenRaw = prefs.getString("token", null) ?: return null
        val iv = Base64.decode(ivRaw, Base64.NO_WRAP)
        val token = Base64.decode(tokenRaw, Base64.NO_WRAP)
        EmbySession(server, userId, decrypt(iv, token), username)
    }.getOrNull()

    fun clear() = prefs.edit().clear().apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv to cipher.doFinal(value.toByteArray())
    }

    private fun decrypt(iv: ByteArray, value: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(value).decodeToString()
    }
}
