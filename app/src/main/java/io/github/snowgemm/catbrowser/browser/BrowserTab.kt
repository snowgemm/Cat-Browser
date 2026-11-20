package io.github.snowgemm.catbrowser.browser

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class BrowserTab internal constructor(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = START_PAGE_URL,
    initialTitle: String = "New tab",
    val isPrivate: Boolean = false,
    initialDesktopMode: Boolean = false,
    initialGroupId: String? = null
) {
    var url by mutableStateOf(initialUrl)
        internal set
    var title by mutableStateOf(initialTitle)
        internal set
    var isDesktopMode by mutableStateOf(initialDesktopMode)
        internal set
    var groupId by mutableStateOf(initialGroupId.takeUnless { isPrivate })
        internal set
    internal var pageSecurity by mutableStateOf<BrowserPageSecurity?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var progress by mutableStateOf(0)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        internal set
    var isFullScreen by mutableStateOf(false)
        internal set
    var session by mutableStateOf<GeckoSession?>(null)
        internal set
    var isViewAttached = false
        internal set
    var loadPendingUntilAttached = false
        internal set
    var pendingOriginalInput: String? = null
        internal set
    var pendingReferrerSession: GeckoSession? = null
        internal set
}

internal fun shouldPersistBrowserTab(tab: BrowserTab): Boolean = !tab.isPrivate
internal fun shouldRecordBrowserHistory(tab: BrowserTab): Boolean = !tab.isPrivate
internal fun shouldAllowBookmark(tab: BrowserTab): Boolean = !tab.isPrivate
