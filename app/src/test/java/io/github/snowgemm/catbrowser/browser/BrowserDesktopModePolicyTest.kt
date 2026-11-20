package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.geckoview.GeckoSessionSettings

class BrowserDesktopModePolicyTest {
    @Test
    fun desktopModeUsesDesktopUserAgentAndViewport() {
        val settings = BrowserDesktopModePolicy.settingsFor(desktopMode = true)

        assertEquals(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP, settings.userAgentMode)
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP, settings.viewportMode)
    }

    @Test
    fun mobileModeRestoresMobileUserAgentAndViewport() {
        val settings = BrowserDesktopModePolicy.settingsFor(desktopMode = false)

        assertEquals(GeckoSessionSettings.USER_AGENT_MODE_MOBILE, settings.userAgentMode)
        assertEquals(GeckoSessionSettings.VIEWPORT_MODE_MOBILE, settings.viewportMode)
    }
}
