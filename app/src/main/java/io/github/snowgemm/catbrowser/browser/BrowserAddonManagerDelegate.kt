package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Adapts GeckoView add-on lifecycle events to BrowserExtensionsManager callbacks.
 * GeckoView delivers these callbacks on the UI thread.
 */
internal class BrowserAddonManagerDelegate(
    private val onInstalled: (WebExtension) -> Unit,
    private val onReady: (WebExtension) -> Unit,
    private val onEnabled: (WebExtension) -> Unit = onReady,
    private val onDisabled: (WebExtension) -> Unit,
    private val onUninstalled: (WebExtension) -> Unit,
    private val onOptionalPermissionsChanged: (WebExtension) -> Unit,
    private val onInstallationFailed: (WebExtension?, WebExtension.InstallException) -> Unit,
) : WebExtensionController.AddonManagerDelegate {
    override fun onInstalled(extension: WebExtension) = onInstalled.invoke(extension)

    override fun onReady(extension: WebExtension) = onReady.invoke(extension)

    override fun onEnabled(extension: WebExtension) = onEnabled.invoke(extension)

    override fun onDisabled(extension: WebExtension) = onDisabled.invoke(extension)

    override fun onUninstalled(extension: WebExtension) = onUninstalled.invoke(extension)

    override fun onOptionalPermissionsChanged(extension: WebExtension) =
        onOptionalPermissionsChanged.invoke(extension)

    override fun onInstallationFailed(
        extension: WebExtension?,
        installException: WebExtension.InstallException,
    ) = onInstallationFailed.invoke(extension, installException)
}
