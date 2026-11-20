package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserExtensionRegressionTest {
    @Test
    fun onlyHttpsMozillaAddonsDownloadUrlsAreTrusted() {
        assertTrue(
            ExtensionSourcePolicy.isTrustedAmoDownloadUrl(
                "https://addons.mozilla.org/firefox/downloads/file/123/addon.xpi",
            ),
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
    fun reentrantPromptReplacementKeepsTheNewestDecisionPending() {
        val decisions = mutableListOf<String>()
        val queue = ReplaceableDecision<Boolean>()

        queue.replace(
            {
                decisions += "first:$it"
                queue.replace({ decisions += "nested:$it" }, false)
            },
            false,
        )
        queue.replace({ decisions += "second:$it" }, false)
        queue.resolve(true)

        assertEquals(
            listOf("first:false", "second:false", "nested:true"),
            decisions,
        )
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

    @Test
    fun parsesPublicAndroidAddonsEvenWhenInstallFileIsUnavailable() {
        val payload = """
            {
              "results": [
                {
                  "id": 42,
                  "slug": "example-addon",
                  "status": "public",
                  "type": "extension",
                  "default_locale": "en-US",
                  "name": {"en-US": "Example Add-on"},
                  "summary": {"en-US": "A test extension"},
                  "current_version": {"version": "2.1"}
                }
              ]
            }
        """.trimIndent()

        val entries = ExtensionCatalogParser.parse(payload)

        assertEquals(1, entries.size)
        assertEquals("Example Add-on", entries.single().name)
        assertEquals("2.1", entries.single().version)
        assertNull(entries.single().downloadUrl)
        assertEquals(
            "https://addons.mozilla.org/android/addon/example-addon/",
            entries.single().listingUrl
        )
    }

    @Test
    fun parsesMozillaSignedXpiFromCurrentVersionFile() {
        val payload = """
            {
              "results": [
                {
                  "id": 7,
                  "slug": "signed-addon",
                  "status": "public",
                  "type": "extension",
                  "name": {"en-US": "Signed Add-on"},
                  "current_version": {
                    "version": "1.4",
                    "file": {
                      "url": "https://addons.mozilla.org/firefox/downloads/file/77/signed.xpi",
                      "status": "public",
                      "is_mozilla_signed_extension": true
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val entry = ExtensionCatalogParser.parse(payload).single()

        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/file/77/signed.xpi",
            entry.downloadUrl
        )
        assertTrue(entry.canInstall)
    }

    @Test
    fun neverOffersExplicitlyUnsignedOrUntrustedCatalogFiles() {
        val payload = """
            {
              "results": [
                {
                  "id": 1,
                  "slug": "unsigned-addon",
                  "status": "public",
                  "type": "extension",
                  "current_version": {
                    "version": "1.0",
                    "file": {
                      "url": "https://addons.mozilla.org/firefox/downloads/file/1/unsigned.xpi",
                      "is_mozilla_signed_extension": false
                    }
                  }
                },
                {
                  "id": 2,
                  "slug": "untrusted-addon",
                  "status": "public",
                  "type": "extension",
                  "current_version": {
                    "version": "1.0",
                    "file": {
                      "url": "https://evil.example/extension.xpi",
                      "is_mozilla_signed_extension": true
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val entries = ExtensionCatalogParser.parse(payload)

        assertEquals(2, entries.size)
        assertTrue(entries.none { it.canInstall })
    }
}
