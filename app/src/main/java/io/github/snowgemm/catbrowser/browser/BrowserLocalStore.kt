package io.github.snowgemm.catbrowser.browser

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RestoredBrowserTabs(
    val tabs: List<BrowserTab>,
    val selectedTabId: String,
    val groups: List<BrowserTabGroup>,
)

/**
 * Persists browser-owned local data while keeping private tabs out of storage.
 *
 * Existing tab JSON fields remain unchanged. Group fields are optional so older
 * installations continue to restore regular tabs without a migration.
 */
internal class BrowserLocalStore(
    private val preferences: SharedPreferences,
) {
    fun restoreTabs(
        defaultUrl: String,
        maxTabs: Int,
    ): RestoredBrowserTabs {
        val savedGroups = readTabGroups()
        val savedGroupIds = savedGroups.mapTo(mutableSetOf()) { it.id }
        val saved = readArray(KEY_TABS)
        val restoredTabs =
            buildList {
                for (index in 0 until minOf(saved.length(), maxTabs)) {
                    val item = saved.optJSONObject(index) ?: continue
                    val url = item.optString("url", defaultUrl)
                    if (!isWebUrl(url)) continue

                    val groupId =
                        item
                            .optString("groupId")
                            .takeIf { it in savedGroupIds }

                    add(
                        BrowserTab(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            initialUrl = url,
                            initialTitle = item.optString("title").ifBlank { "Restored tab" },
                            initialDesktopMode = item.optBoolean("desktopMode", false),
                            initialGroupId = groupId,
                        ),
                    )
                }
            }
        val restoredGroupIds = restoredTabs.mapNotNullTo(mutableSetOf()) { it.groupId }
        return RestoredBrowserTabs(
            tabs = restoredTabs,
            selectedTabId = preferences.getString(KEY_ACTIVE_TAB, "").orEmpty(),
            groups = savedGroups.filter { it.id in restoredGroupIds },
        )
    }

    fun readHistory(maxEntries: Int): List<BrowserEntry> =
        readEntries(KEY_HISTORY)
            .sortedByDescending { it.timestamp }
            .take(maxEntries)

    fun readBookmarkFolders(): List<BrowserBookmarkFolder> {
        val saved = readArray(KEY_BOOKMARK_FOLDERS)
        return BrowserBookmarkPolicy.normalizeFolders(
            buildList {
                for (index in 0 until saved.length()) {
                    val item = saved.optJSONObject(index) ?: continue
                    add(
                        BrowserBookmarkFolder(
                            id = item.optString("id"),
                            name = item.optString("name"),
                        ),
                    )
                }
            },
        )
    }

    fun readBookmarks(): List<BrowserEntry> {
        val validFolderIds = readBookmarkFolders().mapTo(mutableSetOf()) { it.id }
        return BrowserBookmarkPolicy.uniqueByUrl(
            readEntries(KEY_BOOKMARKS).map { entry ->
                entry.copy(folderId = entry.folderId?.takeIf { it in validFolderIds })
            },
        )
    }

    fun saveTabs(
        tabs: List<BrowserTab>,
        selectedTabId: String,
        defaultUrl: String,
        groups: List<BrowserTabGroup>,
    ) {
        // Private-tab URLs and group assignments must never enter app-owned storage.
        val persistentTabs = tabs.filter(::shouldPersistBrowserTab)
        val persistentSelectedTabId =
            selectedTabId
                .takeIf { id -> persistentTabs.any { it.id == id } }
                ?: persistentTabs.firstOrNull()?.id.orEmpty()
        val assignedGroupIds = persistentTabs.mapNotNullTo(mutableSetOf()) { it.groupId }
        val persistentGroups =
            groups
                .filter { it.id in assignedGroupIds }
                .distinctBy { it.id }
        val persistentGroupIds = persistentGroups.mapTo(mutableSetOf()) { it.id }

        val tabsArray = JSONArray()
        persistentTabs.forEach { tab ->
            val item =
                JSONObject()
                    .put("id", tab.id)
                    .put("url", if (isWebUrl(tab.url)) tab.url else defaultUrl)
                    .put("title", tab.title.take(200))
                    .put("desktopMode", tab.isDesktopMode)
            tab.groupId?.takeIf { it in persistentGroupIds }?.let { groupId ->
                item.put("groupId", groupId)
            }
            tabsArray.put(item)
        }

        val groupsArray = JSONArray()
        persistentGroups.forEach { group ->
            val name = BrowserTabGroupPolicy.normalizeName(group.name)
            if (name.isBlank()) return@forEach
            groupsArray.put(
                JSONObject()
                    .put("id", group.id)
                    .put("name", name)
                    .put("colorIndex", BrowserTabGroupPolicy.normalizeColorIndex(group.colorIndex)),
            )
        }

        preferences
            .edit()
            .putString(KEY_TABS, tabsArray.toString())
            .putString(KEY_ACTIVE_TAB, persistentSelectedTabId)
            .putString(KEY_TAB_GROUPS, groupsArray.toString())
            .apply()
    }

    fun saveHistory(entries: List<BrowserEntry>) {
        writeEntries(KEY_HISTORY, entries)
    }

    fun saveBookmarks(entries: List<BrowserEntry>) {
        writeEntries(KEY_BOOKMARKS, entries)
    }

    fun saveBookmarkFolders(folders: List<BrowserBookmarkFolder>) {
        val array = JSONArray()
        BrowserBookmarkPolicy.normalizeFolders(folders).forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name),
            )
        }
        preferences.edit().putString(KEY_BOOKMARK_FOLDERS, array.toString()).apply()
    }

    fun recordHistoryVisit(
        visit: BrowserEntry,
        maxEntries: Int,
    ) {
        val saved =
            runCatching { readEntries(KEY_HISTORY).toMutableList() }
                .getOrDefault(mutableListOf())
        saved.removeAll { it.url == visit.url }
        saved.add(0, visit)
        writeEntries(
            KEY_HISTORY,
            saved.sortedByDescending { it.timestamp }.take(maxEntries),
        )
    }

    fun removeHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    fun removeHistorySince(sinceUnixTimestamp: Long) {
        val retained =
            BrowserBrowsingDataPolicy.retainHistory(
                readEntries(KEY_HISTORY),
                sinceUnixTimestamp,
            )
        writeEntries(KEY_HISTORY, retained)
    }

    private fun readTabGroups(): List<BrowserTabGroup> {
        val saved = readArray(KEY_TAB_GROUPS)
        val seenIds = mutableSetOf<String>()
        return buildList {
            for (index in 0 until saved.length()) {
                val item = saved.optJSONObject(index) ?: continue
                val id =
                    item
                        .optString("id")
                        .takeIf { it.isNotBlank() && it.length <= MAX_GROUP_ID_LENGTH }
                        ?: continue
                if (!seenIds.add(id)) continue
                val name = BrowserTabGroupPolicy.normalizeName(item.optString("name"))
                if (name.isBlank()) continue
                add(
                    BrowserTabGroup(
                        id = id,
                        name = name,
                        colorIndex =
                            BrowserTabGroupPolicy.normalizeColorIndex(
                                item.optInt("colorIndex", 0),
                            ),
                    ),
                )
                if (size >= BrowserTabGroupPolicy.MAX_GROUPS) break
            }
        }
    }

    private fun readEntries(key: String): List<BrowserEntry> {
        val array = readArray(key)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!isWebUrl(url)) continue
                add(
                    BrowserEntry(
                        title = item.optString("title").ifBlank { url },
                        url = url,
                        timestamp = item.optLong("timestamp"),
                        folderId = item.optString("folderId").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun readArray(key: String): JSONArray =
        try {
            JSONArray(preferences.getString(key, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

    private fun writeEntries(
        key: String,
        entries: List<BrowserEntry>,
    ) {
        preferences.edit().putString(key, entriesToJson(entries).toString()).apply()
    }

    private fun entriesToJson(entries: List<BrowserEntry>): JSONArray {
        val array = JSONArray()
        entries.forEach { entry ->
            val item =
                JSONObject()
                    .put("title", entry.title.take(200))
                    .put("url", entry.url)
                    .put("timestamp", entry.timestamp)
            entry.folderId?.takeIf { it.isNotBlank() }?.let { item.put("folderId", it) }
            array.put(item)
        }
        return array
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)

    private companion object {
        const val KEY_TABS = "tabs"
        const val KEY_ACTIVE_TAB = "active_tab"
        const val KEY_TAB_GROUPS = "tab_groups"
        const val KEY_HISTORY = "history"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_BOOKMARK_FOLDERS = "bookmark_folders"
        const val MAX_GROUP_ID_LENGTH = 80
    }
}
