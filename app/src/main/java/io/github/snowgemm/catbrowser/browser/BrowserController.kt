package io.github.snowgemm.catbrowser.browser

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import java.util.UUID
import java.util.concurrent.Executors

const val START_PAGE_URL = "about:blank"
const val DEFAULT_HOME_PAGE_URL = "about:blank"
private const val LEGACY_DEFAULT_HOME_PAGE_URL = "https://duckduckgo.com/"

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

class BrowserTab internal constructor(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = START_PAGE_URL,
    initialTitle: String = "New tab",
    val isPrivate: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
        internal set
    var title by mutableStateOf(initialTitle)
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
    var session by mutableStateOf<GeckoSession?>(null)
        internal set
    var isViewAttached = false
        internal set
    var loadPendingUntilAttached = false
        internal set
    var pendingOriginalInput: String? = null
        internal set
}

internal fun shouldPersistBrowserTab(tab: BrowserTab): Boolean = !tab.isPrivate
internal fun shouldRecordBrowserHistory(tab: BrowserTab): Boolean = !tab.isPrivate
internal fun shouldAllowBookmark(tab: BrowserTab): Boolean = !tab.isPrivate

internal fun areRequestedAndroidPermissionsGranted(
    requestedPermissions: List<String>,
    grantResults: Map<String, Boolean>
): Boolean {
    val locationPermissions = setOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    val requestedLocation = requestedPermissions.any { it in locationPermissions }
    val locationGranted = requestedPermissions
        .filter { it in locationPermissions }
        .any { grantResults[it] == true }
    val otherPermissionsGranted = requestedPermissions
        .filterNot { it in locationPermissions }
        .all { grantResults[it] == true }

    return (!requestedLocation || locationGranted) && otherPermissionsGranted
}

class BrowserController(
    context: Context,
    private val runtime: GeckoRuntime
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var themeMode by mutableStateOf(preferences.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
        private set
    var searchEngine by mutableStateOf(preferences.getString(KEY_SEARCH_ENGINE, SEARCH_DUCKDUCKGO) ?: SEARCH_DUCKDUCKGO)
        private set
    var homePage by mutableStateOf(
        preferences.getString(KEY_HOME_PAGE, DEFAULT_HOME_PAGE_URL)
            ?.takeUnless { it == LEGACY_DEFAULT_HOME_PAGE_URL }
            ?: DEFAULT_HOME_PAGE_URL
    )
        private set
    var startupMode by mutableStateOf(preferences.getString(KEY_STARTUP_MODE, STARTUP_CONTINUE) ?: STARTUP_CONTINUE)
        private set

    val downloadManager = BrowserDownloadManager(context.applicationContext)
    private val storageController = runtime.getStorageController()

    val tabs = mutableStateListOf<BrowserTab>()
    val history = mutableStateListOf<BrowserEntry>()
    val bookmarks = mutableStateListOf<BrowserEntry>()
    val extensionsManager = BrowserExtensionsManager(
        runtime = runtime,
        context = context.applicationContext,
        downloadManager = downloadManager,
        sessionProvider = { tabs.mapNotNull { it.session } },
        onCreateTabFromExtension = ::createExtensionTab,
        onActivateSession = ::activateExtensionTab,
        onCloseSession = ::closeExtensionTab,
        onOpenOptionsPage = ::openExtensionOptionsPage
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
    var pendingAndroidPermissionRequest by mutableStateOf<AndroidPermissionRequest?>(null)
        private set
    var findInPageResult by mutableStateOf<FindInPageState?>(null)
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
    private val dataExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "CatBrowserData").apply { isDaemon = true }
    }
    private var pendingBookmarkToggle = false

    var selectedTabId by mutableStateOf("")
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == selectedTabId }

    val privateTabCount: Int
        get() = tabs.count { it.isPrivate }

    val regularTabCount: Int
        get() = tabs.size - privateTabCount

    init {
        if (preferences.getString(KEY_HOME_PAGE, null) == LEGACY_DEFAULT_HOME_PAGE_URL) {
            preferences.edit().putString(KEY_HOME_PAGE, DEFAULT_HOME_PAGE_URL).apply()
        }
        if (startupMode == STARTUP_CONTINUE) restoreTabs()

        if (tabs.isEmpty()) {
            tabs.add(BrowserTab(initialUrl = homePage))
        }

        if (tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.first().id
        }

        persistTabs()
        activateSelectedTab()
        extensionsManager.refreshInstalled()
    }

    fun newTab() {
        if (tabs.size >= MAX_TABS) return

        val tab = BrowserTab(initialUrl = homePage)
        tabs.add(tab)
        selectTab(tab.id)
    }

    fun newPrivateTab() {
        if (tabs.size >= MAX_TABS) return

        val tab = BrowserTab(
            initialUrl = homePage,
            initialTitle = "Tab ẩn danh",
            isPrivate = true
        )
        tabs.add(tab)
        selectTab(tab.id)
    }

    fun selectTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (selectedTabId == tabId) {
            if (target.url != START_PAGE_URL || target.session != null) ensureSession(target)
            target.session?.let { runtime.webExtensionController.setTabActive(it, true) }
            return
        }

        activeTab?.session?.let { session ->
            runtime.webExtensionController.setTabActive(session, false)
            session.setFocused(false)
            session.setActive(false)
        }

        selectedTabId = target.id
        if (target.url != START_PAGE_URL || target.session != null) {
            ensureSession(target)
            target.session?.let { session ->
                session.setActive(true)
                session.setFocused(target.isViewAttached)
                runtime.webExtensionController.setTabActive(session, true)
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
        if (shouldPersistBrowserTab(removed) && isWebUrl(removed.url)) {
            recentlyClosedTabs.add(
                0,
                BrowserEntry(
                    title = removed.title.ifBlank { removed.url },
                    url = removed.url,
                    timestamp = System.currentTimeMillis()
                )
            )
            while (recentlyClosedTabs.size > MAX_RECENTLY_CLOSED) {
                recentlyClosedTabs.removeAt(recentlyClosedTabs.lastIndex)
            }
        }
        destroySession(removed)

        if (tabs.isEmpty()) {
            val replacement = BrowserTab(initialUrl = homePage)
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
            val replacement = BrowserTab(initialUrl = homePage)
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

    private fun navigateToUrl(destination: String, originalInput: String) {
        val tab = activeTab ?: return
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
                GeckoSession.Loader()
                    .uri(destination)
                    .originalInput(originalInput)
            )
        } else {
            tab.loadPendingUntilAttached = true
            tab.pendingOriginalInput = originalInput
        }
        persistTabs()
    }

    fun goBack() {
        activeTab?.takeIf { it.canGoBack }?.session?.goBack()
    }

    fun goForward() {
        activeTab?.takeIf { it.canGoForward }?.session?.goForward()
    }

    fun goHome() {
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
            val entries = runCatching {
                readEntries(KEY_HISTORY)
                    .sortedByDescending { it.timestamp }
                    .take(MAX_HISTORY)
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
            val entries = runCatching {
                readEntries(KEY_BOOKMARKS).sortedByDescending { it.timestamp }
            }.getOrDefault(emptyList())

            dataHandler.post {
                if (!isBookmarksLoaded) bookmarks.addAll(entries)
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
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        persistBookmarks()
    }

    fun addBookmark(title: String, inputUrl: String): Boolean {
        if (!isBookmarksLoaded) {
            loadBookmarks()
            return false
        }
        val url = BrowserUrlResolver.resolveAddress(inputUrl) ?: return false
        if (!isWebUrl(url)) return false
        val cleanTitle = title.trim().take(100).ifBlank {
            Uri.parse(url).host ?: url
        }
        bookmarks.removeAll { it.url == url }
        bookmarks.add(
            0,
            BrowserEntry(cleanTitle, url, System.currentTimeMillis())
        )
        persistBookmarks()
        return true
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun clearHistory() {
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

    fun setHomePage(input: String): Boolean {
        val normalized = if (input.trim().equals("about:blank", ignoreCase = true)) {
            DEFAULT_HOME_PAGE_URL
        } else {
            BrowserUrlResolver.resolveAddress(input) ?: return false
        }
        homePage = normalized
        preferences.edit().putString(KEY_HOME_PAGE, normalized).apply()
        return true
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
            }
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
                prompt.audioSources.firstOrNull()
            )
        } else {
            callback?.reject()
        }
    }

    fun resolveAndroidPermissionRequest(granted: Boolean) {
        val callback = pendingAndroidPermissionCallback
        pendingAndroidPermissionCallback = null
        pendingAndroidPermissionSession = null
        pendingAndroidPermissionRequest = null
        if (granted) callback?.grant() else callback?.reject()
    }

    fun loadSitePermissions() {
        if (isSitePermissionsLoading) return
        isSitePermissionsLoading = true
        sitePermissionsMessage = null
        val requestVersion = ++sitePermissionsLoadVersion
        storageHandler.post {
            storageController.getAllPermissions().accept(
                { permissions ->
                    val entries = permissions.orEmpty().map { permission ->
                        val host = runCatching { Uri.parse(permission.uri).host }.getOrNull()
                        SitePermissionEntry(
                            permission = permission,
                            origin = host ?: permission.uri,
                            permissionLabel = permissionLabel(permission.permission),
                            stateLabel = when (permission.value) {
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> "Được phép"
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> "Bị chặn"
                                else -> "Mặc định"
                            },
                            isPrivate = permission.privateMode,
                            canReset = permission.permission !=
                                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
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
                }
            )
        }
    }

    fun resetSitePermission(entry: SitePermissionEntry) {
        if (!entry.canReset) return
        dataExecutor.execute {
            runCatching {
                storageController.setPermission(
                    entry.permission,
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
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

    private fun permissionLabel(permission: Int): String = when (permission) {
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
        clearSitePermissions: Boolean
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
            tabs.forEach { it.session?.purgeHistory() }
            history.clear()
            isHistoryLoaded = true
            isHistoryLoading = false
            preferences.edit().remove(KEY_HISTORY).apply()
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
                }
            )
        }
    }

    fun findInPage(query: String, forward: Boolean = true) {
        val session = activeTab?.session ?: return
        val value = query.trim()
        if (value.isEmpty()) {
            clearFindInPage()
            return
        }

        val finder = session.getFinder()
        findInPageResult = null
        finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        finder.find(
            value,
            if (forward) GeckoSession.FINDER_FIND_FORWARD
            else GeckoSession.FINDER_FIND_BACKWARDS
        ).accept(
            { result ->
                dataHandler.post {
                    findInPageResult = result?.let {
                        FindInPageState(it.found, it.current, it.total)
                    } ?: FindInPageState(false, 0, 0)
                }
            },
            {
                dataHandler.post { findInPageResult = FindInPageState(false, 0, 0) }
            }
        )
    }

    fun findNextInPage(forward: Boolean) {
        val finder = activeTab?.session?.getFinder() ?: return
        finder.find(
            null,
            if (forward) GeckoSession.FINDER_FIND_FORWARD
            else GeckoSession.FINDER_FIND_BACKWARDS
        ).accept(
            { result ->
                dataHandler.post {
                    findInPageResult = result?.let {
                        FindInPageState(it.found, it.current, it.total)
                    } ?: FindInPageState(false, 0, 0)
                }
            },
            {
                dataHandler.post { findInPageResult = FindInPageState(false, 0, 0) }
            }
        )
    }

    fun clearFindInPage() {
        activeTab?.session?.getFinder()?.clear()
        findInPageResult = null
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
        val belongsToInstalledExtension = extensionsManager.installed.any { extension ->
            val base = Uri.parse(extension.metaData.baseUrl)
            requested.scheme == "moz-extension" &&
                requested.host != null && requested.host == base.host
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
        tabs.forEach { tab ->
            tab.session?.let { session ->
                val active = tab.id == selected.id
                session.setActive(active)
                session.setFocused(active && tab.isViewAttached)
                runtime.webExtensionController.setTabActive(session, active)
            }
        }
        if (selected.url != START_PAGE_URL || selected.session != null) {
            ensureSession(selected)
            selected.session?.setActive(true)
            selected.session?.setFocused(selected.isViewAttached)
        }
    }

    fun onSessionViewAttached(tabId: String, session: GeckoSession) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.session !== session || !session.isOpen) return

        tab.isViewAttached = true
        val isSelected = tab.id == selectedTabId
        session.setActive(isSelected)
        session.setFocused(isSelected)

        if (isSelected && tab.loadPendingUntilAttached) {
            val originalInput = tab.pendingOriginalInput ?: tab.url
            tab.loadPendingUntilAttached = false
            tab.pendingOriginalInput = null
            session.load(
                GeckoSession.Loader()
                    .uri(tab.url)
                    .originalInput(originalInput)
            )
        }
    }

    fun onSessionViewDetached(tabId: String, session: GeckoSession) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.session !== session) return

        tab.isViewAttached = false
        if (session.isOpen) session.setFocused(false)
    }

    private fun ensureSession(tab: BrowserTab, loadCurrentUrl: Boolean = true) {
        val existing = tab.session
        if (existing != null && existing.isOpen) return

        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(tab.isPrivate)
                .build()
        )
        tab.session = session
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = loadCurrentUrl
        tab.pendingOriginalInput = if (loadCurrentUrl) tab.url else null

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (tab.session !== session || url.isNullOrBlank()) return
                tab.url = url
                persistTabs()
            }

            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                if (tab.session === session) tab.canGoBack = value
            }

            override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                if (tab.session === session) tab.canGoForward = value
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String
            ): GeckoResult<GeckoSession>? {
                if (tabs.size >= MAX_TABS) return null

                val safeUrl = if (isWebUrl(uri)) uri else homePage
                val newTab = BrowserTab(
                    initialUrl = safeUrl,
                    initialTitle = "New tab",
                    isPrivate = tab.isPrivate
                )
                tabs.add(newTab)
                ensureSession(newTab, loadCurrentUrl = false)
                selectTab(newTab.id)
                persistTabs()
                return newTab.session?.let { GeckoResult.fromValue(it) }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback
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

                pendingAndroidPermissionCallback?.reject()
                val request = AndroidPermissionRequest(
                    id = UUID.randomUUID().toString(),
                    permissions = requested
                )
                pendingAndroidPermissionCallback = callback
                pendingAndroidPermissionSession = session
                pendingAndroidPermissionRequest = request
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
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
                sitePermissionPrompt = SitePermissionPrompt(
                    origin = origin,
                    permission = perm.permission,
                    permissionLabel = permissionLabel(perm.permission),
                    isPrivate = perm.privateMode,
                    isIrreversible = perm.permission ==
                        GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
                )
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val videoSources = video.orEmpty().filter {
                    it.type == GeckoSession.PermissionDelegate.MediaSource.TYPE_VIDEO &&
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                }
                val audioSources = audio.orEmpty().filter {
                    it.type == GeckoSession.PermissionDelegate.MediaSource.TYPE_AUDIO &&
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
                }
                val hasUnsupportedSource = video.orEmpty().any { it !in videoSources } ||
                    audio.orEmpty().any { it !in audioSources }

                if (hasUnsupportedSource || (videoSources.isEmpty() && audioSources.isEmpty())) {
                    callback.reject()
                    return
                }
                pendingMediaPermissionCallback?.reject()
                pendingMediaPermissionCallback = callback
                pendingMediaPermissionSession = session
                val origin = runCatching { Uri.parse(uri).host }.getOrNull() ?: uri
                mediaPermissionPrompt = MediaPermissionPrompt(
                    origin = origin,
                    videoSources = videoSources,
                    audioSources = audioSources,
                    isPrivate = tab.isPrivate
                )
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (tab.session !== session || title.isNullOrBlank()) return
                tab.title = title
                persistTabs()
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (tab.session === session) closeTab(tab.id)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                if (tab.session !== session) {
                    runCatching { response.body?.close() }
                    return
                }
                downloadManager.enqueue(response, tab.isPrivate)
            }

            override fun onCrash(session: GeckoSession) {
                if (tab.session !== session) return
                tab.isLoading = false
                tab.progress = 0
                tab.error = "The page process stopped. Reload to continue."
            }

            override fun onKill(session: GeckoSession) {
                if (tab.session !== session) return
                tab.isLoading = false
                tab.progress = 0
                tab.error = "The page process was stopped. Reload to continue."
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (tab.session !== session) return
                tab.isLoading = true
                tab.progress = 0
                tab.error = null
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (tab.session === session) {
                    tab.progress = progress.coerceIn(0, 100)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (tab.session !== session) return
                tab.isLoading = false
                tab.progress = if (success) 100 else 0
                if (success) {
                    tab.error = null
                    recordVisit(tab)
                } else {
                    tab.error = "This page could not be loaded. Check the connection and retry."
                }
            }
        }

        session.open(runtime)
        extensionsManager.attachSession(session)
        val isSelected = tab.id == selectedTabId
        session.setActive(isSelected)
        session.setFocused(isSelected && tab.isViewAttached)
    }

    private fun destroySession(tab: BrowserTab) {
        val session = tab.session ?: return

        if (pendingContentPermissionSession === session) {
            pendingContentPermissionResult?.complete(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
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
        session.navigationDelegate = null
        session.contentDelegate = null
        session.permissionDelegate = null
        session.progressDelegate = null
        if (session.isOpen) session.close()
        tab.session = null
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = false
        tab.pendingOriginalInput = null
    }

    private fun recordVisit(tab: BrowserTab) {
        if (!shouldRecordBrowserHistory(tab) || !isWebUrl(tab.url)) return

        val visit = BrowserEntry(
            title = tab.title.ifBlank { tab.url },
            url = tab.url,
            timestamp = System.currentTimeMillis()
        )
        if (isHistoryLoaded) {
            history.removeAll { it.url == tab.url }
            history.add(0, visit)
            while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
            persistHistory()
        } else {
            // Preserve the existing history without loading it into the startup UI.
            dataExecutor.execute {
                val saved = runCatching { readEntries(KEY_HISTORY).toMutableList() }
                    .getOrDefault(mutableListOf())
                saved.removeAll { it.url == visit.url }
                saved.add(0, visit)
                writeEntries(KEY_HISTORY, saved.sortedByDescending { it.timestamp }.take(MAX_HISTORY))
            }
        }
    }

    private fun restoreTabs() {
        val saved = try {
            JSONArray(preferences.getString(KEY_TABS, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        for (index in 0 until minOf(saved.length(), MAX_TABS)) {
            val item = saved.optJSONObject(index) ?: continue
            val url = item.optString("url", homePage)
            if (!isWebUrl(url)) continue

            tabs.add(
                BrowserTab(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    initialUrl = url,
                    initialTitle = item.optString("title").ifBlank { "Restored tab" }
                )
            )
        }
        selectedTabId = preferences.getString(KEY_ACTIVE_TAB, "").orEmpty()
    }

    private fun readEntries(key: String): List<BrowserEntry> {
        val array = try {
            JSONArray(preferences.getString(key, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!isWebUrl(url)) continue
                add(
                    BrowserEntry(
                        title = item.optString("title").ifBlank { url },
                        url = url,
                        timestamp = item.optLong("timestamp")
                    )
                )
            }
        }
    }

    private fun persistTabs() {
        // Private-tab URLs and titles must never enter app-owned persistent storage.
        val persistentTabs = tabs.filter(::shouldPersistBrowserTab)
        val persistentSelectedTabId = selectedTabId
            .takeIf { id -> persistentTabs.any { it.id == id } }
            ?: persistentTabs.firstOrNull()?.id.orEmpty()

        val array = JSONArray()
        persistentTabs.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", if (isWebUrl(tab.url)) tab.url else homePage)
                    .put("title", tab.title.take(200))
            )
        }

        preferences.edit()
            .putString(KEY_TABS, array.toString())
            .putString(KEY_ACTIVE_TAB, persistentSelectedTabId)
            .apply()
    }

    private fun persistHistory() {
        preferences.edit().putString(KEY_HISTORY, entriesToJson(history).toString()).apply()
    }

    private fun persistBookmarks() {
        val snapshot = bookmarks.toList()
        dataExecutor.execute {
            writeEntries(KEY_BOOKMARKS, snapshot)
        }
    }

    private fun writeEntries(key: String, entries: List<BrowserEntry>) {
        preferences.edit().putString(key, entriesToJson(entries).toString()).apply()
    }

    private fun entriesToJson(entries: List<BrowserEntry>): JSONArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("title", entry.title.take(200))
                    .put("url", entry.url)
                    .put("timestamp", entry.timestamp)
            )
        }
        return array
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)

    private companion object {
        const val PREFERENCES_NAME = "cat_browser_local_state"
        const val KEY_TABS = "tabs"
        const val KEY_ACTIVE_TAB = "active_tab"
        const val KEY_HISTORY = "history"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_HOME_PAGE = "home_page"
        const val KEY_STARTUP_MODE = "startup_mode"
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
        const val MAX_HISTORY = 200
        const val MAX_RECENTLY_CLOSED = 10
        val SUPPORTED_ANDROID_PERMISSIONS = setOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
