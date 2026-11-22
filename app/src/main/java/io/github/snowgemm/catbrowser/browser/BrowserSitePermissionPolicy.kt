package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoSession

/**
 * Identifies stored GeckoView permissions that cannot be reset to prompt.
 */
internal object BrowserSitePermissionPolicy {
    fun canReset(permission: Int): Boolean =
        permission != GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
}
