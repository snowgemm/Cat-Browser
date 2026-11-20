package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserExtensionRegressionTest {
    @Test
    fun onlyHttpsMozillaAddonsDownloadUrlsAreTrusted() {
        assertTrue(
            ExtensionSourcePolicy.isTrustedAmoDownloadUrl(
                "https://addons.mozilla.org/firefox/downloads/file/123/addon.xpi"
            )
        )
        assertFalse(ExtensionSourcePolicy.isTrustedAmoDownloadUrl("http://addons.mozilla.org/addon.xpi"))
        assertFalse(ExtensionSourcePolicy.isTrustedAmoDownloadUrl("https://evil.example/addon.xpi"))
        assertFalse(ExtensionSourcePolicy.isTrustedAmoDownloadUrl("https://user@addons.mozilla.org/addon.xpi"))
        assertFalse(ExtensionSourcePolicy.isTrustedAmoDownloadUrl("https://addons.mozilla.org:8443/addon.xpi"))
        assertFalse(ExtensionSourcePolicy.isTrustedAmoDownloadUrl("not a url"))
    }

    @Test
    fun newerPermissionPromptRejectsOlderPendingPrompt() {
        val decisions = mutableListOf<String>()
        val queue = ReplaceableDecision<String>()

        queue.replace({ decisions += "first:$it" }, "denied")
        queue.replace({ decisions += "second:$it" }, "denied")
        queue.resolve("approved")

        assertEquals(listOf("first:denied", "second:approved"), decisions)
    }

    @Test
    fun resolvingPromptIsOneShot() {
        val decisions = mutableListOf<Boolean>()
        val queue = ReplaceableDecision<Boolean>()
        queue.replace({ decisions += it }, false)

        queue.resolve(true)
        queue.resolve(false)

        assertEquals(listOf(true), decisions)
    }
}
