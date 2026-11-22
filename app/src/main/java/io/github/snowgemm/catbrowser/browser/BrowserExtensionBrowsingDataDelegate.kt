package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

internal class BrowserExtensionBrowsingDataDelegate(
    private val clearHistorySince: (Long) -> GeckoResult<Void>,
    private val clearDownloadsSince: (Long) -> Unit,
) : WebExtension.BrowsingDataDelegate {
    override fun onGetSettings(): GeckoResult<WebExtension.BrowsingDataDelegate.Settings> {
        val supportedTypes =
            WebExtension.BrowsingDataDelegate.Type.HISTORY or
                WebExtension.BrowsingDataDelegate.Type.DOWNLOADS
        val result = GeckoResult<WebExtension.BrowsingDataDelegate.Settings>()
        result.complete(
            WebExtension.BrowsingDataDelegate.Settings(
                0,
                supportedTypes,
                supportedTypes,
            ),
        )
        return result
    }

    override fun onClearHistory(sinceUnixTimestamp: Long): GeckoResult<Void> = clearHistorySince(sinceUnixTimestamp)

    override fun onClearDownloads(sinceUnixTimestamp: Long): GeckoResult<Void> {
        val result = GeckoResult<Void>()
        try {
            clearDownloadsSince(sinceUnixTimestamp)
            result.complete(null)
        } catch (error: Exception) {
            result.completeExceptionally(error)
        }
        return result
    }

    override fun onClearFormData(sinceUnixTimestamp: Long): GeckoResult<Void> = unsupportedDataType("dữ liệu biểu mẫu")

    override fun onClearPasswords(sinceUnixTimestamp: Long): GeckoResult<Void> = unsupportedDataType("mật khẩu")

    private fun unsupportedDataType(label: String): GeckoResult<Void> {
        val result = GeckoResult<Void>()
        result.completeExceptionally(
            UnsupportedOperationException("Cat Browser không quản lý $label."),
        )
        return result
    }
}
