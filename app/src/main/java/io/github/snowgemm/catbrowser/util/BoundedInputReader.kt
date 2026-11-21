package io.github.snowgemm.catbrowser.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads a UTF-8 response while enforcing a hard byte limit before allocating its full body.
 */
internal fun readUtf8Bounded(
    input: InputStream,
    maxBytes: Int,
): String {
    require(maxBytes > 0) { "maxBytes must be positive." }
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (output.size() > maxBytes - count) {
            throw IOException("Response exceeds the $maxBytes-byte limit.")
        }
        output.write(buffer, 0, count)
    }

    return output.toString(Charsets.UTF_8.name())
}

private const val DEFAULT_BUFFER_SIZE = 8 * 1024
