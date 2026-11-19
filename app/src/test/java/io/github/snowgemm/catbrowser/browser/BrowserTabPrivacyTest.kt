package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabPrivacyTest {
    @Test
    fun regularTabsCanBeRestoredAndSaved() {
        val tab = BrowserTab(isPrivate = false)

        assertTrue(shouldPersistBrowserTab(tab))
        assertTrue(shouldRecordBrowserHistory(tab))
        assertTrue(shouldAllowBookmark(tab))
    }

    @Test
    fun privateTabsAreExcludedFromAllAppOwnedBrowsingRecords() {
        val tab = BrowserTab(isPrivate = true)

        assertFalse(shouldPersistBrowserTab(tab))
        assertFalse(shouldRecordBrowserHistory(tab))
        assertFalse(shouldAllowBookmark(tab))
    }
}
