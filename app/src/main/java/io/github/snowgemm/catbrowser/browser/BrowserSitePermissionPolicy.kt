package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoSession

/**
 * Identifies stored GeckoView permissions that the public API cannot reset to prompt.
 */
internal object BrowserSitePermissionPolicy {
    fun canReset(permission: Int): Boolean =
        permission != GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE &&
            permission != GeckoSession.PermissionDelegate.PERMISSION_TRACKING
}
