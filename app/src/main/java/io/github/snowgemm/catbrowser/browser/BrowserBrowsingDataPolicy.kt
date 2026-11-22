package io.github.snowgemm.catbrowser.browser

internal object BrowserBrowsingDataPolicy {
    private const val MILLIS_PER_SECOND = 1_000L

    fun sinceTimestampMillis(sinceUnixTimestamp: Long): Long =
        when {
            sinceUnixTimestamp > Long.MAX_VALUE / MILLIS_PER_SECOND -> Long.MAX_VALUE
            sinceUnixTimestamp < Long.MIN_VALUE / MILLIS_PER_SECOND -> Long.MIN_VALUE
            else -> sinceUnixTimestamp * MILLIS_PER_SECOND
        }

    fun shouldRemove(timestampMillis: Long, sinceUnixTimestamp: Long): Boolean =
        timestampMillis >= sinceTimestampMillis(sinceUnixTimestamp)

    fun shouldRemoveDownload(
        status: BrowserDownloadStatus,
        isPrivate: Boolean,
        createdAtMillis: Long,
        sinceUnixTimestamp: Long,
    ): Boolean {
        val isTerminal = status == BrowserDownloadStatus.COMPLETE ||
            status == BrowserDownloadStatus.FAILED ||
            status == BrowserDownloadStatus.CANCELLED
        return !isPrivate && isTerminal && shouldRemove(createdAtMillis, sinceUnixTimestamp)
    }

    fun retainHistory(
        entries: List<BrowserEntry>,
        sinceUnixTimestamp: Long,
    ): List<BrowserEntry> =
        entries.filterNot { shouldRemove(it.timestamp, sinceUnixTimestamp) }
}
