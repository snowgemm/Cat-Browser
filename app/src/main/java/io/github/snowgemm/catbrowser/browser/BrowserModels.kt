package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.GeckoSession

data class BrowserTabGroup(
    val id: String,
    val name: String,
    val colorIndex: Int
)

internal object BrowserTabGroupPolicy {
    const val MAX_GROUPS = 20
    const val MAX_NAME_LENGTH = 40
    const val COLOR_COUNT = 6

    fun normalizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    fun normalizeColorIndex(value: Int): Int = value.coerceIn(0, COLOR_COUNT - 1)
}

data class BrowserEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

data class SitePermissionPrompt(
    val origin: String,
    val permission: Int,
    val permissionLabel: String,
    val isPrivate: Boolean,
    val isIrreversible: Boolean
)

data class MediaPermissionPrompt(
    val origin: String,
    val videoSources: List<GeckoSession.PermissionDelegate.MediaSource>,
    val audioSources: List<GeckoSession.PermissionDelegate.MediaSource>,
    val isPrivate: Boolean
) {
    val hasCamera: Boolean get() = videoSources.isNotEmpty()
    val hasMicrophone: Boolean get() = audioSources.isNotEmpty()
}

data class AndroidPermissionRequest(
    val id: String,
    val permissions: List<String>
)

data class SitePermissionEntry(
    val permission: GeckoSession.PermissionDelegate.ContentPermission,
    val origin: String,
    val permissionLabel: String,
    val stateLabel: String,
    val isPrivate: Boolean,
    val canReset: Boolean
)

data class FindInPageState(
    val found: Boolean,
    val current: Int,
    val total: Int
)

data class AssistantTabSnapshot(
    val id: String,
    val title: String,
    val url: String,
    val active: Boolean
)
