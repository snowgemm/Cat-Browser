package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.GeckoSession

class BrowserSitePermissionPolicyTest {
    @Test
    fun ordinarySitePermissionsCanBeReset() {
        assertTrue(
            BrowserSitePermissionPolicy.canReset(
                GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION,
            ),
        )
    }

    @Test
    fun trackingProtectionExceptionsCanBeResetToDefault() {
        assertTrue(
            BrowserSitePermissionPolicy.canReset(
                GeckoSession.PermissionDelegate.PERMISSION_TRACKING,
            ),
        )
    }

    @Test
    fun persistentStoragePermissionCannotBeResetToPrompt() {
        assertFalse(
            BrowserSitePermissionPolicy.canReset(
                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
            ),
        )
    }
}
