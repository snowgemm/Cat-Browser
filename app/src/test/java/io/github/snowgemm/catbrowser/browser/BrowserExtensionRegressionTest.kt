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
    fun permissionPromptsArePresentedInArrivalOrder() {
        val queue = ExtensionPromptQueue<String>()

        queue.enqueue("install")
        assertEquals("install", queue.active)

        queue.enqueue("optional")
        queue.enqueue("update")
        assertEquals("install", queue.active)

        assertEquals("optional", queue.resolveActive())
        assertEquals("update", queue.resolveActive())
        assertNull(queue.resolveActive())
    }

    @Test
    fun removingAnExtensionRejectsOnlyItsPendingPermissionPrompts() {
        val queue = ExtensionPromptQueue<Pair<String, String>>()
        queue.enqueue("addon-a" to "active")
        queue.enqueue("addon-b" to "queued")
        queue.enqueue("addon-a" to "later")

        val removed = queue.removeAll { it.first == "addon-a" }

        assertEquals(listOf("addon-a" to "active", "addon-a" to "later"), removed)
        assertEquals("addon-b" to "queued", queue.active)
        assertNull(queue.resolveActive())
    }

    @Test
    fun parsesPublicAndroidAddonsEvenWhenInstallFileIsUnavailable() {
        val payload =
            """
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
            entries.single().listingUrl,
        )
    }

    @Test
    fun fallsBackToDefaultLocaleWhenRequestedTranslationIsNull() {
        val payload =
            """
            {
              "results": [
                {
                  "id": 42,
                  "slug": "fallback-addon",
                  "status": "public",
                  "type": "extension",
                  "default_locale": "en-US",
                  "name": {
                    "vi": null,
                    "en-US": "Example Add-on",
                    "_default": "en-US"
                  },
                  "summary": {
                    "vi": null,
                    "en-US": "An extension without a Vietnamese translation",
                    "_default": "en-US"
                  },
                  "current_version": {"version": "2.1"}
                }
              ]
            }
            """.trimIndent()

        val entry = ExtensionCatalogParser.parse(payload).single()

        assertEquals("Example Add-on", entry.name)
        assertEquals("An extension without a Vietnamese translation", entry.summary)
        assertFalse(entry.name == "null")
        assertFalse(entry.summary == "null")
    }

    @Test
    fun prefersVietnameseTranslationWhenItExists() {
        val payload =
            """
            {
              "results": [
                {
                  "id": 43,
                  "slug": "translated-addon",
                  "status": "public",
                  "type": "extension",
                  "name": {
                    "vi": "Tiện ích mẫu",
                    "en-US": "Example Add-on",
                    "_default": "en-US"
                  },
                  "summary": {
                    "vi": "Mô tả tiếng Việt",
                    "en-US": "English summary",
                    "_default": "en-US"
                  }
                }
              ]
            }
            """.trimIndent()

        val entry = ExtensionCatalogParser.parse(payload).single()

        assertEquals("Tiện ích mẫu", entry.name)
        assertEquals("Mô tả tiếng Việt", entry.summary)
    }

    @Test
    fun parsesMozillaSignedXpiFromCurrentVersionFile() {
        val payload =
            """
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
            entry.downloadUrl,
        )
        assertTrue(entry.canInstall)
    }

    @Test
    fun selectsApprovedSignedFileInsteadOfEarlierUnreviewedFile() {
        val payload =
            """
            {
              "results": [
                {
                  "id": 8,
                  "slug": "multi-file-addon",
                  "status": "public",
                  "type": "extension",
                  "current_version": {
                    "version": "3.0",
                    "file": {
                      "url": "https://addons.mozilla.org/firefox/downloads/file/80/unreviewed.xpi",
                      "status": "unreviewed",
                      "is_mozilla_signed_extension": true
                    },
                    "files": [
                      {
                        "url": "https://addons.mozilla.org/firefox/downloads/file/81/pending.xpi",
                        "status": "unreviewed",
                        "is_mozilla_signed_extension": true
                      },
                      {
                        "url": "https://addons.mozilla.org/firefox/downloads/file/82/unsigned-public.xpi",
                        "status": "public",
                        "is_mozilla_signed_extension": false
                      },
                      {
                        "url": "https://addons.mozilla.org/firefox/downloads/file/83/signed-public.xpi",
                        "status": "public",
                        "is_mozilla_signed_extension": true
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()

        val entry = ExtensionCatalogParser.parse(payload).single()

        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/file/83/signed-public.xpi",
            entry.downloadUrl,
        )
        assertTrue(entry.canInstall)
    }

    @Test
    fun doesNotOfferFilesWithoutExplicitPublicApproval() {
        val payload =
            """
            {
              "results": [
                {
                  "id": 9,
                  "slug": "unknown-status-addon",
                  "status": "public",
                  "type": "extension",
                  "current_version": {
                    "version": "1.0",
                    "file": {
                      "url": "https://addons.mozilla.org/firefox/downloads/file/90/unknown-status.xpi",
                      "is_mozilla_signed_extension": true
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        val entry = ExtensionCatalogParser.parse(payload).single()

        assertNull(entry.downloadUrl)
        assertFalse(entry.canInstall)
    }

    @Test
    fun neverOffersExplicitlyUnsignedOrUntrustedCatalogFiles() {
        val payload =
            """
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
                      "status": "public",
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
                      "status": "public",
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
