package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserBookmarkTransferPolicyTest {
    @Test
    fun exportAndParsePreserveBookmarksAndFolders() {
        val folder = BrowserBookmarkFolder("folder-1", "Reading")
        val bookmark = BrowserEntry("Example", "https://example.com/path", 123L, folder.id)

        val json =
            BrowserBookmarkTransferPolicy.exportJson(
                listOf(bookmark),
                listOf(folder),
                exportedAt = 456L,
            )
        val parsed = BrowserBookmarkTransferPolicy.parse(json)

        assertNotNull(parsed)
        assertEquals(listOf(folder), parsed?.folders)
        assertEquals(listOf(bookmark), parsed?.bookmarks)
    }

    @Test
    fun parseRejectsUnknownFormatVersionAndMalformedJson() {
        assertNull(BrowserBookmarkTransferPolicy.parse("not json"))
        assertNull(
            BrowserBookmarkTransferPolicy.parse(
                """{"format":"another-browser","version":1,"folders":[],"bookmarks":[]}""",
            ),
        )
        assertNull(
            BrowserBookmarkTransferPolicy.parse(
                """{"format":"cat-browser-bookmarks","version":99,"folders":[],"bookmarks":[]}""",
            ),
        )
    }

    @Test
    fun parseRejectsMissingArraysAndOversizedBookmarkLists() {
        assertNull(
            BrowserBookmarkTransferPolicy.parse(
                """{"format":"cat-browser-bookmarks","version":1,"bookmarks":[]}""",
            ),
        )
        val tooManyBookmarks =
            (0..10_000).joinToString(",") {
                """{"title":"Example","url":"https://example.com/$it","timestamp":1}"""
            }
        val json =
            """{"format":"cat-browser-bookmarks","version":1,"folders":[],"bookmarks":[$tooManyBookmarks]}"""
        assertNull(BrowserBookmarkTransferPolicy.parse(json))
    }

    @Test
    fun parseDropsInvalidUrlsAndKeepsNewestDuplicate() {
        val json =
            """
            {
              "format": "cat-browser-bookmarks",
              "version": 1,
              "folders": [],
              "bookmarks": [
                {"title":"Unsafe","url":"javascript:alert(1)","timestamp":30},
                {"title":"Older","url":"https://example.com","timestamp":10},
                {"title":"Newest","url":"https://example.com","timestamp":20},
                {"title":"No host","url":"https:///missing-host","timestamp":40}
              ]
            }
            """.trimIndent()

        val parsed = BrowserBookmarkTransferPolicy.parse(json)

        assertEquals(1, parsed?.bookmarks?.size)
        assertEquals("Newest", parsed?.bookmarks?.single()?.title)
        assertEquals(3, parsed?.skippedBookmarkCount)
    }

    @Test
    fun mergePreservesExistingBookmarksAndReusesFoldersByName() {
        val existingFolder = BrowserBookmarkFolder("local-reading", "Reading")
        val existingBookmark =
            BrowserEntry(
                "Keep current title",
                "https://existing.example",
                50L,
                existingFolder.id,
            )
        val importedFolder = BrowserBookmarkFolder("backup-reading", "reading")
        val imported =
            ParsedBrowserBookmarkBackup(
                bookmarks =
                    listOf(
                        BrowserEntry("Incoming duplicate", existingBookmark.url, 100L, importedFolder.id),
                        BrowserEntry("New page", "https://new.example", 80L, importedFolder.id),
                    ),
                folders = listOf(importedFolder),
                skippedBookmarkCount = 0,
            )

        val merged =
            BrowserBookmarkTransferPolicy.merge(
                existingBookmarks = listOf(existingBookmark),
                existingFolders = listOf(existingFolder),
                imported = imported,
            )

        assertEquals(1, merged.summary.importedBookmarks)
        assertEquals(1, merged.summary.skippedBookmarks)
        assertEquals(0, merged.summary.addedFolders)
        assertEquals(existingBookmark, merged.bookmarks.first { it.url == existingBookmark.url })
        assertEquals(existingFolder.id, merged.bookmarks.single { it.url == "https://new.example" }.folderId)
    }

    @Test
    fun mergeAddsNewFolderAndMapsItsBookmarksToANewLocalId() {
        val importedFolder = BrowserBookmarkFolder("backup-folder", "Work")
        val imported =
            ParsedBrowserBookmarkBackup(
                bookmarks = listOf(BrowserEntry("Project", "https://project.example", 90L, importedFolder.id)),
                folders = listOf(importedFolder),
                skippedBookmarkCount = 0,
            )

        val merged = BrowserBookmarkTransferPolicy.merge(emptyList(), emptyList(), imported)

        assertEquals(1, merged.summary.importedBookmarks)
        assertEquals(1, merged.summary.addedFolders)
        assertEquals("Work", merged.folders.single().name)
        assertEquals(merged.folders.single().id, merged.bookmarks.single().folderId)
        assertNotEquals(importedFolder.id, merged.folders.single().id)
    }

    @Test
    fun mergeKeepsBookmarksUnfiledWhenFolderLimitIsReached() {
        val existingFolders =
            (1..BrowserBookmarkPolicy.MAX_FOLDERS).map { index ->
                BrowserBookmarkFolder("local-$index", "Folder $index")
            }
        val importedFolder = BrowserBookmarkFolder("backup-folder", "Imported")
        val imported =
            ParsedBrowserBookmarkBackup(
                bookmarks = listOf(BrowserEntry("Page", "https://page.example", 10L, importedFolder.id)),
                folders = listOf(importedFolder),
                skippedBookmarkCount = 0,
            )

        val merged = BrowserBookmarkTransferPolicy.merge(emptyList(), existingFolders, imported)

        assertEquals(0, merged.summary.addedFolders)
        assertEquals(null, merged.bookmarks.single().folderId)
        assertEquals(BrowserBookmarkPolicy.MAX_FOLDERS, merged.folders.size)
    }
}
