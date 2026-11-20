package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlResolverTest {
    @Test
    fun keepsValidHttpAndHttpsUrls() {
        assertEquals(
            "https://example.com/path",
            BrowserUrlResolver.resolve("https://example.com/path"),
        )
        assertEquals(
            "http://example.com",
            BrowserUrlResolver.resolve("http://example.com"),
        )
    }

    @Test
    fun addsHttpsToHostnamesAndLocalAddresses() {
        assertEquals(
            "https://example.com",
            BrowserUrlResolver.resolve("example.com"),
        )
        assertEquals(
            "https://localhost:8080",
            BrowserUrlResolver.resolve("localhost:8080"),
        )
        assertEquals(
            "https://192.168.1.20:8080/status",
            BrowserUrlResolver.resolve("192.168.1.20:8080/status"),
        )
    }

    @Test
    fun supportsInternationalizedDomainsAndIpv6() {
        assertEquals(
            "https://xn--bcher-kva.de",
            BrowserUrlResolver.resolve("bücher.de"),
        )
        assertEquals(
            "https://[::1]:8080",
            BrowserUrlResolver.resolve("[::1]:8080"),
        )
    }

    @Test
    fun sendsPlainTextToSearchEngine() {
        assertEquals(
            "https://duckduckgo.com/?q=cat%20browser",
            BrowserUrlResolver.resolve("cat browser"),
        )
    }

    @Test
    fun supportsConfigurableSearchEngines() {
        assertEquals(
            "https://www.google.com/search?q=cat%20browser",
            BrowserUrlResolver.resolve("cat browser", "google"),
        )
        assertEquals(
            "https://www.bing.com/search?q=cat%20browser",
            BrowserUrlResolver.resolve("cat browser", "bing"),
        )
        assertEquals(
            "https://search.brave.com/search?q=cat%20browser",
            BrowserUrlResolver.resolve("cat browser", "brave"),
        )
    }

    @Test
    fun validatesCustomHomepageAsWebAddress() {
        assertEquals(
            "https://example.com",
            BrowserUrlResolver.resolveAddress("example.com"),
        )
        assertEquals(
            "https://example.com/path",
            BrowserUrlResolver.resolveAddress("https://example.com/path"),
        )
        assertNull(BrowserUrlResolver.resolveAddress("search for cats"))
    }

    @Test
    fun treatsNonWebSchemesAsSearchText() {
        assertEquals(
            "https://duckduckgo.com/?q=javascript%3Aalert%281%29",
            BrowserUrlResolver.resolve("javascript:alert(1)"),
        )
    }

    @Test
    fun rejectsMalformedAddressesAndInvalidPorts() {
        assertEquals(
            "https://duckduckgo.com/?q=999.1.1.1",
            BrowserUrlResolver.resolve("999.1.1.1"),
        )
        assertEquals(
            "https://duckduckgo.com/?q=example.com%3A70000",
            BrowserUrlResolver.resolve("example.com:70000"),
        )
        assertEquals(
            "https://duckduckgo.com/?q=https%3A%2F%2Fexample.com%3A70000",
            BrowserUrlResolver.resolve("https://example.com:70000"),
        )
    }

    @Test
    fun rejectsBlankInput() {
        assertNull(BrowserUrlResolver.resolve("  "))
    }
}
