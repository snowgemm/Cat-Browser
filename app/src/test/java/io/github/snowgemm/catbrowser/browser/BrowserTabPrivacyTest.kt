package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabPrivacyTest {
    @Test
    fun regularTabsRemainEligibleForSessionRestore() {
        assertTrue(shouldPersistBrowserTab(BrowserTab(isPrivate = false)))
    }

    @Test
    fun privateTabsAreNeverSavedForSessionRestore() {
        assertFalse(shouldPersistBrowserTab(BrowserTab(isPrivate = true)))
    }
}
