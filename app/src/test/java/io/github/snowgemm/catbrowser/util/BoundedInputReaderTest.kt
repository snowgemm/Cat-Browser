package io.github.snowgemm.catbrowser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedInputReaderTest {
    @Test
    fun readsUtf8ResponseWithinLimit() {
        assertEquals(
            "Cat Browser – trình duyệt",
            readUtf8Bounded(
                ByteArrayInputStream("Cat Browser – trình duyệt".toByteArray(Charsets.UTF_8)),
                64,
            ),
        )
    }

    @Test
    fun acceptsResponseExactlyAtLimit() {
        val body = "12345678"
        assertEquals(body, readUtf8Bounded(ByteArrayInputStream(body.toByteArray()), body.length))
    }

    @Test
    fun rejectsResponseLargerThanLimit() {
        try {
            readUtf8Bounded(ByteArrayInputStream("123456789".toByteArray()), 8)
            fail("Expected oversized response to be rejected.")
        } catch (_: IOException) {
            // Expected: the reader stops before accepting bytes beyond the configured limit.
        }
    }
}
