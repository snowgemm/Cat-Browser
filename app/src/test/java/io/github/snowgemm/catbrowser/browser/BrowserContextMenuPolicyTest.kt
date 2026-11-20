package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserContextMenuPolicyTest {
    @Test
    fun acceptsHttpAndHttpsUrls() {
        assertEquals(
            "https://example.com/path?q=cat",
            BrowserContextMenuPolicy.safeWebUrl("https://example.com/path?q=cat"),
        )
        assertEquals(
            "http://127.0.0.1:8080/",
            BrowserContextMenuPolicy.safeWebUrl("http://127.0.0.1:8080/"),
        )
    }

    @Test
    fun rejectsUnsupportedSchemesAndRelativeUrls() {
        listOf(
            "javascript:alert(1)",
            "data:image/png;base64,AA==",
            "file:///sdcard/image.png",
            "blob:https://example.com/id",
            "//example.com/path",
            "/relative/path",
        ).forEach { value ->
            assertNull(BrowserContextMenuPolicy.safeWebUrl(value))
        }
    }

    @Test
    fun rejectsCredentialsAndInvalidPorts() {
        assertNull(BrowserContextMenuPolicy.safeWebUrl("https://user:pass@example.com/"))
        assertNull(BrowserContextMenuPolicy.safeWebUrl("https://example.com:65536/"))
        assertNull(BrowserContextMenuPolicy.safeWebUrl("https://example.com:0/"))
    }

    @Test
    fun trimsSurroundingWhitespaceWithoutChangingTheUrl() {
        assertEquals(
            "https://example.com/",
            BrowserContextMenuPolicy.safeWebUrl("  https://example.com/  "),
        )
    }
}
