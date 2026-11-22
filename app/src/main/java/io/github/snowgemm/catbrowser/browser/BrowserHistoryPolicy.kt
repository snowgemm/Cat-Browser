package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoSession

internal object BrowserHistoryPolicy {
    fun shouldMarkVisited(
        isPrivate: Boolean,
        url: String,
        flags: Int,
    ): Boolean =
        !isPrivate &&
            (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) &&
            (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) != 0 &&
            (flags and GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR) == 0
}
