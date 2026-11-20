package io.github.snowgemm.catbrowser.browser

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RestoredBrowserTabs(
    val tabs: List<BrowserTab>,
    val selectedTabId: String
)

/**
 * Persists browser-owned local data while keeping private tabs out of storage.
 *
 * The existing SharedPreferences keys and JSON shape are intentionally retained
 * so upgrades do not require a data migration.
 */
internal class BrowserLocalStore(
    private val preferences: SharedPreferences
) {
    fun restoreTabs(defaultUrl: String, maxTabs: Int): RestoredBrowserTabs {
        val saved = try {
            JSONArray(preferences.getString(KEY_TABS, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val restoredTabs = buildList {
            for (index in 0 until minOf(saved.length(), maxTabs)) {
                val item = saved.optJSONObject(index) ?: continue
                val url = item.optString("url", defaultUrl)
                if (!isWebUrl(url)) continue

                add(
                    BrowserTab(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        initialUrl = url,
                        initialTitle = item.optString("title").ifBlank { "Restored tab" }
                    )
                )
            }
        }
        return RestoredBrowserTabs(
            tabs = restoredTabs,
            selectedTabId = preferences.getString(KEY_ACTIVE_TAB, "").orEmpty()
        )
    }

    fun readHistory(maxEntries: Int): List<BrowserEntry> =
        readEntries(KEY_HISTORY)
            .sortedByDescending { it.timestamp }
            .take(maxEntries)

    fun readBookmarks(): List<BrowserEntry> =
        readEntries(KEY_BOOKMARKS).sortedByDescending { it.timestamp }

    fun saveTabs(tabs: List<BrowserTab>, selectedTabId: String, defaultUrl: String) {
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
                    .put("url", if (isWebUrl(tab.url)) tab.url else defaultUrl)
                    .put("title", tab.title.take(200))
            )
        }

        preferences.edit()
            .putString(KEY_TABS, array.toString())
            .putString(KEY_ACTIVE_TAB, persistentSelectedTabId)
            .apply()
    }

    fun saveHistory(entries: List<BrowserEntry>) {
        writeEntries(KEY_HISTORY, entries)
    }

    fun saveBookmarks(entries: List<BrowserEntry>) {
        writeEntries(KEY_BOOKMARKS, entries)
    }

    fun recordHistoryVisit(visit: BrowserEntry, maxEntries: Int) {
        val saved = runCatching { readEntries(KEY_HISTORY).toMutableList() }
            .getOrDefault(mutableListOf())
        saved.removeAll { it.url == visit.url }
        saved.add(0, visit)
        writeEntries(
            KEY_HISTORY,
            saved.sortedByDescending { it.timestamp }.take(maxEntries)
        )
    }

    fun removeHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
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
        const val KEY_TABS = "tabs"
        const val KEY_ACTIVE_TAB = "active_tab"
        const val KEY_HISTORY = "history"
        const val KEY_BOOKMARKS = "bookmarks"
    }
}
