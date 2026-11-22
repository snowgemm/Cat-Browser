package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserBrowsingDataPolicyTest {
    @Test
    fun convertsUnixSecondsToMilliseconds() {
        assertEquals(1_725_000L, BrowserBrowsingDataPolicy.sinceTimestampMillis(1_725L))
    }

    @Test
    fun removesRecordsAtOrAfterTheRequestedBoundary() {
        assertTrue(BrowserBrowsingDataPolicy.shouldRemove(1_725_000L, 1_725L))
        assertTrue(BrowserBrowsingDataPolicy.shouldRemove(1_726_000L, 1_725L))
        assertFalse(BrowserBrowsingDataPolicy.shouldRemove(1_724_999L, 1_725L))
    }

    @Test
    fun retainsOlderHistoryInItsOriginalOrder() {
        val entries =
            listOf(
                BrowserEntry("older", "https://older.example", 1_724_999L),
                BrowserEntry("boundary", "https://boundary.example", 1_725_000L),
                BrowserEntry("newer", "https://newer.example", 1_726_000L),
            )

        assertEquals(
            listOf(entries.first()),
            BrowserBrowsingDataPolicy.retainHistory(entries, 1_725L),
        )
    }

    @Test
    fun removesOnlyRecentCompletedRegularDownloads() {
        assertTrue(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.COMPLETE,
                isPrivate = false,
                createdAtMillis = 1_725_000L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
        assertTrue(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.FAILED,
                isPrivate = false,
                createdAtMillis = 1_725_000L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
        assertFalse(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.DOWNLOADING,
                isPrivate = false,
                createdAtMillis = 1_725_000L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
        assertFalse(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.QUEUED,
                isPrivate = false,
                createdAtMillis = 1_725_000L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
        assertFalse(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.COMPLETE,
                isPrivate = true,
                createdAtMillis = 1_725_000L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
        assertFalse(
            BrowserBrowsingDataPolicy.shouldRemoveDownload(
                BrowserDownloadStatus.COMPLETE,
                isPrivate = false,
                createdAtMillis = 1_724_999L,
                sinceUnixTimestamp = 1_725L,
            ),
        )
    }

    @Test
    fun clampsExtremeTimestampsInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            BrowserBrowsingDataPolicy.sinceTimestampMillis(Long.MAX_VALUE),
        )
        assertEquals(
            Long.MIN_VALUE,
            BrowserBrowsingDataPolicy.sinceTimestampMillis(Long.MIN_VALUE),
        )
    }
}
