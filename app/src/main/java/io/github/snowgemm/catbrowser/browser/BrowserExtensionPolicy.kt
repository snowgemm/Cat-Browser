package io.github.snowgemm.catbrowser.browser

import java.net.URI

internal class ReplaceableDecision<T> {
    private var pending: ((T) -> Unit)? = null

    fun replace(next: (T) -> Unit, rejectedValue: T) {
        val previous = pending
        pending = next
        previous?.let { callback -> runCatching { callback(rejectedValue) } }
    }

    fun resolve(value: T) {
        val callback = pending
        pending = null
        callback?.invoke(value)
    }
}

internal object ExtensionSourcePolicy {
    fun isTrustedAmoDownloadUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.equals("addons.mozilla.org", ignoreCase = true) == true &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443)
    }
}
