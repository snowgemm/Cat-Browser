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
        val results = JSONObject(payload).optJSONArray("results")
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
                val file = version?.optJSONObject("file")
                    ?: version?.optJSONArray("files")?.let { files ->
                        (0 until files.length())
                            .mapNotNull(files::optJSONObject)
                            .firstOrNull { candidate ->
                                candidate.optString("status") != "disabled"
                            }
                    }

                val rawDownloadUrl = file?.optString("url").orEmpty()
                val explicitlyUnsigned = file != null &&
                    file.has("is_mozilla_signed_extension") &&
                    !file.optBoolean("is_mozilla_signed_extension")
                val fileStatus = file?.optString("status").orEmpty()
                val fileApproved = fileStatus.isBlank() || fileStatus == "public"
                val downloadUrl = rawDownloadUrl.takeIf {
                    fileApproved &&
                        !explicitlyUnsigned &&
                        ExtensionSourcePolicy.isTrustedAmoDownloadUrl(it)
                }

                val rawListingUrl = item.optString("url")
                val listingUrl = rawListingUrl.takeIf(ExtensionSourcePolicy::isTrustedAmoListingUrl)
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
                        dailyUsers = item.optInt("average_daily_users", 0)
                    )
                )
            }
        }
    }

    private fun localizedField(item: JSONObject, field: String, fallbackLocale: String): String {
        val value = item.opt(field) ?: return ""
        if (value is String) return value
        if (value !is JSONObject) return ""

        return value.optString("vi")
            .ifBlank { value.optString("en-US") }
            .ifBlank { value.optString(fallbackLocale) }
            .ifBlank {
                val keys = value.keys()
                var translated = ""
                while (keys.hasNext() && translated.isBlank()) {
                    translated = value.optString(keys.next())
                }
                translated
            }
    }
}
