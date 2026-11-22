package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.GeckoSession

class BrowserHistoryPolicyTest {
    @Test
    fun marksTopLevelHttpVisitsAsVisited() {
        assertTrue(
            BrowserHistoryPolicy.shouldMarkVisited(
                isPrivate = false,
                url = "https://example.com",
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL,
            ),
        )
    }

    @Test
    fun acceptsRedirectSourceVisits() {
        assertTrue(
            BrowserHistoryPolicy.shouldMarkVisited(
                isPrivate = false,
                url = "http://example.com",
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL or
                    GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE,
            ),
        )
    }

    @Test
    fun excludesPrivateSubframeAndErrorVisits() {
        val topLevel = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL
        assertFalse(BrowserHistoryPolicy.shouldMarkVisited(true, "https://example.com", topLevel))
        assertFalse(BrowserHistoryPolicy.shouldMarkVisited(false, "https://example.com", 0))
        assertFalse(
            BrowserHistoryPolicy.shouldMarkVisited(
                isPrivate = false,
                url = "https://example.com",
                flags = topLevel or GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR,
            ),
        )
    }

    @Test
    fun excludesNonWebSchemes() {
        assertFalse(
            BrowserHistoryPolicy.shouldMarkVisited(
                isPrivate = false,
                url = "about:blank",
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL,
            ),
        )
    }
}
