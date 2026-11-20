package io.github.snowgemm.catbrowser.browser

import java.net.URI

internal object DownloadQueuePolicy {
    fun canCancel(status: BrowserDownloadStatus): Boolean =
        status == BrowserDownloadStatus.QUEUED || status == BrowserDownloadStatus.DOWNLOADING

    fun restoredStatus(status: BrowserDownloadStatus): BrowserDownloadStatus =
        if (canCancel(status)) BrowserDownloadStatus.FAILED else status

    fun completionStatus(cancelled: Boolean): BrowserDownloadStatus =
        if (cancelled) BrowserDownloadStatus.CANCELLED else BrowserDownloadStatus.COMPLETE

    fun uniqueFileName(
        requestedName: String,
        isOccupied: (String) -> Boolean
    ): String {
        val extensionSeparator = requestedName.lastIndexOf('.')
            .takeIf { it > 0 && it < requestedName.lastIndex }
        val base = if (extensionSeparator == null) requestedName else requestedName.substring(0, extensionSeparator)
        val extension = extensionSeparator?.let { requestedName.substring(it + 1) }
        var suffix = 0
        while (true) {
            val candidate = when {
                suffix == 0 -> requestedName
                extension == null -> "$base ($suffix)"
                else -> "$base ($suffix).$extension"
            }
            if (!isOccupied(candidate)) return candidate
            suffix++
        }
    }
}

internal class ReplaceableDecision<T> {
    private var pending: ((T) -> Unit)? = null

    fun replace(next: (T) -> Unit, rejectedValue: T) {
        pending?.invoke(rejectedValue)
        pending = next
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
            uri.host.equals("addons.mozilla.org", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443)
    }
}

internal object GeckoSessionVisibilityPolicy {
    fun isVisible(appForegrounded: Boolean, isSelected: Boolean, isViewAttached: Boolean): Boolean =
        appForegrounded && isSelected && isViewAttached
}
