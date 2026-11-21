package io.github.snowgemm.catbrowser.browser

import java.net.URI

internal class ExtensionPromptQueue<T> {
    private val waiting = java.util.ArrayDeque<T>()

    var active: T? = null
        private set

    fun enqueue(item: T) {
        waiting.addLast(item)
        activateNextIfNeeded()
    }

    fun resolveActive(): T? {
        active = null
        activateNextIfNeeded()
        return active
    }

    fun removeAll(predicate: (T) -> Boolean): List<T> {
        val removed = mutableListOf<T>()
        val current = active
        if (current != null && predicate(current)) {
            removed += current
            active = null
        }

        val retained = java.util.ArrayDeque<T>()
        while (waiting.isNotEmpty()) {
            val item = waiting.removeFirst()
            if (predicate(item)) {
                removed += item
            } else {
                retained.addLast(item)
            }
        }
        waiting.addAll(retained)
        activateNextIfNeeded()
        return removed
    }

    private fun activateNextIfNeeded() {
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
