package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlResolverTest {
    @Test
    fun keepsHttpAndHttpsUrls() {
        assertEquals(
            "https://example.com/path",
            BrowserUrlResolver.resolve("https://example.com/path")
        )
        assertEquals("http://example.com", BrowserUrlResolver.resolve("http://example.com"))
    }

    @Test
    fun addsHttpsToHostnamesAndLocalAddresses() {
        assertEquals("https://example.com", BrowserUrlResolver.resolve("example.com"))
        assertEquals("https://localhost:8080", BrowserUrlResolver.resolve("localhost:8080"))
    }

    @Test
    fun sendsPlainTextToSearchEngine() {
        assertEquals(
            "https://duckduckgo.com/?q=cat%20browser",
            BrowserUrlResolver.resolve("cat browser")
        )
    }

    @Test
    fun treatsNonWebSchemesAsSearchText() {
        assertEquals(
            "https://duckduckgo.com/?q=javascript%3Aalert%281%29",
            BrowserUrlResolver.resolve("javascript:alert(1)")
        )
    }

    @Test
    fun rejectsBlankInput() {
        assertNull(BrowserUrlResolver.resolve("  "))
    }
}
