package io.github.biliaudioplayer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SessionStore {
    fun read(): String
    fun write(cookie: String)
    fun clear()
}

/** Only ciphertext is persisted. AndroidKeyStore key material never leaves the keystore. */
internal class EncryptedSessionStore(
    context: Context,
    preferenceName: String = "bili_session",
    private val alias: String = "biliaudioplayer.session.v1",
) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    @Synchronized
    override fun read(): String {
        val value = preferences.getString("encrypted_cookie", null) ?: return ""
        return try {
            val pieces = value.split(':', limit = 2)
            if (pieces.size != 2) throw IllegalStateException()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            ""
        }
    }

    @Synchronized
    override fun write(cookie: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(cipher.doFinal(cookie.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            if (!preferences.edit().putString("encrypted_cookie", "$iv:$encrypted").commit()) {
                throw IllegalStateException()
            }
        } catch (_: Exception) {
            throw BiliApiException(message = "无法安全保存登录状态，请重试")
        }
    }

    @Synchronized
    override fun clear() {
        preferences.edit().remove("encrypted_cookie").commit()
    }
}
