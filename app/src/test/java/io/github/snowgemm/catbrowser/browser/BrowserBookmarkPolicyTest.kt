package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserBookmarkPolicyTest {
    @Test
    fun renameTrimsTitleAndPreservesUrlTimestampAndPosition() {
        val first = entry("First", "https://first.example", 10L)
        val target = entry("Old title", "https://target.example", 20L).copy(folderId = "reading")
        val updated =
            BrowserBookmarkPolicy.rename(
                listOf(first, target),
                target.url,
                "  New title  ",
            )

        assertEquals(
            listOf(first, target.copy(title = "New title")),
            updated,
        )
    }

    @Test
    fun renameRejectsBlankTitleAndUnknownUrl() {
        val bookmark = entry("Example", "https://example.com", 10L)

        assertNull(BrowserBookmarkPolicy.rename(listOf(bookmark), bookmark.url, "  "))
        assertNull(BrowserBookmarkPolicy.rename(listOf(bookmark), "https://missing.example", "New"))
    }

    @Test
    fun renameLimitsTitleLength() {
        val bookmark = entry("Example", "https://example.com", 10L)
        val updated =
            BrowserBookmarkPolicy.rename(
                listOf(bookmark),
                bookmark.url,
                "x".repeat(BrowserBookmarkPolicy.MAX_TITLE_LENGTH + 20),
            )

        assertEquals(BrowserBookmarkPolicy.MAX_TITLE_LENGTH, updated?.single()?.title?.length)
    }

    @Test
    fun removeDeletesMatchingBookmarkAndKeepsOtherEntriesInOrder() {
        val first = entry("First", "https://first.example", 10L)
        val second = entry("Second", "https://second.example", 20L)
        val third = entry("Third", "https://third.example", 30L)

        assertEquals(
            listOf(first, third),
            BrowserBookmarkPolicy.remove(listOf(first, second, third), second.url),
        )
        assertNull(BrowserBookmarkPolicy.remove(listOf(first), "https://missing.example"))
    }

    @Test
    fun duplicateUrlsKeepTheNewestBookmark() {
        val older = entry("Old title", "https://example.com", 10L)
        val newer = entry("New title", "https://example.com", 20L)
        val other = entry("Other", "https://other.example", 15L)

        assertEquals(
            listOf(newer, other),
            BrowserBookmarkPolicy.uniqueByUrl(listOf(older, other, newer)),
        )
    }

    @Test
    fun alphabeticalSortIsCaseInsensitive() {
        val beta = entry("beta", "https://beta.example", 10L)
        val alpha = entry("Alpha", "https://alpha.example", 20L)
        val gamma = entry("Gamma", "https://gamma.example", 30L)

        assertEquals(
            listOf(alpha, beta, gamma),
            BrowserBookmarkPolicy.sortAlphabetically(listOf(beta, gamma, alpha)),
        )
    }

    @Test
    fun createFolderTrimsNameAndRejectsDuplicatesAndBlankNames() {
        val created = BrowserBookmarkPolicy.createFolder(emptyList(), "folder-1", "  Reading  ")
        assertEquals(listOf(BrowserBookmarkFolder("folder-1", "Reading")), created)
        assertNull(BrowserBookmarkPolicy.createFolder(created.orEmpty(), "folder-2", "reading"))
        assertNull(BrowserBookmarkPolicy.createFolder(created.orEmpty(), "folder-2", "  "))
    }

    @Test
    fun folderNamesAreBoundedAndNormalized() {
        val name = "  " + "Long ".repeat(15) + "Name  "
        val created = BrowserBookmarkPolicy.createFolder(emptyList(), "folder-1", name)
        assertEquals(BrowserBookmarkPolicy.MAX_FOLDER_NAME_LENGTH, created?.single()?.name?.length)
    }

    @Test
    fun renameFolderRejectsDuplicateAndUnknownFolder() {
        val folders =
            listOf(
                BrowserBookmarkFolder("one", "Reading"),
                BrowserBookmarkFolder("two", "Work"),
            )
        assertEquals(
            listOf(BrowserBookmarkFolder("one", "Reference"), folders[1]),
            BrowserBookmarkPolicy.renameFolder(folders, "one", " Reference "),
        )
        assertNull(BrowserBookmarkPolicy.renameFolder(folders, "one", "work"))
        assertNull(BrowserBookmarkPolicy.renameFolder(folders, "missing", "Other"))
    }

    @Test
    fun deletingFolderDoesNotMutateTheInputList() {
        val first = BrowserBookmarkFolder("one", "Reading")
        val second = BrowserBookmarkFolder("two", "Work")
        assertEquals(listOf(second), BrowserBookmarkPolicy.removeFolder(listOf(first, second), "one"))
        assertNull(BrowserBookmarkPolicy.removeFolder(listOf(second), "missing"))
    }

    @Test
    fun movingBookmarkAssignsAndClearsFolder() {
        val folder = BrowserBookmarkFolder("reading", "Reading")
        val bookmark = entry("Example", "https://example.com", 10L)
        assertEquals(
            listOf(bookmark.copy(folderId = folder.id)),
            BrowserBookmarkPolicy.moveToFolder(listOf(bookmark), bookmark.url, folder.id, listOf(folder)),
        )
        assertEquals(
            listOf(bookmark),
            BrowserBookmarkPolicy.moveToFolder(
                listOf(bookmark.copy(folderId = folder.id)),
                bookmark.url,
                null,
                listOf(folder),
            ),
        )
        assertNull(
            BrowserBookmarkPolicy.moveToFolder(listOf(bookmark), bookmark.url, "missing", listOf(folder)),
        )
        assertNull(
            BrowserBookmarkPolicy.moveToFolder(listOf(bookmark), "https://missing.example", null, emptyList()),
        )
    }

    @Test
    fun normalizeFoldersDropsDuplicateIdsNamesAndLimitsCount() {
        val folders =
            (1..(BrowserBookmarkPolicy.MAX_FOLDERS + 2)).map { index ->
                BrowserBookmarkFolder("folder-$index", "Folder $index")
            } + BrowserBookmarkFolder("folder-1", "Another")
        val normalized = BrowserBookmarkPolicy.normalizeFolders(folders)
        assertEquals(BrowserBookmarkPolicy.MAX_FOLDERS, normalized.size)
        assertEquals(BrowserBookmarkPolicy.MAX_FOLDERS, normalized.map { it.id }.distinct().size)
        val normalizedNames =
            BrowserBookmarkPolicy.normalizeFolders(
                listOf(
                    BrowserBookmarkFolder("first", "Folder 1"),
                    BrowserBookmarkFolder("second", "folder 1"),
                    BrowserBookmarkFolder("third", "Folder 2"),
                ),
            )
        assertEquals(listOf("Folder 1", "Folder 2"), normalizedNames.map { it.name })
    }

    private fun entry(
        title: String,
        url: String,
        timestamp: Long,
    ) = BrowserEntry(title = title, url = url, timestamp = timestamp)
}
