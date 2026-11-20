package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserBookmarkPolicyTest {
    @Test
    fun renameTrimsTitleAndPreservesUrlTimestampAndPosition() {
        val first = entry("First", "https://first.example", 10L)
        val target = entry("Old title", "https://target.example", 20L)
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

    private fun entry(
        title: String,
        url: String,
        timestamp: Long,
    ) = BrowserEntry(title = title, url = url, timestamp = timestamp)
}
