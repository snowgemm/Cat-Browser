package io.github.snowgemm.catbrowser.browser

internal object BrowserBookmarkPolicy {
    const val MAX_TITLE_LENGTH = 100
    const val MAX_FOLDER_NAME_LENGTH = 40
    const val MAX_FOLDERS = 30
    private const val MAX_FOLDER_ID_LENGTH = 80

    fun rename(
        entries: List<BrowserEntry>,
        url: String,
        inputTitle: String,
    ): List<BrowserEntry>? {
        val title = inputTitle.trim().take(MAX_TITLE_LENGTH)
        if (title.isBlank()) return null

        val index = entries.indexOfFirst { it.url == url }
        if (index < 0) return null

        return entries.toMutableList().apply {
            this[index] = this[index].copy(title = title)
        }
    }

    fun remove(
        entries: List<BrowserEntry>,
        url: String,
    ): List<BrowserEntry>? {
        if (entries.none { it.url == url }) return null
        return entries.filterNot { it.url == url }
    }

    fun uniqueByUrl(entries: List<BrowserEntry>): List<BrowserEntry> =
        entries
            .sortedByDescending { it.timestamp }
            .distinctBy { it.url }

    fun sortAlphabetically(entries: List<BrowserEntry>): List<BrowserEntry> = entries.sortedBy { it.title.lowercase() }

    fun normalizeFolderName(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(MAX_FOLDER_NAME_LENGTH)

    fun normalizeFolders(folders: List<BrowserBookmarkFolder>): List<BrowserBookmarkFolder> {
        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        return buildList {
            folders.forEach { folder ->
                val id = folder.id.trim()
                val name = normalizeFolderName(folder.name)
                if (
                    id.isBlank() ||
                    id.length > MAX_FOLDER_ID_LENGTH ||
                    name.isBlank() ||
                    !seenIds.add(id) ||
                    !seenNames.add(name.lowercase())
                ) {
                    return@forEach
                }
                add(folder.copy(id = id, name = name))
                if (size >= MAX_FOLDERS) return@buildList
            }
        }
    }

    fun createFolder(
        folders: List<BrowserBookmarkFolder>,
        id: String,
        inputName: String,
    ): List<BrowserBookmarkFolder>? {
        val name = normalizeFolderName(inputName)
        if (name.isBlank() || id.isBlank() || id.length > MAX_FOLDER_ID_LENGTH) return null
        if (folders.size >= MAX_FOLDERS) return null
        if (folders.any { it.name.equals(name, ignoreCase = true) || it.id == id }) return null
        return folders + BrowserBookmarkFolder(id = id, name = name)
    }

    fun renameFolder(
        folders: List<BrowserBookmarkFolder>,
        id: String,
        inputName: String,
    ): List<BrowserBookmarkFolder>? {
        val name = normalizeFolderName(inputName)
        if (name.isBlank()) return null
        val index = folders.indexOfFirst { it.id == id }
        if (index < 0 || folders.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            return null
        }
        return folders.toMutableList().apply {
            this[index] = this[index].copy(name = name)
        }
    }

    fun removeFolder(
        folders: List<BrowserBookmarkFolder>,
        id: String,
    ): List<BrowserBookmarkFolder>? {
        if (folders.none { it.id == id }) return null
        return folders.filterNot { it.id == id }
    }

    fun moveToFolder(
        entries: List<BrowserEntry>,
        url: String,
        folderId: String?,
        folders: List<BrowserBookmarkFolder>,
    ): List<BrowserEntry>? {
        if (folderId != null && folders.none { it.id == folderId }) return null
        val index = entries.indexOfFirst { it.url == url }
        if (index < 0) return null
        return entries.toMutableList().apply {
            this[index] = this[index].copy(folderId = folderId)
        }
    }
}
