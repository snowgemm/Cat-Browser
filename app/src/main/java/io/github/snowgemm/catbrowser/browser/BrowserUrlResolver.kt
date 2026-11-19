package io.github.snowgemm.catbrowser.browser

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserUrlResolver {
    fun resolve(input: String, searchEngine: String = "duckduckgo"): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        return resolveAddress(value) ?: search(value, searchEngine)
    }

    fun resolveAddress(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        ) return normalizeWebUrl(value)
        if (!value.any(Char::isWhitespace)) return normalizeBareAddress(value)
        return null
    }

    private fun normalizeWebUrl(value: String): String? {
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null

        val authority = parseAuthority(uri.rawAuthority) ?: return null
        return composeUrl(scheme, authority, uri)
    }

    private fun normalizeBareAddress(value: String): String? {
        val uri = try {
            URI("https://" + value)
        } catch (_: Exception) {
            return null
        }

        val authority = parseAuthority(uri.rawAuthority) ?: return null
        return composeUrl("https", authority, uri)
    }

    private data class ParsedAuthority(
        val host: String,
        val port: Int?,
        val isIpv6: Boolean
    )

    private fun parseAuthority(rawAuthority: String?): ParsedAuthority? {
        val authority = rawAuthority?.takeIf { it.isNotBlank() } ?: return null
        if (authority.contains('@')) return null

        if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null

            val host = authority.substring(1, closingBracket)
            val suffix = authority.substring(closingBracket + 1)
            val port = parsePortSuffix(suffix) ?: if (suffix.isEmpty()) null else return null

            val isIpv6 = try {
                InetAddress.getByName(host).address.size == 16
            } catch (_: Exception) {
                false
            }
            if (!isIpv6) return null
            return ParsedAuthority(host.lowercase(), port, true)
        }

        if (authority.count { it == ':' } > 1) return null
        val separator = authority.lastIndexOf(':')
        val host = if (separator >= 0) authority.substring(0, separator) else authority
        val suffix = if (separator >= 0) authority.substring(separator) else ""
        val port = parsePortSuffix(suffix) ?: if (suffix.isEmpty()) null else return null
        val normalizedHost = normalizeHost(host) ?: return null

        return ParsedAuthority(normalizedHost, port, false)
    }

    private fun parsePortSuffix(suffix: String): Int? {
        if (suffix.isEmpty()) return null
        if (!suffix.startsWith(":")) return null
        val digits = suffix.drop(1)
        if (digits.isEmpty() || digits.length > 5 || digits.any { !it.isDigit() }) return null
        val port = digits.toIntOrNull() ?: return null
        return port.takeIf { it in 1..65535 }
    }

    private fun normalizeHost(host: String): String? {
        if (host.isBlank()) return null

        if (host.all { it.isDigit() || it == '.' }) {
            val parts = host.split('.')
            if (parts.size != 4) return null
            if (parts.any { part ->
                    part.isEmpty() || part.length > 3 ||
                        (part.toIntOrNull()?.let { it !in 0..255 } ?: true)
                }
            ) {
                return null
            }
            return parts.joinToString(".") { it.toInt().toString() }
        }

        if (host.equals("localhost", ignoreCase = true)) return "localhost"

        val ascii = try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: Exception) {
            return null
        }

        val normalized = ascii.removeSuffix(".")
        if (normalized.length > 253) return null
        val labels = normalized.split('.')
        if (labels.size < 2) return null
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 ||
                    label.startsWith("-") || label.endsWith("-")
            }
        ) {
            return null
        }
        return normalized
    }

    private fun composeUrl(
        scheme: String,
        authority: ParsedAuthority,
        uri: URI
    ): String? {
        val host = if (authority.isIpv6) {
            "[" + authority.host + "]"
        } else {
            authority.host
        }
        val port = authority.port?.let { ":" + it }.orEmpty()
        val rawPath = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?" + it }.orEmpty()
        val fragment = uri.rawFragment?.let { "#" + it }.orEmpty()
        val candidate = scheme + "://" + host + port + rawPath + query + fragment

        return try {
            URI(candidate).toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    private fun search(value: String, searchEngine: String): String {
        val baseUrl = when (searchEngine) {
            "google" -> "https://www.google.com/search?q="
            "bing" -> "https://www.bing.com/search?q="
            "brave" -> "https://search.brave.com/search?q="
            else -> "https://duckduckgo.com/?q="
        }
        val encodedQuery = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return baseUrl + encodedQuery
    }
}
