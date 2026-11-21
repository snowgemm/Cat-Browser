package io.github.snowgemm.catbrowser.browser

import org.json.JSONObject

/**
 * Parses the public AMO v5 search response.
 *
 * Android compatibility in AMO is only a discovery filter; GeckoView still
 * validates the XPI signature and manifest during installation.
 */
internal object ExtensionCatalogParser {
    fun parse(payload: String): List<ExtensionCatalogEntry> {
        val results =
            JSONObject(payload).optJSONArray("results")
                ?: throw IllegalStateException("Phản hồi Mozilla không chứa danh sách tiện ích.")

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val status = item.optString("status")
                if (status.isNotBlank() && status != "public") continue

                val type = item.optString("type")
                if (type.isNotBlank() && type != "extension") continue

                val slug = item.optString("slug").takeIf(String::isNotBlank) ?: continue

                val version = item.optJSONObject("current_version")
                val file = version?.let(::findInstallableFile)
                val downloadUrl = file?.optString("url")?.takeIf(String::isNotBlank)

                val rawListingUrl = item.optString("url")
                val listingUrl =
                    rawListingUrl.takeIf(ExtensionSourcePolicy::isTrustedAmoListingUrl)
                        ?: "https://addons.mozilla.org/android/addon/$slug/"
                val locale = item.optString("default_locale", "en-US")
                val ratings = item.optJSONObject("ratings")

                add(
                    ExtensionCatalogEntry(
                        id = item.optString("id", slug),
                        slug = slug,
                        name = localizedField(item, "name", locale).ifBlank { slug },
                        summary = localizedField(item, "summary", locale),
                        version = version?.optString("version").orEmpty(),
                        downloadUrl = downloadUrl,
                        listingUrl = listingUrl,
                        averageRating = ratings?.optDouble("average", 0.0) ?: 0.0,
                        dailyUsers = item.optInt("average_daily_users", 0),
                    ),
                )
            }
        }
    }

    /**
     * Only explicitly approved public AMO files are eligible for installation.
     * When multiple candidates are present, prefer a file explicitly marked as
     * Mozilla-signed. GeckoView remains the final signature/manifest validator.
     */
    private fun findInstallableFile(version: JSONObject): JSONObject? {
        val candidates =
            buildList {
                version.optJSONObject("file")?.let(::add)
                version.optJSONArray("files")?.let { files ->
                    for (index in 0 until files.length()) {
                        files.optJSONObject(index)?.let(::add)
                    }
                }
            }

        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.optString("status") == "public" &&
                    candidate.optString("url").isNotBlank() &&
                    !(
                        candidate.has("is_mozilla_signed_extension") &&
                            !candidate.optBoolean("is_mozilla_signed_extension")
                    )
            }.sortedByDescending { it.optBoolean("is_mozilla_signed_extension") }
            .firstOrNull {
                ExtensionSourcePolicy.isTrustedAmoDownloadUrl(it.optString("url"))
            }
    }

    private fun localizedField(
        item: JSONObject,
        field: String,
        fallbackLocale: String,
    ): String {
        val value = item.opt(field) ?: return ""
        if (value is String) return value.trim()
        if (value !is JSONObject) return ""

        fun translation(locale: String?): String? {
            if (locale.isNullOrBlank()) return null
            return (value.opt(locale) as? String)?.trim()?.takeIf(String::isNotEmpty)
        }

        // AMO marks an unavailable requested locale as JSON null and supplies its fallback in _default.
        val defaultLocale = value.opt("_default") as? String
        translation("vi")?.let { return it }
        translation(defaultLocale)?.let { return it }
        translation(fallbackLocale)?.let { return it }
        translation("en-US")?.let { return it }

        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "_default") continue
            translation(key)?.let { return it }
        }
        return ""
    }
}
