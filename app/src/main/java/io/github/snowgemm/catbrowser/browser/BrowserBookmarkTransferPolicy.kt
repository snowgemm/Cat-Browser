package io.github.snowgemm.catbrowser.browser

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class BrowserBookmarkBackupSnapshot(
    val bookmarks: List<BrowserEntry>,
    val folders: List<BrowserBookmarkFolder>,
)

internal data class ParsedBrowserBookmarkBackup(
    val bookmarks: List<BrowserEntry>,
    val folders: List<BrowserBookmarkFolder>,
    val skippedBookmarkCount: Int,
)

internal data class BrowserBookmarkImportSummary(
    val importedBookmarks: Int,
    val skippedBookmarks: Int,
    val addedFolders: Int,
)

internal data class MergedBrowserBookmarkBackup(
    val bookmarks: List<BrowserEntry>,
    val folders: List<BrowserBookmarkFolder>,
    val summary: BrowserBookmarkImportSummary,
)

/**
 * Defines the versioned JSON backup format and merges imported records without
 * replacing bookmarks that are already present on the device.
 */
internal object BrowserBookmarkTransferPolicy {
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    private const val FORMAT = "cat-browser-bookmarks"
    private const val FORMAT_VERSION = 1
    private const val MAX_IMPORTED_BOOKMARKS = 10_000
    private const val MAX_URL_LENGTH = 4_096

    fun exportJson(
        entries: List<BrowserEntry>,
        folders: List<BrowserBookmarkFolder>,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val safeFolders = BrowserBookmarkPolicy.normalizeFolders(folders)
        val validFolderIds = safeFolders.mapTo(mutableSetOf()) { it.id }
        val safeEntries =
            BrowserBookmarkPolicy.uniqueByUrl(
                entries.mapNotNull { normalizeEntry(it, validFolderIds) },
            )
        require(safeEntries.size <= MAX_IMPORTED_BOOKMARKS) {
            "Bookmark backup contains too many entries."
        }

        val folderArray = JSONArray()
        safeFolders.forEach { folder ->
            folderArray.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name),
            )
        }

        val bookmarkArray = JSONArray()
        safeEntries.forEach { entry ->
            val item =
                JSONObject()
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("timestamp", entry.timestamp)
            entry.folderId?.let { item.put("folderId", it) }
            bookmarkArray.put(item)
        }

        val json =
            JSONObject()
                .put("format", FORMAT)
                .put("version", FORMAT_VERSION)
                .put("exportedAt", exportedAt.coerceAtLeast(0L))
                .put("folders", folderArray)
                .put("bookmarks", bookmarkArray)
                .toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) {
            "Bookmark backup exceeds the import size limit."
        }
        return json
    }

    fun parse(json: String): ParsedBrowserBookmarkBackup? {
        if (json.length > MAX_IMPORT_BYTES) return null
        if (json.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) return null

        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT || root.optInt("version", -1) != FORMAT_VERSION) {
            return null
        }

        val folderArray = root.optJSONArray("folders") ?: return null
        val bookmarkArray = root.optJSONArray("bookmarks") ?: return null
        if (folderArray.length() > BrowserBookmarkPolicy.MAX_FOLDERS ||
            bookmarkArray.length() > MAX_IMPORTED_BOOKMARKS
        ) {
            return null
        }

        val parsedFolders =
            buildList {
                for (index in 0 until folderArray.length()) {
                    val item = folderArray.optJSONObject(index) ?: continue
                    val id = item.opt("id") as? String ?: continue
                    val name = item.opt("name") as? String ?: continue
                    add(BrowserBookmarkFolder(id = id, name = name))
                }
            }
        val safeFolders = BrowserBookmarkPolicy.normalizeFolders(parsedFolders)
        val validFolderIds = safeFolders.mapTo(mutableSetOf()) { it.id }

        val parsedEntries =
            buildList {
                for (index in 0 until bookmarkArray.length()) {
                    val item = bookmarkArray.optJSONObject(index) ?: continue
                    val rawUrl = item.opt("url") as? String ?: continue
                    val url = normalizeUrl(rawUrl) ?: continue
                    val rawTitle = item.opt("title") as? String ?: ""
                    val timestamp = (item.opt("timestamp") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                    val folderId =
                        (item.opt("folderId") as? String)
                            ?.takeIf { it in validFolderIds }
                    add(
                        BrowserEntry(
                            title =
                                rawTitle
                                    .trim()
                                    .take(BrowserBookmarkPolicy.MAX_TITLE_LENGTH)
                                    .ifBlank { url.take(BrowserBookmarkPolicy.MAX_TITLE_LENGTH) },
                            url = url,
                            timestamp = timestamp,
                            folderId = folderId,
                        ),
                    )
                }
            }
        val uniqueEntries = BrowserBookmarkPolicy.uniqueByUrl(parsedEntries)

        return ParsedBrowserBookmarkBackup(
            bookmarks = uniqueEntries,
            folders = safeFolders,
            skippedBookmarkCount = bookmarkArray.length() - uniqueEntries.size,
        )
    }

    fun merge(
        existingBookmarks: List<BrowserEntry>,
        existingFolders: List<BrowserBookmarkFolder>,
        imported: ParsedBrowserBookmarkBackup,
    ): MergedBrowserBookmarkBackup {
        var mergedFolders = BrowserBookmarkPolicy.normalizeFolders(existingFolders)
        val importedFolderIds = mutableMapOf<String, String>()
        var addedFolders = 0

        imported.folders.forEach { importedFolder ->
            val matchingFolder =
                mergedFolders.firstOrNull {
                    it.name.equals(importedFolder.name, ignoreCase = true)
                }
            if (matchingFolder != null) {
                importedFolderIds[importedFolder.id] = matchingFolder.id
            } else {
                val newId = UUID.randomUUID().toString()
                val updated =
                    BrowserBookmarkPolicy.createFolder(
                        mergedFolders,
                        newId,
                        importedFolder.name,
                    )
                if (updated != null) {
                    mergedFolders = updated
                    importedFolderIds[importedFolder.id] = newId
                    addedFolders++
                }
            }
        }

        val existingFolderIds = mergedFolders.mapTo(mutableSetOf()) { it.id }
        val safeExistingBookmarks =
            BrowserBookmarkPolicy.uniqueByUrl(
                existingBookmarks.mapNotNull { normalizeEntry(it, existingFolderIds) },
            )
        val knownUrls = safeExistingBookmarks.mapNotNullTo(mutableSetOf()) { normalizeUrl(it.url) }
        val newBookmarks =
            imported.bookmarks.mapNotNull { entry ->
                val url = normalizeUrl(entry.url) ?: return@mapNotNull null
                if (!knownUrls.add(url)) return@mapNotNull null
                val safeEntry = normalizeEntry(entry, importedFolderIds.keys) ?: return@mapNotNull null
                safeEntry.copy(folderId = entry.folderId?.let(importedFolderIds::get))
            }

        val mergedBookmarks =
            BrowserBookmarkPolicy.uniqueByUrl(safeExistingBookmarks + newBookmarks)
        val skippedBookmarks =
            imported.skippedBookmarkCount + imported.bookmarks.size - newBookmarks.size

        return MergedBrowserBookmarkBackup(
            bookmarks = mergedBookmarks,
            folders = mergedFolders,
            summary =
                BrowserBookmarkImportSummary(
                    importedBookmarks = newBookmarks.size,
                    skippedBookmarks = skippedBookmarks,
                    addedFolders = addedFolders,
                ),
        )
    }

    private fun normalizeEntry(
        entry: BrowserEntry,
        validFolderIds: Set<String>,
    ): BrowserEntry? {
        val url = normalizeUrl(entry.url) ?: return null
        return BrowserEntry(
            title =
                entry.title
                    .trim()
                    .take(BrowserBookmarkPolicy.MAX_TITLE_LENGTH)
                    .ifBlank { url.take(BrowserBookmarkPolicy.MAX_TITLE_LENGTH) },
            url = url,
            timestamp = entry.timestamp.coerceAtLeast(0L),
            folderId = entry.folderId?.takeIf { it in validFolderIds },
        )
    }

    private fun normalizeUrl(input: String): String? {
        if (input.length > MAX_URL_LENGTH) return null
        if (!input.startsWith("http://", ignoreCase = true) &&
            !input.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return BrowserUrlResolver.resolveAddress(input)
    }
}
