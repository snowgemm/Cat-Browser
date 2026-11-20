package io.github.snowgemm.catbrowser.browser

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

