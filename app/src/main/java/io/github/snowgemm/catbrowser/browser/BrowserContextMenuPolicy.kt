package io.github.snowgemm.catbrowser.browser

import java.net.URI

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
    fun safeWebUrl(value: String?): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.rawUserInfo != null || uri.host.isNullOrBlank()) return null
        if (uri.port == 0 || uri.port > 65535) return null
        return candidate
    }
}
