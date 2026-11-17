package io.github.snowgemm.catbrowser.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserUrlResolver {
    private const val SEARCH_URL = "https://duckduckgo.com/?q="

    private val domainAddress = Regex(
        "^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}(?::[0-9]{1,5})?(?:[/?#].*)?$"
    )
    private val localhostAddress = Regex(
        "^localhost(?::[0-9]{1,5})?(?:[/?#].*)?$",
        RegexOption.IGNORE_CASE
    )
    private val ipv4Address = Regex(
        "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?:[/?#].*)?$"
    )

    fun resolve(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null

        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        ) {
            return value
        }

        val isAddress = !value.any(Char::isWhitespace) && (
            domainAddress.matches(value) ||
                localhostAddress.matches(value) ||
                ipv4Address.matches(value)
            )
        if (isAddress) return "https://$value"

        val encodedQuery = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return SEARCH_URL + encodedQuery
    }
}
