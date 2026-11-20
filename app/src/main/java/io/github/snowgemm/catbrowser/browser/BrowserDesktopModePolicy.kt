package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoSessionSettings

internal data class BrowserSessionDisplaySettings(
    val userAgentMode: Int,
    val viewportMode: Int
)

internal object BrowserDesktopModePolicy {
    fun settingsFor(desktopMode: Boolean): BrowserSessionDisplaySettings =
        if (desktopMode) {
            BrowserSessionDisplaySettings(
                userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP,
                viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            )
        } else {
            BrowserSessionDisplaySettings(
                userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
                viewportMode = GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
        }
}
