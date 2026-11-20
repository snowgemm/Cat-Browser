package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLifecyclePolicyTest {
    @Test
    fun sessionIsActiveOnlyWhenAppForegroundedSelectedAndAttached() {
        assertTrue(GeckoSessionVisibilityPolicy.isVisible(true, true, true))
        assertFalse(GeckoSessionVisibilityPolicy.isVisible(false, true, true))
        assertFalse(GeckoSessionVisibilityPolicy.isVisible(true, false, true))
        assertFalse(GeckoSessionVisibilityPolicy.isVisible(true, true, false))
    }
}
