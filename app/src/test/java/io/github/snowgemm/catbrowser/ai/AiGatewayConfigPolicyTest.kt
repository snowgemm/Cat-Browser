package io.github.snowgemm.catbrowser.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGatewayConfigPolicyTest {
    @Test
    fun acceptsOnlyHttpsGatewayBaseUrls() {
        assertEquals(
            "https://cat-gateway.example",
            AiGatewayConfigPolicy.normalizeBaseUrl("  https://cat-gateway.example/  ")
        )
        assertEquals(
            "https://cat-gateway.example/api",
            AiGatewayConfigPolicy.normalizeBaseUrl("https://cat-gateway.example/api")
        )
    }

    @Test
    fun rejectsCleartextAndUrlsWithEmbeddedCredentialsOrParameters() {
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("http://cat-gateway.example"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("https://user:pass@cat-gateway.example"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("https://cat-gateway.example?token=secret"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("https://cat-gateway.example/#fragment"))
    }

    @Test
    fun rejectsRelativeAndMalformedGatewayUrls() {
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("cat-gateway.example"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("https:///missing-host"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl("https://cat-gateway.example:70000"))
        assertNull(AiGatewayConfigPolicy.normalizeBaseUrl(" "))
    }

    @Test
    fun gatewayTokenMustHaveReasonableLengthAndNoWhitespace() {
        assertTrue(AiGatewayConfigPolicy.isValidAccessToken("a".repeat(32)))
        assertFalse(AiGatewayConfigPolicy.isValidAccessToken("short"))
        assertFalse(AiGatewayConfigPolicy.isValidAccessToken("a".repeat(30) + " "))
        assertFalse(AiGatewayConfigPolicy.isValidAccessToken("a".repeat(257)))
    }
}
