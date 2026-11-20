package io.github.snowgemm.catbrowser.browser

internal enum class BrowserContextResourceType {
    NONE,
    IMAGE,
    VIDEO,
    AUDIO,
}

internal data class BrowserContextMenuRequest(
    val tabId: String,
    val linkUrl: String?,
    val resourceUrl: String?,
    val resourceType: BrowserContextResourceType,
    val title: String?,
)

internal object BrowserContextMenuPolicy {
    private const val MAX_CONTEXT_URL_LENGTH = 8_192

    fun safeWebUrl(value: String?): String? {
        val candidate = value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    it.length <= MAX_CONTEXT_URL_LENGTH &&
                    it.none(Char::isWhitespace)
            } ?: return null

        if (
            !candidate.startsWith("https://", ignoreCase = true) &&
            !candidate.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }

        return BrowserUrlResolver.resolveAddress(candidate)
    }
}
