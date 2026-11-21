package io.github.snowgemm.catbrowser.browser

import java.net.URI

internal class ReplaceableDecision<T> {
    private var pending: ((T) -> Unit)? = null

    fun replace(
        next: (T) -> Unit,
        rejectedValue: T,
    ) {
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

internal class ExtensionPromptQueue<T> {
    private val waiting = ArrayDeque<T>()

    var active: T? = null
        private set

    fun enqueue(item: T): T? {
        waiting.addLast(item)
        activateNext()
        return active
    }

    fun resolveActive(): T? {
        active = null
        activateNext()
        return active
    }

    fun removeAll(predicate: (T) -> Boolean): List<T> {
        val removed = mutableListOf<T>()
        active?.takeIf(predicate)?.let {
            removed += it
            active = null
        }
        val retained = ArrayDeque<T>()
        while (waiting.isNotEmpty()) {
            val item = waiting.removeFirst()
            if (predicate(item)) {
                removed += item
            } else {
                retained.addLast(item)
            }
        }
        waiting.addAll(retained)
        activateNext()
        return removed
    }

    private fun activateNext() {
        if (active == null && waiting.isNotEmpty()) {
            active = waiting.removeFirst()
        }
    }
}

internal object ExtensionSourcePolicy {
    fun isTrustedAmoDownloadUrl(value: String): Boolean {
        val uri = trustedAmoUri(value) ?: return false
        return uri.rawQuery == null && uri.rawFragment == null
    }

    fun isTrustedAmoListingUrl(value: String): Boolean = trustedAmoUri(value) != null

    private fun trustedAmoUri(value: String): URI? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("addons.mozilla.org", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) {
            return null
        }
        return uri
    }
}
