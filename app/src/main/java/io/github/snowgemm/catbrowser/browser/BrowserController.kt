package io.github.snowgemm.catbrowser.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

const val START_PAGE_URL = "https://duckduckgo.com/"

data class BrowserEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

class BrowserTab internal constructor(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = START_PAGE_URL,
    initialTitle: String = "New tab"
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

class BrowserController(
    context: Context,
    private val runtime: GeckoRuntime
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    val tabs = mutableStateListOf<BrowserTab>()
    val history = mutableStateListOf<BrowserEntry>()
    val bookmarks = mutableStateListOf<BrowserEntry>()

    var selectedTabId by mutableStateOf("")
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == selectedTabId }

    init {
        restoreCollections()
        restoreTabs()

        if (tabs.isEmpty()) {
            tabs.add(BrowserTab())
        }

        if (tabs.none { it.id == selectedTabId }) {
            selectedTabId = tabs.first().id
        }

        persistTabs()
        activateSelectedTab()
    }

    fun newTab() {
        if (tabs.size >= MAX_TABS) return

        val tab = BrowserTab()
        tabs.add(tab)
        selectTab(tab.id)
    }

    fun selectTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (selectedTabId == tabId) {
            ensureSession(target)
            return
        }

        activeTab?.session?.let { session ->
            session.setFocused(false)
            session.setActive(false)
        }

        selectedTabId = target.id
        ensureSession(target)
        target.session?.let { session ->
            session.setActive(true)
            session.setFocused(target.isViewAttached)
        }
        persistTabs()
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return

        val wasSelected = selectedTabId == tabId
        val removed = tabs.removeAt(index)
        destroySession(removed)

        if (tabs.isEmpty()) {
            val replacement = BrowserTab()
            tabs.add(replacement)
            selectedTabId = replacement.id
            ensureSession(replacement)
        } else if (wasSelected) {
            val replacementIndex = index.coerceAtMost(tabs.lastIndex)
            selectedTabId = tabs[replacementIndex].id
            activateSelectedTab()
        }

        persistTabs()
    }

    fun navigate(input: String) {
        val destination = BrowserUrlResolver.resolve(input) ?: return
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
        navigate(START_PAGE_URL)
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

    fun toggleBookmark() {
        val tab = activeTab ?: return
        if (!isWebUrl(tab.url)) return

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

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    fun openEntry(url: String) {
        navigate(url)
    }

    private fun activateSelectedTab() {
        val selected = activeTab ?: return
        tabs.forEach { tab ->
            tab.session?.let { session ->
                val active = tab.id == selected.id
                session.setActive(active)
                session.setFocused(active && tab.isViewAttached)
            }
        }
        ensureSession(selected)
        selected.session?.setActive(true)
        selected.session?.setFocused(selected.isViewAttached)
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

        val session = GeckoSession()
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

                val safeUrl = if (isWebUrl(uri)) uri else START_PAGE_URL
                val newTab = BrowserTab(
                    initialUrl = safeUrl,
                    initialTitle = "New tab"
                )
                tabs.add(newTab)
                ensureSession(newTab, loadCurrentUrl = false)
                selectTab(newTab.id)
                persistTabs()
                return newTab.session?.let { GeckoResult.fromValue(it) }
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
        val isSelected = tab.id == selectedTabId
        session.setActive(isSelected)
        session.setFocused(isSelected && tab.isViewAttached)
    }

    private fun destroySession(tab: BrowserTab) {
        val session = tab.session ?: return
        session.setFocused(false)
        session.setActive(false)
        session.navigationDelegate = null
        session.contentDelegate = null
        session.progressDelegate = null
        if (session.isOpen) session.close()
        tab.session = null
        tab.isViewAttached = false
        tab.loadPendingUntilAttached = false
        tab.pendingOriginalInput = null
    }

    private fun recordVisit(tab: BrowserTab) {
        if (!isWebUrl(tab.url)) return

        history.removeAll { it.url == tab.url }
        history.add(
            0,
            BrowserEntry(
                title = tab.title.ifBlank { tab.url },
                url = tab.url,
                timestamp = System.currentTimeMillis()
            )
        )
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        persistHistory()
    }

    private fun restoreTabs() {
        val saved = try {
            JSONArray(preferences.getString(KEY_TABS, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        for (index in 0 until minOf(saved.length(), MAX_TABS)) {
            val item = saved.optJSONObject(index) ?: continue
            val url = item.optString("url", START_PAGE_URL)
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

    private fun restoreCollections() {
        val savedHistory = readEntries(KEY_HISTORY)
        history.addAll(savedHistory.sortedByDescending { it.timestamp }.take(MAX_HISTORY))

        val savedBookmarks = readEntries(KEY_BOOKMARKS)
        bookmarks.addAll(savedBookmarks.sortedByDescending { it.timestamp })
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
        val array = JSONArray()
        tabs.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", if (isWebUrl(tab.url)) tab.url else START_PAGE_URL)
                    .put("title", tab.title.take(200))
            )
        }

        preferences.edit()
            .putString(KEY_TABS, array.toString())
            .putString(KEY_ACTIVE_TAB, selectedTabId)
            .apply()
    }

    private fun persistHistory() {
        preferences.edit().putString(KEY_HISTORY, entriesToJson(history).toString()).apply()
    }

    private fun persistBookmarks() {
        preferences.edit().putString(KEY_BOOKMARKS, entriesToJson(bookmarks).toString()).apply()
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
        const val MAX_TABS = 20
        const val MAX_HISTORY = 200
    }
}
