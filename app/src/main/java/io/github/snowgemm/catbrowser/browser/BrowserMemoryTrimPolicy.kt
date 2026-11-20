package io.github.snowgemm.catbrowser.browser

import android.content.ComponentCallbacks2
import android.os.Build

internal enum class BrowserMemoryTrimAction {
    KEEP_PREVIEWS,
    CLEAR_INACTIVE_PREVIEWS,
    CLEAR_ALL_PREVIEWS,
}

internal object BrowserMemoryTrimPolicy {
    private const val FIRST_API_LEVEL_WITHOUT_RUNNING_TRIM_LEVELS =
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    // Android 14+ no longer dispatches these legacy levels.
    // Keep their documented values for Android 7.0–13 without referencing deprecated SDK fields.
    private const val LEGACY_RUNNING_LOW = 10
    private const val LEGACY_RUNNING_CRITICAL = 15

    fun actionFor(level: Int, sdkInt: Int): BrowserMemoryTrimAction {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            return BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS
        }

        if (sdkInt >= FIRST_API_LEVEL_WITHOUT_RUNNING_TRIM_LEVELS) {
            return BrowserMemoryTrimAction.KEEP_PREVIEWS
        }

        return when {
            level >= LEGACY_RUNNING_CRITICAL ->
                BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS
            level >= LEGACY_RUNNING_LOW ->
                BrowserMemoryTrimAction.CLEAR_INACTIVE_PREVIEWS
            else -> BrowserMemoryTrimAction.KEEP_PREVIEWS
        }
    }
}
