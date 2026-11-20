package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageSecurityTest {
    @Test
    fun verifiedSecureConnectionShowsSecureIndicator() {
        val security =
        BrowserPageSecurity(
            isSecure = true,
            isException = false,
            activeMixedContentLoaded = false,
            passiveMixedContentLoaded = false,
        )

        assertTrue(security.isVerifiedSecure)
    }

    @Test
    fun securityExceptionDoesNotShowSecureIndicator() {
        val security =
        BrowserPageSecurity(
            isSecure = true,
            isException = true,
            activeMixedContentLoaded = false,
            passiveMixedContentLoaded = false,
        )

        assertFalse(security.isVerifiedSecure)
    }

    @Test
    fun loadedActiveMixedContentDoesNotShowSecureIndicator() {
        val security =
        BrowserPageSecurity(
            isSecure = true,
            isException = false,
            activeMixedContentLoaded = true,
            passiveMixedContentLoaded = false,
        )

        assertFalse(security.isVerifiedSecure)
    }

    @Test
    fun loadedPassiveMixedContentDoesNotShowSecureIndicator() {
        val security =
        BrowserPageSecurity(
            isSecure = true,
            isException = false,
            activeMixedContentLoaded = false,
            passiveMixedContentLoaded = true
        )

        assertFalse(security.isVerifiedSecure)
    }
}
