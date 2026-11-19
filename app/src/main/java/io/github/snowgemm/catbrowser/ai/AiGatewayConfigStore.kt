package io.github.snowgemm.catbrowser.ai

import android.content.Context
import java.net.URI
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiGatewayConfig(
    val baseUrl: String,
    val accessToken: String
)

internal object AiGatewayConfigPolicy {
    fun normalizeBaseUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed.length > 240) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            (uri.port != -1 && uri.port !in 1..65535)
        ) return null
        return trimmed
    }

    fun isValidAccessToken(value: String): Boolean =
        value.length in 24..256 && value.none(Char::isWhitespace)
}

/**
 * Stores only the user's own gateway access token, encrypted with an Android
 * Keystore AES-GCM key. The OpenAI API key never belongs in the mobile app.
 */
class AiGatewayConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "cat_ai_gateway",
        Context.MODE_PRIVATE
    )

    fun load(): AiGatewayConfig? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null) ?: return null
        val encryptedToken = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val token = runCatching { decrypt(encryptedToken) }.getOrNull()
        if (token.isNullOrBlank()) {
            clear()
            return null
        }
        return AiGatewayConfig(baseUrl, token)
    }

    fun isConfigured(): Boolean = load() != null

    fun savedBaseUrl(): String? = preferences.getString(KEY_BASE_URL, null)

    fun updateBaseUrl(baseUrl: String): Boolean {
        val currentToken = load()?.accessToken ?: return false
        return save(baseUrl, currentToken)
    }

    fun save(baseUrl: String, accessToken: String): Boolean {
        val normalized = AiGatewayConfigPolicy.normalizeBaseUrl(baseUrl) ?: return false
        val token = accessToken.trim()
        if (!AiGatewayConfigPolicy.isValidAccessToken(token)) return false
        val encrypted = runCatching { encrypt(token) }.getOrNull() ?: return false
        preferences.edit()
            .putString(KEY_BASE_URL, normalized)
            .putString(KEY_ACCESS_TOKEN, encrypted)
            .apply()
        return true
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val payload = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_BASE_URL = "gateway_base_url"
        const val KEY_ACCESS_TOKEN = "gateway_access_token"
        const val KEY_ALIAS = "cat_ai_gateway_access_token"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
