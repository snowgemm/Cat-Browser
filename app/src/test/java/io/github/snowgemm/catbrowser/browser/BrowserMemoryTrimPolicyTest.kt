package io.github.snowgemm.catbrowser.browser

import android.content.ComponentCallbacks2
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMemoryTrimPolicyTest {
    @Test
    fun uiHiddenClearsAllPreviewsOnLegacyAndModernAndroid() {
        assertEquals(
            BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                Build.VERSION_CODES.TIRAMISU
            )
        )
        assertEquals(
            BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            )
        )
    }

    @Test
    fun legacyRunningCriticalClearsAllPreviewsBeforeAndroid14() {
        assertEquals(
            BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(15, Build.VERSION_CODES.TIRAMISU)
        )
    }

    @Test
    fun legacyRunningLowClearsOnlyInactivePreviewsBeforeAndroid14() {
        assertEquals(
            BrowserMemoryTrimAction.CLEAR_INACTIVE_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(10, Build.VERSION_CODES.TIRAMISU)
        )
    }

    @Test
    fun legacyRunningLevelsAreIgnoredFromAndroid14Onward() {
        assertEquals(
            BrowserMemoryTrimAction.KEEP_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(15, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertEquals(
            BrowserMemoryTrimAction.KEEP_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(10, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
    }

    @Test
    fun backgroundLevelStillClearsAllPreviewsOnModernAndroid() {
        assertEquals(
            BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            )
        )
    }

    @Test
    fun lowerUnknownLevelsKeepAllPreviews() {
        assertEquals(
            BrowserMemoryTrimAction.KEEP_PREVIEWS,
            BrowserMemoryTrimPolicy.actionFor(9, Build.VERSION_CODES.TIRAMISU)
        )
    }
}
