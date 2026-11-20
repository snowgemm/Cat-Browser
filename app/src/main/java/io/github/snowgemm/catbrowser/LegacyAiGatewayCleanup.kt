package io.github.snowgemm.catbrowser

import android.content.Context
import java.security.KeyStore

/**
 * Removes credentials left by the retired user-configured Vercel/OpenAI Android client.
 * The active Supabase session uses a different preference file and Keystore alias.
 */
internal object LegacyAiGatewayCleanup {
    fun clear(context: Context) {
        context.deleteSharedPreferences(LEGACY_PREFERENCES)

        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) {
                keyStore.deleteEntry(LEGACY_KEY_ALIAS)
            }
        }
    }

    private const val LEGACY_PREFERENCES = "cat_ai_gateway"
    private const val LEGACY_KEY_ALIAS = "cat_ai_gateway_access_token"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
}
