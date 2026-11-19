package io.github.snowgemm.catbrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loads a site's own HTTPS /favicon.ico without sending the visited hostname
 * to a third-party favicon proxy. The cache is memory-only and bounded.
 */
internal object FaviconRepository {
    private const val MAX_BYTES = 128 * 1024
    private const val MAX_DIMENSION = 256
    private const val OUTPUT_SIZE = 64

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "CatFavicon").apply { isDaemon = true }
    }
    private val cache = object : LruCache<String, Bitmap>(96) {}
    private val negativeCache = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, MutableList<(Bitmap?) -> Unit>>()

    fun load(pageUrl: String, callback: (Bitmap?) -> Unit) {
        val faviconUrl = faviconUrlFor(pageUrl)
        if (faviconUrl == null) {
            mainHandler.post { callback(null) }
            return
        }

        val cached = synchronized(cache) { cache.get(faviconUrl) }
        if (cached != null) {
            mainHandler.post { callback(cached) }
            return
        }
        if (negativeCache.contains(faviconUrl)) {
            mainHandler.post { callback(null) }
            return
        }

        var shouldStart = false
        synchronized(inFlight) {
            val existing = inFlight[faviconUrl]
            if (existing != null) {
                existing.add(callback)
            } else {
                inFlight[faviconUrl] = mutableListOf(callback)
                shouldStart = true
            }
        }
        if (!shouldStart) return

        executor.execute {
            val bitmap = fetch(faviconUrl)
            if (bitmap == null) {
                negativeCache.add(faviconUrl)
            } else {
                synchronized(cache) { cache.put(faviconUrl, bitmap) }
            }
            val callbacks = synchronized(inFlight) { inFlight.remove(faviconUrl).orEmpty() }
            mainHandler.post { callbacks.forEach { it(bitmap) } }
        }
    }

    private fun faviconUrlFor(pageUrl: String): String? {
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return null
        if (isLocalOrPrivateHost(host)) return null
        // Do not carry URL paths, query strings, fragments, or credentials into the icon request.
        return Uri.Builder()
            .scheme("https")
            .encodedAuthority(
                if (uri.port > 0 && uri.port != 80 && uri.port != 443) {
                    host + ":" + uri.port
                } else {
                    host
                }
            )
            .appendPath("favicon.ico")
            .build()
            .toString()
    }

    private fun isLocalOrPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
        if (host.contains(":")) return true // Avoid loopback/link-local/private IPv6 fetches.
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (first, second) = octets
        return first == 10 ||
            first == 127 ||
            first == 0 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun fetch(address: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as? HttpURLConnection) ?: return null
            connection.connectTimeout = 3_500
            connection.readTimeout = 3_500
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "Cat-Browser-Favicon")
            if (connection.responseCode !in 200..299) return null
            val mimeType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (mimeType == null || !mimeType.startsWith("image/")) return null
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_BYTES) return null

            val bytes = ByteArrayOutputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(4 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                }
                output.toByteArray()
            }
            if (bytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outWidth > 4096 || bounds.outHeight > 4096
            ) return null

            val sample = maxOf(
                1,
                maxOf(bounds.outWidth / MAX_DIMENSION, bounds.outHeight / MAX_DIMENSION)
            )
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null

            val scale = minOf(
                OUTPUT_SIZE.toFloat() / decoded.width.coerceAtLeast(1),
                OUTPUT_SIZE.toFloat() / decoded.height.coerceAtLeast(1),
                1f
            )
            if (scale >= 1f) decoded else {
                val scaled = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== decoded) decoded.recycle()
                scaled
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
