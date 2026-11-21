package io.github.snowgemm.catbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.snowgemm.catbrowser.BrowserMediaPlaybackService
import io.github.snowgemm.catbrowser.update.BrowserUpdateManager
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.PictureInPictureController
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import java.util.UUID
import java.util.concurrent.Executors

const val START_PAGE_URL = "about:blank"
const val DEFAULT_HOME_PAGE_URL = "about:blank"
const val SEARCH_HOME_PAGE_URL = "cat-internal://search-home"
private const val LEGACY_DEFAULT_HOME_PAGE_URL = "https://duckduckgo.com/"

@OptIn(ExperimentalGeckoViewApi::class)
class BrowserController(
    context: Context,
    private val runtime: GeckoRuntime,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    private val localStore = BrowserLocalStore(preferences)
    val updateManager = BrowserUpdateManager(appContext)

    var developerMode by mutableStateOf(preferences.getBoolean(KEY_DEVELOPER_MODE, false))
        private set

    var themeMode by mutableStateOf(preferences.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
        private set
    var searchEngine by mutableStateOf(preferences.getString(KEY_SEARCH_ENGINE, SEARCH_DUCKDUCKGO) ?: SEARCH_DUCKDUCKGO)
        private set
    var homePage by mutableStateOf(
        preferences
            .getString(KEY_HOME_PAGE, DEFAULT_HOME_PAGE_URL)
            ?.takeUnless { it == LEGACY_DEFAULT_HOME_PAGE_URL }
            ?: DEFAULT_HOME_PAGE_URL,
    )
        private set
    var startupMode by mutableStateOf(preferences.getString(KEY_STARTUP_MODE, STARTUP_CONTINUE) ?: STARTUP_CONTINUE)
        private set
    var trackingProtectionEnabled by mutableStateOf(
        preferences.getBoolean(KEY_TRACKING_PROTECTION, true),
    )
        private set

    val downloadManager = BrowserDownloadManager(appContext)
    private val storageController = runtime.getStorageController()
    private val contentBlockingController = runtime.getContentBlockingController()

    val tabs = mutableStateListOf<BrowserTab>()
    val tabGroups = mutableStateListOf<BrowserTabGroup>()
    val history = mutableStateListOf<BrowserEntry>()
    val bookmarks = mutableStateListOf<BrowserEntry>()
    val bookmarkFolders = mutableStateListOf<BrowserBookmarkFolder>()
    val extensionsManager =
        BrowserExtensionsManager(
            runtime = runtime,
            context = appContext,
            downloadManager = downloadManager,
            sessionProvider = { tabs.mapNotNull { it.session } },
            onCreateTabFromExtension = ::createExtensionTab,
            onActivateSession = ::activateExtensionTab,
            onCloseSession = ::closeExtensionTab,
            onOpenOptionsPage = ::openExtensionOptionsPage,
            onRestoreBrowserFocus = {
                activeTab?.session?.let(::activateExtensionTab)
            },
        )

    // Kept in memory only; private tabs are never added here.
    val recentlyClosedTabs = mutableStateListOf<BrowserEntry>()

    var isHistoryLoading by mutableStateOf(false)
        private set
    var isHistoryLoaded by mutableStateOf(false)
        private set
    var isBookmarksLoading by mutableStateOf(false)
        private set
    var isBookmarksLoaded by mutableStateOf(false)
        private set
    internal var privacyStats by mutableStateOf(BrowserPrivacyStats(isLoading = true))
        private set

    val sitePermissions = mutableStateListOf<SitePermissionEntry>()
    var isSitePermissionsLoading by mutableStateOf(false)
        private set
    var sitePermissionsMessage by mutableStateOf<String?>(null)
        private set
    private var sitePermissionsLoadVersion = 0

    var sitePermissionPrompt by mutableStateOf<SitePermissionPrompt?>(null)
        private set
    var mediaPermissionPrompt by mutableStateOf<MediaPermissionPrompt?>(null)
        private set
    internal var contextMenuRequest by mutableStateOf<BrowserContextMenuRequest?>(null)
        private set
    private var contextMenuSourceSession: GeckoSession? = null
    var pendingAndroidPermissionRequest by mutableStateOf<AndroidPermissionRequest?>(null)
        private set
    var findInPageResult by mutableStateOf<FindInPageState?>(null)
        private set
    var isPdfExporting by mutableStateOf(false)
        private set
    var pdfExportMessage by mutableStateOf<String?>(null)
        private set
    var isClearingData by mutableStateOf(false)
        private set
    var clearDataMessage by mutableStateOf<String?>(null)
        private set
    var clearDataError by mutableStateOf(false)
        private set

    private var pendingContentPermissionResult: GeckoResult<Int>? = null
    private var pendingContentPermissionSession: GeckoSession? = null
    private var pendingMediaPermissionCallback: GeckoSession.PermissionDelegate.MediaCallback? = null
    private var pendingMediaPermissionSession: GeckoSession? = null
    private var pendingAndroidPermissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var pendingAndroidPermissionSession: GeckoSession? = null
    private val dataHandler = Handler(Looper.getMainLooper())
    private val storageHandlerThread = HandlerThread("CatBrowserStorage").apply { start() }
    private val storageHandler = Handler(storageHandlerThread.looper)
    private val privacyStatsHandlerThread = HandlerThread("CatBrowserPrivacyStats").apply { start() }
    private val privacyStatsHandler = Handler(privacyStatsHandlerThread.looper)
    private val dataExecutor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "CatBrowserData").apply { isDaemon = true }
        }
    private val pdfWriterExecutor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "CatBrowserPdfWriter").apply { isDaemon = true }
        }
    private var privacyStatsRequestVersion = 0
    private var privacyStatsRequestInFlight = false
    private var privacyStatsRefreshQueued = false
    private var lastPrivacyStatsRefreshMs = 0L
    private var pendingBookmarkToggle = false
    private var historyClearGeneration = 0L
    var isAppForegrounded by mutableStateOf(true)
        private set

    var pictureInPictureSession by mutableStateOf<GeckoSession?>(null)
        private set
    private var pictureInPictureSourceSession: GeckoSession? = null
    private var pictureInPictureViewAttached = false
    private var pictureInPictureExitHandledByGecko = false
    private var enterPictureInPictureWindow: (() -> Boolean)? = null
    private var exitPictureInPictureWindow: (() -> Unit)? = null
    private var activeMediaGeckoSession: GeckoSession? = null
    private var activeMediaSession: MediaSession? = null
    private var isActiveMediaPlaying = false
    private var hasMediaPlaybackNotification = false

    var selectedTabId by mutableStateOf("")
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == selectedTabId }

    val privateTabCount: Int
        get() = tabs.count { it.isPrivate }

    val regularTabCount: Int
        get() = tabs.size - privateTabCount

    val ungroupedTabCount: Int
        get() = tabs.count { !it.isPrivate && it.groupId == null }

    val groupedTabCount: Int
        get() = tabGroups.size

    val canCreateTab: Boolean
        get() = tabs.size < MAX_TABS

    init {
        if (preferences.getString(KEY_HOME_PAGE, null) == LEGACY_DEFAULT_HOME_PAGE_URL) {
            preferences.edit().putString(KEY_HOME_PAGE, DEFAULT_HOME_PAGE_URL).apply()
        }
        if (startupMode == STARTUP_CONTINUE) restoreTabs()

        if (tabs.isEmpty()) {
            tabs.add(BrowserTab(initialUrl = resolvedHomePageUrl()))
        }

        if (tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.first().id
        }

        persistTabs()
        activateSelectedTab()
        extensionsManager.refreshInstalled()
    }

    fun newTab(groupId: String? = null) {
        if (tabs.size >= MAX_TABS) return

        val validGroupId = groupId?.takeIf { id -> tabGroups.any { it.id == id } }
        val tab =
            BrowserTab(
                initialUrl = resolvedHomePageUrl(),
                initialGroupId = validGroupId,
            )
        tabs.add(tab)
        selectTab(tab.id)
    }

    fun newPrivateTab() {
        if (tabs.size >= MAX_TABS) return

        val tab =
            BrowserTab(
                initialUrl = resolvedHomePageUrl(),
                initialTitle = "Tab ẩn danh",
                isPrivate = true,
            )
        tabs.add(tab)
        selectTab(tab.id)
    }

    fun dismissContextMenu() {
        contextMenuRequest = null
        contextMenuSourceSession = null
    }

    fun openContextMenuTargetInNewTab(url: String): Boolean {
        val request = contextMenuRequest ?: return false
        val safeUrl = BrowserContextMenuPolicy.safeWebUrl(url) ?: return false
        if (safeUrl != request.linkUrl && safeUrl != request.resourceUrl) return false
        val sourceTab = tabs.firstOrNull { it.id == request.tabId } ?: return false
        val sourceSession = contextMenuSourceSession
        if (
            selectedTabId != sourceTab.id ||
            sourceTab.session !== sourceSession ||
            sourceSession?.isOpen != true ||
            tabs.size >= MAX_TABS
        ) {
            return false
        }

        dismissContextMenu()
        val newTab =
            BrowserTab(
                initialUrl = safeUrl,
                initialTitle = safeUrl,
                isPrivate = sourceTab.isPrivate,
                initialDesktopMode = sourceTab.isDesktopMode,
                initialGroupId = sourceTab.groupId,
            ).apply {
                pendingReferrerSession = sourceSession
            }
        tabs.add(newTab)
        selectTab(newTab.id)
        persistTabs()
        return true
    }

    fun createTabGroup(
        tabIds: Collection<String>,
        name: String,
        colorIndex: Int,
    ): BrowserTabGroup? {
        val normalizedName = BrowserTabGroupPolicy.normalizeName(name)
        val selectedIds = tabIds.toSet()
        val selectedTabs = tabs.filter { it.id in selectedIds && !it.isPrivate }
        if (
            normalizedName.isBlank() ||
            selectedTabs.size < MIN_TABS_PER_GROUP ||
            tabGroups.size >= BrowserTabGroupPolicy.MAX_GROUPS
        ) {
            return null
        }

        val group =
            BrowserTabGroup(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                colorIndex = BrowserTabGroupPolicy.normalizeColorIndex(colorIndex),
            )
        tabGroups.add(group)
        selectedTabs.forEach { it.groupId = group.id }
        pruneEmptyTabGroups()
        persistTabs()
        return group
    }

    fun assignTabToGroup(
        tabId: String,
        groupId: String?,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        if (tab.isPrivate) return false
        if (groupId != null && tabGroups.none { it.id == groupId }) return false
        if (tab.groupId == groupId) return false

        tab.groupId = groupId
        pruneEmptyTabGroups()
        persistTabs()
        return true
    }

    fun updateTabGroup(
        groupId: String,
        name: String,
        colorIndex: Int,
    ): Boolean {
        val index = tabGroups.indexOfFirst { it.id == groupId }
        if (index < 0) return false
        val normalizedName = BrowserTabGroupPolicy.normalizeName(name)
        if (normalizedName.isBlank()) return false

        tabGroups[index] =
            tabGroups[index].copy(
                name = normalizedName,
                colorIndex = BrowserTabGroupPolicy.normalizeColorIndex(colorIndex),
            )
        persistTabs()
        return true
    }

    fun deleteTabGroup(groupId: String): Boolean {
        val index = tabGroups.indexOfFirst { it.id == groupId }
        if (index < 0) return false

        tabs.filter { it.groupId == groupId }.forEach { it.groupId = null }
        tabGroups.removeAt(index)
        persistTabs()
        return true
    }

    fun closeTabGroup(groupId: String): Boolean {
        if (tabGroups.none { it.id == groupId }) return false
        val groupedTabIds = tabs.filter { it.groupId == groupId }.map { it.id }
        groupedTabIds.forEach(::closeTab)
        pruneEmptyTabGroups()
        persistTabs()
        return true
    }

    private fun pruneEmptyTabGroups() {
        val activeGroupIds = tabs.mapNotNullTo(mutableSetOf()) { it.groupId }
        tabGroups.removeAll { it.id !in activeGroupIds }
    }

    fun updateTabPreview(
        tabId: String,
        bitmap: Bitmap,
    ) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val maxWidth = 320
        val maxHeight = 180
        val scale =
            minOf(
                1f,
                maxWidth.toFloat() / bitmap.width.coerceAtLeast(1),
                maxHeight.toFloat() / bitmap.height.coerceAtLeast(1),
            )
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled =
            if (width == bitmap.width && height == bitmap.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
        val compact =
            if (scaled.config != Bitmap.Config.RGB_565) {
                runCatching { scaled.copy(Bitmap.Config.RGB_565, false) }.getOrNull() ?: scaled
            } else {
                scaled
            }

        if (compact !== scaled && !scaled.isRecycled) scaled.recycle()
        if (compact !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        tab.previewBitmap = compact
    }

    /**
     * GeckoView uses active state as a visibility hint. Inactive sessions release
     * substantial page/compositor memory and are less likely to keep background work alive.
     */
    fun onAppBackgrounded() {
        isAppForegrounded = false
        tabs.forEach(::updateSessionActivity)
        pictureInPictureSession?.takeIf { it.isOpen }?.apply {
            setFocused(false)
            setActive(true)
            setPriorityHint(GeckoSession.PRIORITY_HIGH)
        }
        extensionsManager.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        isAppForegrounded = true
        tabs.forEach(::updateSessionActivity)
        pictureInPictureSession?.takeIf { it.isOpen }?.apply {
            setFocused(false)
            setActive(true)
            setPriorityHint(GeckoSession.PRIORITY_HIGH)
        }
        extensionsManager.onAppForegrounded()
    }

    fun setPictureInPictureLaunchHost(onLaunch: () -> Boolean) {
        enterPictureInPictureWindow = onLaunch
    }

    fun clearPictureInPictureLaunchHost() {
        enterPictureInPictureWindow = null
    }

    fun setPictureInPictureCloseHost(onClose: () -> Unit) {
        exitPictureInPictureWindow = onClose
    }

    fun clearPictureInPictureCloseHost() {
        exitPictureInPictureWindow = null
    }

    fun resumeMediaPlayback() {
        activeMediaSession?.takeIf { it.isActive() }?.play()
    }

    fun pauseMediaPlayback() {
        activeMediaSession?.takeIf { it.isActive() }?.pause()
    }

    fun stopMediaPlayback() {
        activeMediaSession?.stop()
    }

    fun seekMediaPlayback(positionMs: Long) {
        if (positionMs < 0L) return
        activeMediaSession?.takeIf { it.isActive() }?.seekTo(positionMs / 1_000.0, false)
    }

    fun notifyMediaAudioFocusChange(change: Int) {
        activeMediaSession?.notifySystemAudioFocusChange(change)
    }

    private fun rememberMediaSession(
        tab: BrowserTab,
        session: GeckoSession,
        mediaSession: MediaSession,
        force: Boolean = false,
    ) {
        if (tab.session !== session) return
        if (
            !force &&
            activeMediaGeckoSession != null &&
            activeMediaGeckoSession !== session &&
            isActiveMediaPlaying
        ) {
            return
        }
        if (activeMediaGeckoSession !== session && hasMediaPlaybackNotification && !force) {
            hasMediaPlaybackNotification = false
            runCatching { BrowserMediaPlaybackService.stop(appContext) }
        }
        activeMediaGeckoSession = session
        activeMediaSession = mediaSession
    }

    private fun updateMediaPlaybackState(
        tab: BrowserTab,
        session: GeckoSession,
        mediaSession: MediaSession,
        isPlaying: Boolean,
    ) {
        if (tab.session !== session) return
        rememberMediaSession(tab, session, mediaSession, force = true)
        isActiveMediaPlaying = isPlaying
        if (isPlaying) {
            hasMediaPlaybackNotification = true
            session.setActive(true)
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        }
        if (hasMediaPlaybackNotification) publishMediaPlayback(tab, isPlaying)
        updateSessionActivity(tab)
    }

    private fun refreshMediaNotification(
        tab: BrowserTab,
        session: GeckoSession,
    ) {
        if (activeMediaGeckoSession !== session || !hasMediaPlaybackNotification) return
        publishMediaPlayback(tab, isActiveMediaPlaying)
    }

    private fun publishMediaPlayback(
        tab: BrowserTab,
        isPlaying: Boolean,
    ) {
        val title =
            if (tab.isPrivate) {
                "Đang phát media riêng tư"
            } else {
                tab.title.ifBlank { "Cat Browser" }
            }
        val subtitle =
            if (tab.isPrivate) {
                "Cat Browser"
            } else {
                Uri.parse(tab.url).host?.takeIf(String::isNotBlank) ?: "Cat Browser"
            }
        runCatching {
            BrowserMediaPlaybackService.publish(
                context = appContext,
                isActive = true,
                isPlaying = isPlaying,
                title = title,
                subtitle = subtitle,
            )
        }
    }

    private fun clearMediaPlaybackSession(session: GeckoSession) {
        if (activeMediaGeckoSession !== session) return
        activeMediaGeckoSession = null
        activeMediaSession = null
        isActiveMediaPlaying = false
        hasMediaPlaybackNotification = false
        runCatching { BrowserMediaPlaybackService.stop(appContext) }
        tabs.firstOrNull { it.session === session }?.let(::updateSessionActivity)
    }

    private fun updateSessionActivity(tab: BrowserTab) {
        val session = tab.session?.takeIf { it.isOpen } ?: return
        val isPictureInPictureSource = session === pictureInPictureSourceSession
        val isVisible =
            GeckoSessionVisibilityPolicy.isVisible(
                isAppForegrounded,
                tab.id == selectedTabId,
                tab.isViewAttached,
            )
        val keepActive =
            BrowserMediaPlaybackPolicy.shouldKeepSessionActive(
                isVisible = isVisible,
                isPictureInPictureSource = isPictureInPictureSource,
                isPlayingMedia = activeMediaGeckoSession === session && isActiveMediaPlaying,
            )
        session.setFocused(isVisible && !isPictureInPictureSource)
        session.setActive(keepActive)
        session.setPriorityHint(
            if (keepActive) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
        )
        runtime.webExtensionController.setTabActive(session, isVisible && !isPictureInPictureSource)
    }

    fun onPictureInPictureWindowDestroyed(session: GeckoSession) {
        completePictureInPictureSession(session)
    }

    fun restorePictureInPictureSourceTab() {
        val sourceSession = pictureInPictureSourceSession ?: return
        tabs.firstOrNull { it.session === sourceSession }?.let { sourceTab ->
            selectTab(sourceTab.id)
        }
    }

    fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean) {
        pictureInPictureSourceSession?.let { source ->
            if (source.isOpen) {
                source.compositorController.onPipModeChanged(inPictureInPictureMode)
            }
        }
        pictureInPictureSession?.let { pip ->
            if (pip.isOpen) {
                pip.compositorController.onPipModeChanged(inPictureInPictureMode)
                pip.setFocused(false)
                pip.setActive(inPictureInPictureMode)
                pip.setPriorityHint(
                    if (inPictureInPictureMode) {
                        GeckoSession.PRIORITY_HIGH
                    } else {
                        GeckoSession.PRIORITY_DEFAULT
                    },
                )
            }
        }
    }

    fun onPictureInPictureViewAttached(session: GeckoSession) {
        if (pictureInPictureSession !== session || !session.isOpen) return
        pictureInPictureViewAttached = true
        session.setActive(true)
        session.setFocused(false)
        session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
    }

    fun onPictureInPictureViewDetached(session: GeckoSession) {
        if (pictureInPictureSession !== session || !session.isOpen) return
        pictureInPictureViewAttached = false
        session.setFocused(false)
        session.setActive(false)
        session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
    }

    private fun startPictureInPicture(
        sourceSession: GeckoSession,
        placementToken: String,
    ) {
        val sourceTab = activeTab
        if (
            !BrowserPictureInPicturePolicy.canOpen(
                placementToken = placementToken,
                sourceSessionIsOpen = sourceSession.isOpen,
                sourceTabIsSelected = sourceTab?.session === sourceSession,
                pictureInPictureAlreadyOpen = pictureInPictureSession != null,
            )
        ) {
            sourceSession.sessionPictureInPicture.exit()
            return
        }

        val launchHost = enterPictureInPictureWindow
        if (launchHost == null) {
            sourceSession.sessionPictureInPicture.exit()
            return
        }

        val sessionSettingsBuilder = GeckoSessionSettings.Builder()
        sessionSettingsBuilder.pictureInPictureToken(placementToken)
        val sessionSettings = sessionSettingsBuilder.build()
        val pipSession =
            runCatching {
                GeckoSession(sessionSettings).also { it.open(runtime) }
            }.getOrElse {
                sourceSession.sessionPictureInPicture.exit()
                return
            }

        pictureInPictureSourceSession = sourceSession
        pictureInPictureSession = pipSession
        pictureInPictureViewAttached = false
        pictureInPictureExitHandledByGecko = false
        sourceSession.sessionPictureInPicture.setKeepActive(true)
        sourceSession.setActive(true)
        sourceSession.setPriorityHint(GeckoSession.PRIORITY_HIGH)

        val launched = runCatching { launchHost() }.getOrDefault(false)
        if (!launched) completePictureInPictureSession(pipSession)
    }

    private fun requestPictureInPictureWindowClose(
        sourceSession: GeckoSession,
        geckoAlreadyHandledExit: Boolean,
    ) {
        if (pictureInPictureSourceSession !== sourceSession) return
        if (geckoAlreadyHandledExit) {
            pictureInPictureExitHandledByGecko = true
            if (sourceSession.isOpen) {
                sourceSession.sessionPictureInPicture.setKeepActive(false)
            }
        }

        val closeHost = exitPictureInPictureWindow
        if (closeHost != null) {
            closeHost()
        } else {
            pictureInPictureSession?.let { pip ->
                if (!pictureInPictureViewAttached) {
                    completePictureInPictureSession(pip)
                }
            }
        }
    }

    private fun closePictureInPictureForSourceSession(
        sourceSession: GeckoSession,
        notifyGecko: Boolean,
    ) {
        if (pictureInPictureSourceSession !== sourceSession) return
        if (notifyGecko && !pictureInPictureExitHandledByGecko && sourceSession.isOpen) {
            sourceSession.sessionPictureInPicture.exit()
        }
        requestPictureInPictureWindowClose(
            sourceSession = sourceSession,
            geckoAlreadyHandledExit = true,
        )
    }

    private fun completePictureInPictureSession(pipSession: GeckoSession) {
        if (pictureInPictureSession !== pipSession) return

        val source = pictureInPictureSourceSession
        val geckoAlreadyHandledExit = pictureInPictureExitHandledByGecko
        pictureInPictureSession = null
        pictureInPictureSourceSession = null
        pictureInPictureViewAttached = false
        pictureInPictureExitHandledByGecko = false

        if (source?.isOpen == true) {
            if (!geckoAlreadyHandledExit) {
                source.sessionPictureInPicture.exit()
            }
            source.sessionPictureInPicture.setKeepActive(false)

            val sourceTab = tabs.firstOrNull { it.session === source }
            val visible =
                sourceTab?.let { tab ->
                    GeckoSessionVisibilityPolicy.isVisible(
                        isAppForegrounded,
                        tab.id == selectedTabId,
                        tab.isViewAttached,
                    )
                } == true
            source.setFocused(visible)
            source.setActive(visible)
            source.setPriorityHint(
                if (visible) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
            )
            runtime.webExtensionController.setTabActive(source, visible)
        }

        if (pipSession.isOpen) {
            pipSession.setFocused(false)
            pipSession.setActive(false)
            pipSession.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
            pipSession.close()
        }
    }

    fun trimMemory(level: Int) {
        when (BrowserMemoryTrimPolicy.actionFor(level, Build.VERSION.SDK_INT)) {
            BrowserMemoryTrimAction.CLEAR_ALL_PREVIEWS -> {
                tabs.forEach { it.previewBitmap = null }
            }
            BrowserMemoryTrimAction.CLEAR_INACTIVE_PREVIEWS -> {
                tabs.filter { it.id != selectedTabId }.forEach { it.previewBitmap = null }
            }
            BrowserMemoryTrimAction.KEEP_PREVIEWS -> Unit
        }
    }

    fun exitFullScreen() {
        val tab = activeTab ?: return
        val session = tab.session
        if (tab.isFullScreen && session?.isOpen == true) {
            session.exitFullScreen()
        } else {
            tab.isFullScreen = false
        }
    }

    fun moveTab(
        tabId: String,
        targetId: String,
    ): Boolean {
        if (tabId == targetId) return false
        val fromIndex = tabs.indexOfFirst { it.id == tabId }
        val targetIndex = tabs.indexOfFirst { it.id == targetId }
        if (fromIndex < 0 || targetIndex < 0) return false
        val tab = tabs.removeAt(fromIndex)
        val insertionIndex = if (fromIndex < targetIndex) targetIndex - 1 else targetIndex
        tabs.add(insertionIndex.coerceIn(0, tabs.size), tab)
        persistTabs()
        return true
    }

    fun selectTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (selectedTabId != tabId) {
            activeTab
                ?.session
                ?.takeIf { it.isOpen }
                ?.getFinder()
                ?.clear()
            findInPageRequests.invalidate()
            findInPageResult = null
            dismissContextMenu()
        }
        if (selectedTabId == tabId) {
            if (target.url != START_PAGE_URL || target.session != null) ensureSession(target)
            target.session?.let { session ->
                val visible = GeckoSessionVisibilityPolicy.isVisible(isAppForegrounded, target.id == selectedTabId, target.isViewAttached)
                session.setActive(visible)
                session.setFocused(visible)
                session.setPriorityHint(
                    if (visible) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
                )
                runtime.webExtensionController.setTabActive(session, visible)
            }
            return
        }

        activeTab?.let { previous ->
            if (previous.isFullScreen) {
                previous.session?.exitFullScreen()
                previous.isFullScreen = false
            }
            previous.session?.let { session ->
                runtime.webExtensionController.setTabActive(session, false)
                session.setFocused(false)
                session.setActive(false)
                session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
            }
        }

        selectedTabId = target.id
        if (target.url != START_PAGE_URL || target.session != null) {
            ensureSession(target)
            target.session?.let { session ->
                val visible = GeckoSessionVisibilityPolicy.isVisible(isAppForegrounded, target.id == selectedTabId, target.isViewAttached)
                session.setActive(visible)
                session.setFocused(visible)
                session.setPriorityHint(
                    if (visible) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
                )
                runtime.webExtensionController.setTabActive(session, visible)
            }
        }
        persistTabs()
    }

    fun selectAdjacentTab(direction: Int): Boolean {
        if (direction == 0 || tabs.size < 2) return false
        val currentIndex = tabs.indexOfFirst { it.id == selectedTabId }
        if (currentIndex < 0) return false
        val step = if (direction < 0) -1 else 1
        val targetIndex = (currentIndex + step).coerceIn(0, tabs.lastIndex)
        if (targetIndex == currentIndex) return false
        selectTab(tabs[targetIndex].id)
        return true
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return

        val wasSelected = selectedTabId == tabId
        val removed = tabs.removeAt(index)
        removed.groupId?.let { groupId ->
            if (tabs.none { it.groupId == groupId }) {
                tabGroups.removeAll { it.id == groupId }
            }
        }
        if (shouldPersistBrowserTab(removed) && isWebUrl(removed.url)) {
            recentlyClosedTabs.add(
                0,
                BrowserEntry(
                    title = removed.title.ifBlank { removed.url },
                    url = removed.url,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            while (recentlyClosedTabs.size > MAX_RECENTLY_CLOSED) {
                recentlyClosedTabs.removeAt(recentlyClosedTabs.lastIndex)
            }
        }
        destroySession(removed)

        if (tabs.isEmpty()) {
            val replacement = BrowserTab(initialUrl = resolvedHomePageUrl())
            tabs.add(replacement)
            selectedTabId = replacement.id
            activateSelectedTab()
        } else if (wasSelected) {
            val replacementIndex = index.coerceAtMost(tabs.lastIndex)
            selectedTabId = tabs[replacementIndex].id
            activateSelectedTab()
        }

        persistTabs()
    }

    fun restoreRecentlyClosedTab(): Boolean {
        val entry = recentlyClosedTabs.firstOrNull() ?: return false
        if (tabs.size >= MAX_TABS || !isWebUrl(entry.url)) return false
        recentlyClosedTabs.removeAt(0)
        val restored = BrowserTab(initialUrl = entry.url, initialTitle = entry.title)
        tabs.add(restored)
        selectTab(restored.id)
        return true
    }

    fun closeAllPrivateTabs() {
        val privateTabs = tabs.filter { it.isPrivate }
        if (privateTabs.isEmpty()) return

        val selectedWasPrivate = activeTab?.isPrivate == true
        privateTabs.forEach(::destroySession)
        tabs.removeAll { it.isPrivate }

        if (tabs.isEmpty()) {
            val replacement = BrowserTab(initialUrl = resolvedHomePageUrl())
            tabs.add(replacement)
            selectedTabId = replacement.id
            activateSelectedTab()
        } else if (selectedWasPrivate || tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.first().id
            activateSelectedTab()
        }

        persistTabs()
    }

    fun navigate(input: String) {
        val destination = BrowserUrlResolver.resolve(input, searchEngine) ?: return
        navigateToUrl(destination, input.trim())
    }

    fun openExternalUrl(input: String) {
        val value = input.trim()
        if (!value.startsWith("https://", ignoreCase = true) &&
            !value.startsWith("http://", ignoreCase = true)
        ) {
            return
        }

        val destination = BrowserUrlResolver.resolve(value) ?: return
        navigateToUrl(destination, value)
    }

    private fun navigateToUrl(
        destination: String,
        originalInput: String,
    ) {
        val tab = activeTab ?: return
        if (tab.isFullScreen) tab.session?.exitFullScreen()
        tab.isFullScreen = false
        tab.url = destination
        tab.title = destination
        tab.error = null
        tab.progress = 0
        tab.isLoading = true

        val session = tab.session
        if (session == null || !session.isOpen) {
            ensureSession(tab, loadCurrentUrl = true)
            tab.pendingOriginalInput = originalInput
        } else if (tab.isViewAttached) {
            tab.loadPendingUntilAttached = false
            tab.pendingOriginalInput = null
            session.load(
                GeckoSession
                    .Loader()
                    .uri(destination)
                    .originalInput(originalInput),
            )
        } else {
            tab.loadPendingUntilAttached = true
            tab.pendingOriginalInput = originalInput
        }
        persistTabs()
    }

    /** Browser context exposed to Cat AI deliberately excludes every private tab. */
    fun assistantListTabs(): List<AssistantTabSnapshot> =
        tabs
            .filterNot { it.isPrivate }
            .map { tab ->
                AssistantTabSnapshot(
                    id = tab.id,
                    title = tab.title.ifBlank { if (tab.url == START_PAGE_URL) "Tab mới" else tab.url },
                    url = tab.url.takeUnless { it == START_PAGE_URL }.orEmpty(),
                    active = tab.id == selectedTabId,
                )
            }

    fun assistantActiveTab(): AssistantTabSnapshot? =
        activeTab?.takeUnless { it.isPrivate }?.let { tab ->
            AssistantTabSnapshot(
                id = tab.id,
                title = tab.title.ifBlank { if (tab.url == START_PAGE_URL) "Tab mới" else tab.url },
                url = tab.url.takeUnless { it == START_PAGE_URL }.orEmpty(),
                active = true,
            )
        }

    /** AI-created pages always open in a normal tab and require approval in the UI. */
    fun assistantOpenUrlInNewTab(input: String): String? {
        val raw = input.trim()
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
        val destination = BrowserUrlResolver.resolveAddress(raw) ?: return null
        if (!destination.startsWith("https://", true) && !destination.startsWith("http://", true)) return null
        if (tabs.size >= MAX_TABS) return null

        val tab = BrowserTab(initialUrl = destination, initialTitle = destination)
        tabs.add(tab)
        selectTab(tab.id)
        return tab.id
    }

    fun assistantSwitchToTab(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId && !it.isPrivate } ?: return false
        selectTab(tab.id)
        return true
    }

    fun assistantGoBack(): Boolean {
        val tab = activeTab?.takeUnless { it.isPrivate } ?: return false
        if (!tab.canGoBack || tab.session == null) return false
        tab.session?.goBack()
        return true
    }

    fun assistantGoForward(): Boolean {
        val tab = activeTab?.takeUnless { it.isPrivate } ?: return false
        if (!tab.canGoForward || tab.session == null) return false
        tab.session?.goForward()
        return true
    }

    fun assistantGoHome(): Boolean {
        if (activeTab?.isPrivate != false) return false
        goHome()
        return true
    }

    fun assistantBookmarkCurrentPage(): Boolean {
        val tab = activeTab?.takeUnless { it.isPrivate } ?: return false
        if (!isWebUrl(tab.url) || !isBookmarksLoaded) return false
        if (bookmarks.any { it.url == tab.url }) return true
        return addBookmark(tab.title, tab.url)
    }

    fun goBack() {
        activeTab?.takeIf { it.canGoBack }?.session?.goBack()
    }

    fun goForward() {
        activeTab?.takeIf { it.canGoForward }?.session?.goForward()
    }

    private fun resolvedHomePageUrl(): String =
        when (homePage) {
            SEARCH_HOME_PAGE_URL ->
                when (searchEngine) {
                    SEARCH_GOOGLE -> "https://www.google.com/"
                    SEARCH_BING -> "https://www.bing.com/"
                    SEARCH_BRAVE -> "https://search.brave.com/"
                    else -> "https://duckduckgo.com/"
                }
            else -> homePage
        }

    fun goHome() {
        if (homePage == SEARCH_HOME_PAGE_URL) {
            navigate(resolvedHomePageUrl())
            return
        }
        if (homePage != START_PAGE_URL) {
            navigate(homePage)
            return
        }
        val tab = activeTab ?: return
        destroySession(tab)
        tab.url = START_PAGE_URL
        tab.title = "Cat Browser"
        tab.isLoading = false
        tab.progress = 0
        tab.canGoBack = false
        tab.canGoForward = false
        tab.error = null
        tab.loadPendingUntilAttached = false
        tab.pendingOriginalInput = null
        persistTabs()
    }

    fun stopOrReload() {
        val tab = activeTab ?: return
        val session = tab.session

        if (tab.isLoading && tab.loadPendingUntilAttached) {
            tab.loadPendingUntilAttached = false
            tab.pendingOriginalInput = null
            tab.isLoading = false
            tab.progress = 0
            return
        }

        if (tab.isLoading && session?.isOpen == true) {
            session.stop()
            tab.isLoading = false
            tab.progress = 0
            return
        }

        tab.error = null
        if (session == null || !session.isOpen) {
            ensureSession(tab, loadCurrentUrl = true)
        } else if (tab.isViewAttached) {
            session.reload()
        } else {
            tab.loadPendingUntilAttached = true
            tab.pendingOriginalInput = tab.url
            tab.isLoading = true
        }
    }

    fun loadHistory() {
        if (isHistoryLoaded || isHistoryLoading) return
        isHistoryLoading = true
        dataExecutor.execute {
            val entries =
                runCatching {
                    localStore.readHistory(MAX_HISTORY)
                }.getOrDefault(emptyList())

            dataHandler.post {
                if (!isHistoryLoaded) history.addAll(entries)
                isHistoryLoaded = true
                isHistoryLoading = false
            }
        }
    }

    fun loadBookmarks() {
        if (isBookmarksLoaded || isBookmarksLoading) return
        isBookmarksLoading = true
        dataExecutor.execute {
            val folders =
                runCatching {
                    localStore.readBookmarkFolders()
                }.getOrDefault(emptyList())
            val entries =
                runCatching {
                    localStore.readBookmarks()
                }.getOrDefault(emptyList())

            dataHandler.post {
                if (!isBookmarksLoaded) {
                    bookmarkFolders.addAll(folders)
                    bookmarks.addAll(entries)
                }
                isBookmarksLoaded = true
                isBookmarksLoading = false
                if (pendingBookmarkToggle) {
                    pendingBookmarkToggle = false
                    toggleBookmark()
                }
            }
        }
    }

    fun toggleBookmark() {
        if (!isBookmarksLoaded) {
            pendingBookmarkToggle = true
            loadBookmarks()
            return
        }

        val tab = activeTab ?: return
        if (!shouldAllowBookmark(tab) || !isWebUrl(tab.url)) return

        val existing = bookmarks.indexOfFirst { it.url == tab.url }
        if (existing >= 0) {
            bookmarks.removeAt(existing)
        } else {
            bookmarks.add(
                0,
                BrowserEntry(
                    title = tab.title.ifBlank { tab.url },
                    url = tab.url,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
        persistBookmarks()
    }

    fun addBookmark(
        title: String,
        inputUrl: String,
    ): Boolean {
        if (!isBookmarksLoaded) {
            loadBookmarks()
            return false
        }
        val url = BrowserUrlResolver.resolveAddress(inputUrl) ?: return false
        if (!isWebUrl(url)) return false
        val cleanTitle =
            title.trim().take(100).ifBlank {
                Uri.parse(url).host ?: url
            }
        val existingFolderId = bookmarks.firstOrNull { it.url == url }?.folderId
        bookmarks.removeAll { it.url == url }
        bookmarks.add(
            0,
            BrowserEntry(cleanTitle, url, System.currentTimeMillis(), existingFolderId),
        )
        persistBookmarks()
        return true
    }

    internal fun bookmarkBackupSnapshot(): BrowserBookmarkBackupSnapshot? {
        if (!isBookmarksLoaded || isBookmarksLoading) return null
        return BrowserBookmarkBackupSnapshot(
            bookmarks = bookmarks.toList(),
            folders = bookmarkFolders.toList(),
        )
    }

    internal fun importBookmarkBackup(backup: ParsedBrowserBookmarkBackup): BrowserBookmarkImportSummary? {
        if (!isBookmarksLoaded || isBookmarksLoading) return null
        val merged =
            BrowserBookmarkTransferPolicy.merge(
                existingBookmarks = bookmarks.toList(),
                existingFolders = bookmarkFolders.toList(),
                imported = backup,
            )
        bookmarks.clear()
        bookmarks.addAll(merged.bookmarks)
        bookmarkFolders.clear()
        bookmarkFolders.addAll(merged.folders)
        persistBookmarks()
        persistBookmarkFolders()
        return merged.summary
    }

    fun renameBookmark(
        url: String,
        title: String,
    ): Boolean {
        if (!isBookmarksLoaded) return false
        val updated = BrowserBookmarkPolicy.rename(bookmarks.toList(), url, title) ?: return false
        bookmarks.clear()
        bookmarks.addAll(updated)
        persistBookmarks()
        return true
    }

    fun removeBookmark(url: String): Boolean {
        if (!isBookmarksLoaded) return false
        val updated = BrowserBookmarkPolicy.remove(bookmarks.toList(), url) ?: return false
        bookmarks.clear()
        bookmarks.addAll(updated)
        persistBookmarks()
        return true
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun createBookmarkFolder(name: String): Boolean {
        if (!isBookmarksLoaded) return false
        val updated =
            BrowserBookmarkPolicy.createFolder(
                bookmarkFolders.toList(),
                UUID.randomUUID().toString(),
                name,
            ) ?: return false
        bookmarkFolders.clear()
        bookmarkFolders.addAll(updated)
        persistBookmarkFolders()
        return true
    }

    fun renameBookmarkFolder(
        id: String,
        name: String,
    ): Boolean {
        if (!isBookmarksLoaded) return false
        val updated = BrowserBookmarkPolicy.renameFolder(bookmarkFolders.toList(), id, name) ?: return false
        bookmarkFolders.clear()
        bookmarkFolders.addAll(updated)
        persistBookmarkFolders()
        return true
    }

    fun removeBookmarkFolder(id: String): Boolean {
        if (!isBookmarksLoaded) return false
        val updated = BrowserBookmarkPolicy.removeFolder(bookmarkFolders.toList(), id) ?: return false
        bookmarkFolders.clear()
        bookmarkFolders.addAll(updated)
        bookmarks.indices.forEach { index ->
            if (bookmarks[index].folderId == id) {
                bookmarks[index] = bookmarks[index].copy(folderId = null)
            }
        }
        persistBookmarkFolders()
        persistBookmarks()
        return true
    }

    fun moveBookmarkToFolder(
        url: String,
        folderId: String?,
    ): Boolean {
        if (!isBookmarksLoaded) return false
        val updated =
            BrowserBookmarkPolicy.moveToFolder(
                bookmarks.toList(),
                url,
                folderId,
                bookmarkFolders.toList(),
            ) ?: return false
        bookmarks.clear()
        bookmarks.addAll(updated)
        persistBookmarks()
        return true
    }

    fun clearHistory() {
        historyClearGeneration++
        tabs.forEach { it.session?.purgeHistory() }
        history.clear()
        recentlyClosedTabs.clear()
        isHistoryLoaded = true
        isHistoryLoading = false
        persistHistory()
    }

    fun updateThemeMode(mode: String) {
        if (mode !in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)) return
        themeMode = mode
        preferences.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun updateSearchEngine(engine: String) {
        if (engine !in setOf(SEARCH_GOOGLE, SEARCH_DUCKDUCKGO, SEARCH_BING, SEARCH_BRAVE)) return
        searchEngine = engine
        preferences.edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    fun setHomePageMode(mode: String): Boolean {
        val selected =
            when (mode) {
                "cat" -> DEFAULT_HOME_PAGE_URL
                "search" -> SEARCH_HOME_PAGE_URL
                else -> return false
            }
        homePage = selected
        preferences.edit().putString(KEY_HOME_PAGE, selected).apply()
        return true
    }

    fun setHomePage(input: String): Boolean {
        val normalized = BrowserUrlResolver.resolveAddress(input) ?: return false
        if (!normalized.startsWith("http://", true) && !normalized.startsWith("https://", true)) return false
        homePage = normalized
        preferences.edit().putString(KEY_HOME_PAGE, normalized).apply()
        return true
    }

    fun unlockDeveloperMode(code: String): Boolean {
        if (code.trim() != DEVELOPER_UNLOCK_CODE) return false
        developerMode = true
        preferences.edit().putBoolean(KEY_DEVELOPER_MODE, true).apply()
        return true
    }

    fun disableDeveloperMode() {
        developerMode = false
        preferences.edit().putBoolean(KEY_DEVELOPER_MODE, false).apply()
    }

    fun updateStartupMode(mode: String) {
        if (mode !in setOf(STARTUP_CONTINUE, STARTUP_HOME)) return
        startupMode = mode
        preferences.edit().putString(KEY_STARTUP_MODE, mode).apply()
    }

    fun openEntry(url: String) {
        navigate(url)
    }

    fun resolveSitePermission(allow: Boolean) {
        val result = pendingContentPermissionResult
        pendingContentPermissionResult = null
        pendingContentPermissionSession = null
        sitePermissionPrompt = null
        result?.complete(
            if (allow) {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            } else {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            },
        )
        loadSitePermissions()
    }

    fun resolveMediaPermission(allow: Boolean) {
        val prompt = mediaPermissionPrompt ?: return
        val callback = pendingMediaPermissionCallback
        pendingMediaPermissionCallback = null
        pendingMediaPermissionSession = null
        mediaPermissionPrompt = null
        if (allow && callback != null) {
            callback.grant(
                prompt.videoSources.firstOrNull(),
                prompt.audioSources.firstOrNull(),
            )
        } else {
            callback?.reject()
        }
    }

    fun resolveAndroidPermissionRequest(grantResults: Map<String, Boolean>) {
        val request = pendingAndroidPermissionRequest
        val callback = pendingAndroidPermissionCallback
        if (request != null) {
            val denied =
                preferences
                    .getStringSet(KEY_DENIED_ANDROID_PERMISSIONS, emptySet())
                    .orEmpty()
                    .toMutableSet()
            denied.removeAll(request.permissions.toSet())
            denied.addAll(
                BrowserAndroidPermissionPolicy.deniedPermissionsAfterRequest(
                    request.permissions,
                    grantResults,
                ),
            )
            preferences.edit().putStringSet(KEY_DENIED_ANDROID_PERMISSIONS, denied).apply()
        }

        val granted =
            request != null && areRequestedAndroidPermissionsGranted(request.permissions, grantResults)
        pendingAndroidPermissionCallback = null
        pendingAndroidPermissionSession = null
        pendingAndroidPermissionRequest = null
        if (granted) callback?.grant() else callback?.reject()
    }

    fun setDesktopMode(
        tabId: String,
        enabled: Boolean,
    ) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isDesktopMode == enabled) return

        tab.isDesktopMode = enabled
        tab.session?.takeIf { it.isOpen }?.let { session ->
            val displaySettings = BrowserDesktopModePolicy.settingsFor(enabled)
            session.settings.setUserAgentMode(displaySettings.userAgentMode)
            session.settings.setViewportMode(displaySettings.viewportMode)
            session.reload()
        }
        persistTabs()
    }

    fun updateTrackingProtection(enabled: Boolean) {
        if (trackingProtectionEnabled == enabled) return
        trackingProtectionEnabled = enabled
        preferences.edit().putBoolean(KEY_TRACKING_PROTECTION, enabled).apply()
        tabs.forEach { tab ->
            tab.session?.settings?.setUseTrackingProtection(enabled)
        }
        refreshPrivacyStats(force = true)
    }

    fun refreshPrivacyStats(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (privacyStatsRequestInFlight) {
            if (force) privacyStatsRefreshQueued = true
            return
        }
        if (!force && now - lastPrivacyStatsRefreshMs < PRIVACY_STATS_REFRESH_INTERVAL_MS) {
            return
        }

        privacyStatsRequestInFlight = true
        lastPrivacyStatsRefreshMs = now
        val requestVersion = ++privacyStatsRequestVersion
        privacyStats = privacyStats.copy(isLoading = true, hasError = false)

        privacyStatsHandler.post {
            try {
                contentBlockingController
                    .getTrackingDbEventsByDateRange(
                        now - PRIVACY_STATS_RANGE_MS,
                        now,
                    ).accept(
                        { events ->
                            val result =
                                aggregatePrivacyBlockingEvents(
                                    events.orEmpty().map { event ->
                                        PrivacyBlockingEvent(type = event.type, count = event.count)
                                    },
                                )
                            dataHandler.post {
                                if (requestVersion == privacyStatsRequestVersion) {
                                    privacyStats = result.copy(isLoading = false, hasError = false)
                                    finishPrivacyStatsRequest()
                                }
                            }
                        },
                        { _ ->
                            dataHandler.post {
                                if (requestVersion == privacyStatsRequestVersion) {
                                    privacyStats =
                                        privacyStats.copy(
                                            isLoading = false,
                                            hasError = true,
                                        )
                                    finishPrivacyStatsRequest()
                                }
                            }
                        },
                    )
            } catch (_: Exception) {
                dataHandler.post {
                    if (requestVersion == privacyStatsRequestVersion) {
                        privacyStats =
                            privacyStats.copy(
                                isLoading = false,
                                hasError = true,
                            )
                        finishPrivacyStatsRequest()
                    }
                }
            }
        }
    }

    private fun finishPrivacyStatsRequest() {
        privacyStatsRequestInFlight = false
        if (privacyStatsRefreshQueued) {
            privacyStatsRefreshQueued = false
            refreshPrivacyStats(force = true)
        }
    }

    fun loadSitePermissions() {
        if (isSitePermissionsLoading) return
        isSitePermissionsLoading = true
        sitePermissionsMessage = null
        val requestVersion = ++sitePermissionsLoadVersion
        storageHandler.post {
            storageController.getAllPermissions().accept(
                { permissions ->
                    val entries =
                        permissions
                            .orEmpty()
                            .map { permission ->
                                val host = runCatching { Uri.parse(permission.uri).host }.getOrNull()
                                SitePermissionEntry(
                                    permission = permission,
                                    origin = host ?: permission.uri,
                                    permissionLabel = permissionLabel(permission.permission),
                                    stateLabel =
                                        when (permission.value) {
                                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> "Được phép"
                                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> "Bị chặn"
                                            else -> "Mặc định"
                                        },
                                    isPrivate = permission.privateMode,
                                    canReset = BrowserSitePermissionPolicy.canReset(permission.permission),
                                )
                            }.sortedWith(compareBy<SitePermissionEntry>({ it.origin }, { it.permissionLabel }))

                    dataHandler.post {
                        if (requestVersion != sitePermissionsLoadVersion) return@post
                        sitePermissions.clear()
                        sitePermissions.addAll(entries)
                        isSitePermissionsLoading = false
                    }
                },
                { error ->
                    dataHandler.post {
                        if (requestVersion != sitePermissionsLoadVersion) return@post
                        isSitePermissionsLoading = false
                        sitePermissionsMessage = error?.message ?: "Không thể tải quyền website."
                    }
                },
            )
        }
    }

    fun resetSitePermission(entry: SitePermissionEntry) {
        if (!entry.canReset) return
        dataExecutor.execute {
            runCatching {
                storageController.setPermission(
                    entry.permission,
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
                )
            }.onSuccess {
                dataHandler.post {
                    loadSitePermissions()
                    sitePermissionsMessage = "Đã đặt lại quyền cho " + entry.origin + "."
                }
            }.onFailure { error ->
                dataHandler.post {
                    sitePermissionsMessage = error.message ?: "Không thể đặt lại quyền website."
                }
            }
        }
    }

    private fun permissionLabel(permission: Int): String =
        when (permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Vị trí"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Thông báo"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "Lưu trữ lâu dài"
            GeckoSession.PermissionDelegate.PERMISSION_XR -> "Thực tế ảo / XR"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "Tự phát âm thanh"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "Tự phát video"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "Phát nội dung DRM"
            GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> "Cho phép trình theo dõi"
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "Truy cập cookie bên thứ ba"
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> "Truy cập thiết bị cục bộ"
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> "Truy cập mạng cục bộ"
            else -> "Quyền website ($permission)"
        }

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookiesAndSiteData: Boolean,
        clearCache: Boolean,
        clearSitePermissions: Boolean,
    ) {
        if (isClearingData) return
        if (!clearHistory && !clearCookiesAndSiteData && !clearCache && !clearSitePermissions) {
            clearDataMessage = "Chọn ít nhất một loại dữ liệu cần xóa."
            clearDataError = true
            return
        }

        var flags = 0L
        if (clearCookiesAndSiteData) {
            flags = flags or StorageController.ClearFlags.COOKIES
            flags = flags or StorageController.ClearFlags.DOM_STORAGES
            flags = flags or StorageController.ClearFlags.AUTH_SESSIONS
        }
        if (clearCache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (clearSitePermissions) flags = flags or StorageController.ClearFlags.PERMISSIONS

        isClearingData = true
        clearDataError = false
        clearDataMessage = "Đang xóa dữ liệu duyệt web…"

        if (clearHistory) {
            historyClearGeneration++
            tabs.forEach { it.session?.purgeHistory() }
            history.clear()
            recentlyClosedTabs.clear()
            isHistoryLoaded = true
            isHistoryLoading = false
            dataExecutor.execute { localStore.removeHistory() }
        }

        if (flags == 0L) {
            dataHandler.post {
                isClearingData = false
                clearDataMessage = "Đã xóa lịch sử duyệt web."
            }
            return
        }

        // Close every GeckoSession first. GeckoView warns that open sessions can
        // recreate data while the storage clear operation is running.
        tabs.forEach(::destroySession)

        storageHandler.post {
            storageController.clearData(flags).accept(
                {
                    dataHandler.post {
                        if (clearSitePermissions) {
                            sitePermissionsLoadVersion++
                            isSitePermissionsLoading = false
                            sitePermissions.clear()
                            loadSitePermissions()
                        }
                        isClearingData = false
                        clearDataError = false
                        clearDataMessage = "Đã xóa dữ liệu đã chọn. Tab hiện tại sẽ tải lại."
                        persistTabs()
                        activateSelectedTab()
                    }
                },
                { error ->
                    dataHandler.post {
                        isClearingData = false
                        clearDataError = true
                        clearDataMessage = error?.message ?: "Không thể xóa một phần dữ liệu website."
                        persistTabs()
                        activateSelectedTab()
                    }
                },
            )
        }
    }

    fun findInPage(
        query: String,
        forward: Boolean = true,
    ) {
        val generation = findInPageRequests.next()
        val session = activeTab?.session
        if (session == null || !session.isOpen) {
            findInPageResult = null
            return
        }

        val value = query.trim()
        if (value.isEmpty()) {
            session.getFinder().clear()
            findInPageResult = null
            return
        }

        val finder = session.getFinder()
        findInPageResult = null
        finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        val findResult =
            finder.find(
                value,
                if (forward) {
                    GeckoSession.FINDER_FIND_FORWARD
                } else {
                    GeckoSession.FINDER_FIND_BACKWARDS
                },
            )
        findResult.accept(
            { result ->
                dataHandler.post {
                    if (findInPageRequests.isCurrent(generation) && activeTab?.session === session) {
                        findInPageResult =
                            result?.let {
                                FindInPageState(it.found, it.current, it.total)
                            } ?: FindInPageState(false, 0, 0)
                    }
                }
            },
            {
                dataHandler.post {
                    if (findInPageRequests.isCurrent(generation) && activeTab?.session === session) {
                        findInPageResult = FindInPageState(false, 0, 0)
                    }
                }
            },
        )
    }

    fun findNextInPage(forward: Boolean) {
        val generation = findInPageRequests.next()
        val session = activeTab?.session
        if (session == null || !session.isOpen) {
            findInPageResult = null
            return
        }

        val finder = session.getFinder()
        val findResult =
            finder.find(
                null,
                if (forward) {
                    GeckoSession.FINDER_FIND_FORWARD
                } else {
                    GeckoSession.FINDER_FIND_BACKWARDS
                },
            )
        findResult.accept(
            { result ->
                dataHandler.post {
                    if (findInPageRequests.isCurrent(generation) && activeTab?.session === session) {
                        findInPageResult =
                            result?.let {
                                FindInPageState(it.found, it.current, it.total)
                            } ?: FindInPageState(false, 0, 0)
                    }
                }
            },
            {
                dataHandler.post {
                    if (findInPageRequests.isCurrent(generation) && activeTab?.session === session) {
                        findInPageResult = FindInPageState(false, 0, 0)
                    }
                }
            },
        )
    }

    fun clearFindInPage() {
        findInPageRequests.invalidate()
        activeTab
            ?.session
            ?.takeIf { it.isOpen }
            ?.getFinder()
            ?.clear()
        findInPageResult = null
    }

    fun printTab(tabId: String): Boolean {
        val session =
            tabs
                .firstOrNull { it.id == tabId }
                ?.session
                ?.takeIf { it.isOpen && it.printDelegate != null } ?: return false
        session.printPageContent()
        return true
    }

    fun saveTabAsPdf(
        tabId: String,
        destinationUri: Uri,
    ): Boolean {
        if (isPdfExporting) {
            pdfExportMessage = "Đang có một yêu cầu xuất PDF."
            return false
        }

        val session =
            tabs
                .firstOrNull { it.id == tabId }
                ?.session
                ?.takeIf { it.isOpen } ?: run {
                pdfExportMessage = "Trang chưa sẵn sàng để xuất PDF."
                return false
            }

        isPdfExporting = true
        pdfExportMessage = "Đang tạo PDF…"
        storageHandler.post {
            runCatching { session.saveAsPdf() }
                .onSuccess { result ->
                    result.accept(
                        { input ->
                            pdfWriterExecutor.execute {
                                val saveResult =
                                    runCatching {
                                        input?.use { source ->
                                            val output =
                                                appContext.contentResolver
                                                    .openOutputStream(destinationUri, "w")
                                                    ?: error("Unable to open the selected document.")
                                            output.use { target -> source.copyTo(target) }
                                        } ?: error("GeckoView returned an empty PDF stream.")
                                    }
                                dataHandler.post {
                                    isPdfExporting = false
                                    pdfExportMessage =
                                        if (saveResult.isSuccess) {
                                            "Đã lưu tệp PDF vào vị trí đã chọn."
                                        } else {
                                            "Không thể lưu tệp PDF. Hãy thử lại."
                                        }
                                }
                            }
                        },
                        { publishPdfExportFailure() },
                    )
                }.onFailure { publishPdfExportFailure() }
        }
        return true
    }

    fun consumePdfExportMessage() {
        pdfExportMessage = null
    }

    private fun publishPdfExportFailure() {
        dataHandler.post {
            isPdfExporting = false
            pdfExportMessage = "GeckoView không thể tạo hoặc lưu PDF cho trang này."
        }
    }

    private fun createExtensionTab(active: Boolean): GeckoSession? {
        if (tabs.size >= MAX_TABS) return null
        val tab = BrowserTab(initialUrl = START_PAGE_URL, initialTitle = "Tab tiện ích")
        tabs.add(tab)
        ensureSession(tab, loadCurrentUrl = false)
        if (active || selectedTabId.isBlank()) {
            selectTab(tab.id)
        } else {
            tab.session?.let { session ->
                session.setActive(false)
                session.setFocused(false)
                runtime.webExtensionController.setTabActive(session, false)
            }
        }
        persistTabs()
        return tab.session
    }

    private fun activateExtensionTab(session: GeckoSession) {
        tabs.firstOrNull { it.session === session }?.let { selectTab(it.id) }
    }

    private fun closeExtensionTab(session: GeckoSession): Boolean {
        val tab = tabs.firstOrNull { it.session === session } ?: return false
        closeTab(tab.id)
        return true
    }

    private fun openExtensionOptionsPage(url: String) {
        val requested = Uri.parse(url)
        val belongsToInstalledExtension =
            extensionsManager.installed.any { extension ->
                val base = Uri.parse(extension.metaData.baseUrl)
                requested.scheme == "moz-extension" &&
                    requested.host != null &&
                    requested.host == base.host
            }
        if (!belongsToInstalledExtension) {
            extensionsManager.reportStatus("Đã chặn trang tuỳ chọn không thuộc tiện ích đã cài.")
            return
        }
        if (tabs.size >= MAX_TABS) {
            extensionsManager.reportStatus("Đã đạt giới hạn 20 tab.")
            return
        }
        val tab = BrowserTab(initialUrl = url, initialTitle = "Tuỳ chọn tiện ích")
        tabs.add(tab)
        selectTab(tab.id)
        persistTabs()
    }

    private fun activateSelectedTab() {
        val selected = activeTab ?: return
        tabs.forEach(::updateSessionActivity)
        if (selected.url != START_PAGE_URL || selected.session != null) {
            ensureSession(selected)
            updateSessionActivity(selected)
        }
    }

    fun onSessionViewAttached(
        tabId: String,
        session: GeckoSession,
    ) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.session !== session || !session.isOpen) return

        tab.isViewAttached = true
        val visible =
            GeckoSessionVisibilityPolicy.isVisible(
                isAppForegrounded,
                tab.id == selectedTabId,
                tab.isViewAttached,
            )
        updateSessionActivity(tab)

        if (visible && tab.loadPendingUntilAttached) {
            val originalInput = tab.pendingOriginalInput ?: tab.url
            tab.loadPendingUntilAttached = false
            tab.pendingOriginalInput = null
            val loader =
                GeckoSession
                    .Loader()
                    .uri(tab.url)
                    .originalInput(originalInput)
            val referrerSession = tab.pendingReferrerSession
            tab.pendingReferrerSession = null
            if (referrerSession?.isOpen == true) loader.referrer(referrerSession)
            session.load(loader)
        }
    }

    fun onSessionViewDetached(
        tabId: String,
        session: GeckoSession,
    ) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.session !== session) return

        tab.isViewAttached = false
        updateSessionActivity(tab)
    }

    private fun ensureSession(
        tab: BrowserTab,
        loadCurrentUrl: Boolean = true,
    ) {
        val existing = tab.session
        if (existing != null && existing.isOpen) return

        val sessionSettings =
            GeckoSessionSettings
                .Builder()
                .usePrivateMode(tab.isPrivate)
                .useTrackingProtection(trackingProtectionEnabled)
                .userAgentMode(
                    BrowserDesktopModePolicy.settingsFor(tab.isDesktopMode).userAgentMode,
                ).viewportMode(
                    BrowserDesktopModePolicy.settingsFor(tab.isDesktopMode).viewportMode,
                ).build()
        val session = GeckoSession(sessionSettings)
        tab.session = session
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = loadCurrentUrl
        tab.pendingOriginalInput = if (loadCurrentUrl) tab.url else null

        session.navigationDelegate =
            object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean,
                ) {
                    if (tab.session !== session || url.isNullOrBlank()) return
                    if (contextMenuRequest?.tabId == tab.id && tab.url != url) {
                        dismissContextMenu()
                    }
                    tab.url = url
                    persistTabs()
                }

                override fun onCanGoBack(
                    session: GeckoSession,
                    value: Boolean,
                ) {
                    if (tab.session === session) tab.canGoBack = value
                }

                override fun onCanGoForward(
                    session: GeckoSession,
                    value: Boolean,
                ) {
                    if (tab.session === session) tab.canGoForward = value
                }

                override fun onNewSession(
                    session: GeckoSession,
                    uri: String,
                ): GeckoResult<GeckoSession>? {
                    if (tabs.size >= MAX_TABS) return null

                    val safeUrl = if (isWebUrl(uri)) uri else resolvedHomePageUrl()
                    val newTab =
                        BrowserTab(
                            initialUrl = safeUrl,
                            initialTitle = "New tab",
                            isPrivate = tab.isPrivate,
                            initialDesktopMode = tab.isDesktopMode,
                        )
                    tabs.add(newTab)
                    ensureSession(newTab, loadCurrentUrl = false)
                    selectTab(newTab.id)
                    persistTabs()
                    return newTab.session?.let { GeckoResult.fromValue(it) }
                }
            }

        session.permissionDelegate =
            object : GeckoSession.PermissionDelegate {
                override fun onAndroidPermissionsRequest(
                    session: GeckoSession,
                    permissions: Array<String>?,
                    callback: GeckoSession.PermissionDelegate.Callback,
                ) {
                    val requested = permissions?.distinct().orEmpty()
                    if (requested.isEmpty()) {
                        callback.grant()
                        return
                    }
                    if (requested.any { it !in SUPPORTED_ANDROID_PERMISSIONS }) {
                        callback.reject()
                        return
                    }

                    val denied =
                        preferences
                            .getStringSet(KEY_DENIED_ANDROID_PERMISSIONS, emptySet())
                            .orEmpty()
                            .toMutableSet()
                    val alreadyGranted =
                        requested.filterTo(mutableSetOf()) {
                            appContext.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    if (denied.removeAll(alreadyGranted)) {
                        preferences.edit().putStringSet(KEY_DENIED_ANDROID_PERMISSIONS, denied).apply()
                    }
                    if (
                        BrowserAndroidPermissionPolicy.shouldRejectPreviouslyDenied(
                            requested,
                            denied,
                            alreadyGranted,
                        )
                    ) {
                        callback.reject()
                        return
                    }

                    pendingAndroidPermissionCallback?.reject()
                    val request =
                        AndroidPermissionRequest(
                            id = UUID.randomUUID().toString(),
                            permissions = requested,
                        )
                    pendingAndroidPermissionCallback = callback
                    pendingAndroidPermissionSession = session
                    pendingAndroidPermissionRequest = request
                }

                override fun onContentPermissionRequest(
                    session: GeckoSession,
                    perm: GeckoSession.PermissionDelegate.ContentPermission,
                ): GeckoResult<Int>? {
                    val result = GeckoResult<Int>()
                    if (perm.value != GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT) {
                        result.complete(perm.value)
                        return result
                    }
                    if (pendingContentPermissionResult != null) {
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        return result
                    }

                    pendingContentPermissionResult = result
                    pendingContentPermissionSession = session
                    val origin = runCatching { Uri.parse(perm.uri).host }.getOrNull() ?: perm.uri
                    sitePermissionPrompt =
                        SitePermissionPrompt(
                            origin = origin,
                            permission = perm.permission,
                            permissionLabel = permissionLabel(perm.permission),
                            isPrivate = perm.privateMode,
                            isIrreversible =
                                perm.permission ==
                                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                        )
                    return result
                }

                override fun onMediaPermissionRequest(
                    session: GeckoSession,
                    uri: String,
                    video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                    audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                    callback: GeckoSession.PermissionDelegate.MediaCallback,
                ) {
                    val videoSources =
                        video.orEmpty().filter {
                            it.type == GeckoSession.PermissionDelegate.MediaSource.TYPE_VIDEO &&
                                it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                        }
                    val audioSources =
                        audio.orEmpty().filter {
                            it.type == GeckoSession.PermissionDelegate.MediaSource.TYPE_AUDIO &&
                                it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
                        }
                    val hasUnsupportedSource =
                        video.orEmpty().any { it !in videoSources } ||
                            audio.orEmpty().any { it !in audioSources }

                    if (hasUnsupportedSource || (videoSources.isEmpty() && audioSources.isEmpty())) {
                        callback.reject()
                        return
                    }
                    pendingMediaPermissionCallback?.reject()
                    pendingMediaPermissionCallback = callback
                    pendingMediaPermissionSession = session
                    val origin = runCatching { Uri.parse(uri).host }.getOrNull() ?: uri
                    mediaPermissionPrompt =
                        MediaPermissionPrompt(
                            origin = origin,
                            videoSources = videoSources,
                            audioSources = audioSources,
                            isPrivate = tab.isPrivate,
                        )
                }
            }

        session.contentDelegate =
            object : GeckoSession.ContentDelegate {
                override fun onContextMenu(
                    session: GeckoSession,
                    screenX: Int,
                    screenY: Int,
                    element: GeckoSession.ContentDelegate.ContextElement,
                ) {
                    if (tab.session !== session || selectedTabId != tab.id) return
                    val linkUrl = BrowserContextMenuPolicy.safeWebUrl(element.linkUri)
                    val resourceUrl = BrowserContextMenuPolicy.safeWebUrl(element.srcUri)
                    if (linkUrl == null && resourceUrl == null) {
                        dismissContextMenu()
                        return
                    }

                    val resourceType =
                        when (element.type) {
                            GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> BrowserContextResourceType.IMAGE
                            GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> BrowserContextResourceType.VIDEO
                            GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> BrowserContextResourceType.AUDIO
                            else -> BrowserContextResourceType.NONE
                        }
                    contextMenuSourceSession = session
                    contextMenuRequest =
                        BrowserContextMenuRequest(
                            tabId = tab.id,
                            linkUrl = linkUrl,
                            resourceUrl = resourceUrl,
                            resourceType = resourceType,
                            title =
                                listOfNotNull(element.title, element.altText, element.linkText)
                                    .firstOrNull(String::isNotBlank)
                                    ?.take(160),
                        )
                }

                override fun onTitleChange(
                    session: GeckoSession,
                    title: String?,
                ) {
                    if (tab.session !== session || title.isNullOrBlank()) return
                    tab.title = title
                    refreshMediaNotification(tab, session)
                    persistTabs()
                }

                override fun onCloseRequest(session: GeckoSession) {
                    if (tab.session === session) closeTab(tab.id)
                }

                override fun onFullScreen(
                    session: GeckoSession,
                    fullScreen: Boolean,
                ) {
                    if (tab.session === session) tab.isFullScreen = fullScreen
                }

                override fun onExternalResponse(
                    session: GeckoSession,
                    response: WebResponse,
                ) {
                    if (tab.session !== session) {
                        runCatching { response.body?.close() }
                        return
                    }
                    downloadManager.enqueue(response, tab.isPrivate)
                }

                override fun onCrash(session: GeckoSession) {
                    recoverAfterContentProcessDeath(
                        tab,
                        session,
                        "Trang bị dừng đột ngột. Nhấn Thử lại để tải lại trang.",
                    )
                }

                override fun onKill(session: GeckoSession) {
                    recoverAfterContentProcessDeath(
                        tab,
                        session,
                        "Android đã dừng tiến trình trang. Nhấn Thử lại để tải lại trang.",
                    )
                }
            }

        session.setPictureInPictureDelegate(
            object : PictureInPictureController.SessionPictureInPicture.Delegate {
                override fun onEnterPictureInPicture(
                    session: GeckoSession,
                    placementToken: String,
                ) {
                    if (tab.session === session) {
                        startPictureInPicture(session, placementToken)
                    }
                }

                override fun onExitPictureInPicture(session: GeckoSession) {
                    requestPictureInPictureWindowClose(
                        sourceSession = session,
                        geckoAlreadyHandledExit = true,
                    )
                }
            },
        )

        session.setMediaSessionDelegate(
            object : MediaSession.Delegate {
                override fun onActivated(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                ) {
                    rememberMediaSession(tab, session, mediaSession)
                }

                override fun onDeactivated(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                ) {
                    clearMediaPlaybackSession(session)
                }

                override fun onPlay(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                ) {
                    updateMediaPlaybackState(tab, session, mediaSession, isPlaying = true)
                }

                override fun onPause(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                ) {
                    if (activeMediaGeckoSession === session) {
                        updateMediaPlaybackState(tab, session, mediaSession, isPlaying = false)
                    }
                }

                override fun onStop(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                ) {
                    clearMediaPlaybackSession(session)
                }

                override fun onMetadata(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                    meta: MediaSession.Metadata,
                ) {
                    if (activeMediaGeckoSession === session) {
                        refreshMediaNotification(tab, session)
                    }
                }

                override fun onFullscreen(
                    session: GeckoSession,
                    mediaSession: MediaSession,
                    enabled: Boolean,
                    meta: MediaSession.ElementMetadata?,
                ) {
                    if (tab.session === session) tab.isFullScreen = enabled
                }
            },
        )

        session.progressDelegate =
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    if (tab.session !== session) return
                    if (tab.id == selectedTabId) {
                        findInPageRequests.invalidate()
                        findInPageResult = null
                    }
                    tab.previewBitmap = null
                    tab.pageSecurity = null
                    tab.isLoading = true
                    tab.progress = 0
                    tab.error = null
                }

                override fun onProgressChange(
                    session: GeckoSession,
                    progress: Int,
                ) {
                    if (tab.session === session) {
                        tab.progress = progress.coerceIn(0, 100)
                    }
                }

                override fun onSecurityChange(
                    session: GeckoSession,
                    securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
                ) {
                    if (tab.session !== session) return
                    tab.pageSecurity =
                        BrowserPageSecurity(
                            isSecure = securityInfo.isSecure,
                            isException = securityInfo.isException,
                            activeMixedContentLoaded =
                                securityInfo.mixedModeActive ==
                                    GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                            passiveMixedContentLoaded =
                                securityInfo.mixedModePassive ==
                                    GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                        )
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    if (tab.session !== session) return
                    tab.isLoading = false
                    tab.progress = if (success) 100 else 0
                    if (success) {
                        tab.error = null
                        recordVisit(tab)
                        if (!tab.isPrivate) refreshPrivacyStats()
                    } else {
                        tab.error = "This page could not be loaded. Check the connection and retry."
                    }
                }
            }

        session.open(runtime)
        extensionsManager.attachSession(session)
        updateSessionActivity(tab)
    }

    private fun recoverAfterContentProcessDeath(
        tab: BrowserTab,
        failedSession: GeckoSession,
        message: String,
    ) {
        if (tab.session !== failedSession) return

        // GeckoView closes a session before delivering onCrash/onKill. It must not
        // remain attached to Compose's AndroidView or be reused for the retry.
        clearMediaPlaybackSession(failedSession)
        if (pictureInPictureSourceSession === failedSession) {
            closePictureInPictureForSourceSession(
                sourceSession = failedSession,
                notifyGecko = false,
            )
        }
        if (contextMenuSourceSession === failedSession ||
            contextMenuRequest?.tabId == tab.id
        ) {
            dismissContextMenu()
        }
        tab.pendingReferrerSession = null
        tabs
            .filter { it.pendingReferrerSession === failedSession }
            .forEach { it.pendingReferrerSession = null }
        extensionsManager.detachSession(failedSession)
        tab.session = null
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = false
        tab.pendingOriginalInput = tab.url
        tab.isFullScreen = false
        tab.pageSecurity = null
        tab.isLoading = false
        tab.progress = 0
        tab.error = message
    }

    private fun destroySession(tab: BrowserTab) {
        val session =
            tab.session ?: run {
                tab.isFullScreen = false
                tab.pendingReferrerSession = null
                if (contextMenuRequest?.tabId == tab.id) dismissContextMenu()
                return
            }
        clearMediaPlaybackSession(session)
        if (pictureInPictureSourceSession === session) {
            closePictureInPictureForSourceSession(
                sourceSession = session,
                notifyGecko = true,
            )
        }
        tab.isFullScreen = false
        tab.pageSecurity = null
        tab.pendingReferrerSession = null
        if (contextMenuRequest?.tabId == tab.id || contextMenuSourceSession === session) {
            dismissContextMenu()
        }
        tabs
            .filter { it.pendingReferrerSession === session }
            .forEach { it.pendingReferrerSession = null }

        if (pendingContentPermissionSession === session) {
            pendingContentPermissionResult?.complete(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
            )
            pendingContentPermissionResult = null
            pendingContentPermissionSession = null
            sitePermissionPrompt = null
        }
        if (pendingMediaPermissionSession === session) {
            pendingMediaPermissionCallback?.reject()
            pendingMediaPermissionCallback = null
            pendingMediaPermissionSession = null
            mediaPermissionPrompt = null
        }
        if (pendingAndroidPermissionSession === session) {
            pendingAndroidPermissionCallback?.reject()
            pendingAndroidPermissionCallback = null
            pendingAndroidPermissionSession = null
            pendingAndroidPermissionRequest = null
        }

        session.setFocused(false)
        session.setActive(false)
        session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        extensionsManager.detachSession(session)
        session.navigationDelegate = null
        session.contentDelegate = null
        session.permissionDelegate = null
        session.progressDelegate = null
        session.pictureInPictureDelegate = null
        if (session.isOpen) session.close()
        tab.session = null
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = false
        tab.pendingOriginalInput = null
    }

    private fun recordVisit(tab: BrowserTab) {
        if (!shouldRecordBrowserHistory(tab) || !isWebUrl(tab.url)) return

        val visit =
            BrowserEntry(
                title = tab.title.ifBlank { tab.url },
                url = tab.url,
                timestamp = System.currentTimeMillis(),
            )
        if (isHistoryLoaded) {
            history.removeAll { it.url == tab.url }
            history.add(0, visit)
            while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
            persistHistory()
        } else {
            // Preserve stored history without loading it into the startup UI. If a
            // history screen finishes loading before this write, merge the visit into
            // memory too; a clear operation invalidates this deferred UI update.
            val clearGeneration = historyClearGeneration
            dataExecutor.execute {
                localStore.recordHistoryVisit(visit, MAX_HISTORY)
                dataHandler.post {
                    if (historyClearGeneration != clearGeneration || !isHistoryLoaded) {
                        return@post
                    }
                    history.removeAll { it.url == visit.url }
                    history.add(0, visit)
                    while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
                }
            }
        }
    }

    private fun restoreTabs() {
        val restored = localStore.restoreTabs(resolvedHomePageUrl(), MAX_TABS)
        tabs.addAll(restored.tabs)
        tabGroups.addAll(restored.groups)
        selectedTabId = restored.selectedTabId
    }

    private fun persistTabs() {
        localStore.saveTabs(
            tabs = tabs.toList(),
            selectedTabId = selectedTabId,
            defaultUrl = resolvedHomePageUrl(),
            groups = tabGroups.toList(),
        )
    }

    private fun persistHistory() {
        val snapshot = history.toList()
        dataExecutor.execute {
            localStore.saveHistory(snapshot)
        }
    }

    private fun persistBookmarks() {
        val snapshot = bookmarks.toList()
        dataExecutor.execute {
            localStore.saveBookmarks(snapshot)
        }
    }

    private fun persistBookmarkFolders() {
        val snapshot = bookmarkFolders.toList()
        dataExecutor.execute {
            localStore.saveBookmarkFolders(snapshot)
        }
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)

    private val findInPageRequests = BrowserRequestGeneration()

    private companion object {
        const val PREFERENCES_NAME = "cat_browser_local_state"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_HOME_PAGE = "home_page"
        const val KEY_STARTUP_MODE = "startup_mode"
        const val KEY_TRACKING_PROTECTION = "tracking_protection_enabled"
        const val KEY_DENIED_ANDROID_PERMISSIONS = "denied_android_permissions"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val PRIVACY_STATS_RANGE_MS = 7L * 24L * 60L * 60L * 1000L
        const val PRIVACY_STATS_REFRESH_INTERVAL_MS = 15_000L
        const val DEVELOPER_UNLOCK_CODE = "dev-cat"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val SEARCH_GOOGLE = "google"
        const val SEARCH_DUCKDUCKGO = "duckduckgo"
        const val SEARCH_BING = "bing"
        const val SEARCH_BRAVE = "brave"
        const val STARTUP_CONTINUE = "continue"
        const val STARTUP_HOME = "home"
        const val MAX_TABS = 20
        const val MIN_TABS_PER_GROUP = 2
        const val MAX_HISTORY = 200
        const val MAX_RECENTLY_CLOSED = 10
        val SUPPORTED_ANDROID_PERMISSIONS =
            setOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
    }
}
