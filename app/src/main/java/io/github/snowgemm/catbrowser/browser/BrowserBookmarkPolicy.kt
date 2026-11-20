package io.github.snowgemm.catbrowser.browser

internal object BrowserBookmarkPolicy {
    const val MAX_TITLE_LENGTH = 100

    fun rename(
        entries: List<BrowserEntry>,
        url: String,
        inputTitle: String,
    ): List<BrowserEntry>? {
        val title = inputTitle.trim().take(MAX_TITLE_LENGTH)
        if (title.isBlank()) return null

        val index = entries.indexOfFirst { it.url == url }
        if (index < 0) return null

        return entries.toMutableList().apply {
            this[index] = this[index].copy(title = title)
        }
    }

    fun remove(
        entries: List<BrowserEntry>,
        url: String,
    ): List<BrowserEntry>? {
        if (entries.none { it.url == url }) return null
        return entries.filterNot { it.url == url }
    }

    fun uniqueByUrl(entries: List<BrowserEntry>): List<BrowserEntry> =
        entries
            .sortedByDescending { it.timestamp }
            .distinctBy { it.url }

    fun sortAlphabetically(entries: List<BrowserEntry>): List<BrowserEntry> = entries.sortedBy { it.title.lowercase() }
}
