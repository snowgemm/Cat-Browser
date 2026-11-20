package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadRegressionTest {
    @Test
    fun repeatedSameNameRequestsReceiveDistinctStableNames() {
        val occupied = mutableSetOf<String>()
        val first = DownloadQueuePolicy.uniqueFileName("report.pdf") { it in occupied }
        occupied += first
        val second = DownloadQueuePolicy.uniqueFileName("report.pdf") { it in occupied }
        occupied += second
        val third = DownloadQueuePolicy.uniqueFileName("report.pdf") { it in occupied }

        assertEquals("report.pdf", first)
        assertEquals("report (1).pdf", second)
        assertEquals("report (2).pdf", third)
    }

    @Test
    fun filenamePolicyHandlesExtensionlessAndDotfileNames() {
        assertEquals(
            "README (1)",
            DownloadQueuePolicy.uniqueFileName("README") { it == "README" }
        )
        assertEquals(
            ".env (1)",
            DownloadQueuePolicy.uniqueFileName(".env") { it == ".env" }
        )
    }

    @Test
    fun queuedAndRunningDownloadsCanBeCancelledButTerminalOnesCannot() {
        assertTrue(DownloadQueuePolicy.canCancel(BrowserDownloadStatus.QUEUED))
        assertTrue(DownloadQueuePolicy.canCancel(BrowserDownloadStatus.DOWNLOADING))
        assertFalse(DownloadQueuePolicy.canCancel(BrowserDownloadStatus.COMPLETE))
        assertFalse(DownloadQueuePolicy.canCancel(BrowserDownloadStatus.FAILED))
        assertFalse(DownloadQueuePolicy.canCancel(BrowserDownloadStatus.CANCELLED))
    }

    @Test
    fun interruptedDownloadsRestoreAsFailedMetadata() {
        assertEquals(
            BrowserDownloadStatus.FAILED,
            DownloadQueuePolicy.restoredStatus(BrowserDownloadStatus.QUEUED)
        )
        assertEquals(
            BrowserDownloadStatus.FAILED,
            DownloadQueuePolicy.restoredStatus(BrowserDownloadStatus.DOWNLOADING)
        )
        assertEquals(
            BrowserDownloadStatus.COMPLETE,
            DownloadQueuePolicy.restoredStatus(BrowserDownloadStatus.COMPLETE)
        )
    }

    @Test
    fun cancellationWinsAgainstLateWorkerCompletion() {
        assertEquals(
            BrowserDownloadStatus.CANCELLED,
            DownloadQueuePolicy.completionStatus(cancelled = true)
        )
        assertEquals(
            BrowserDownloadStatus.COMPLETE,
            DownloadQueuePolicy.completionStatus(cancelled = false)
        )
    }
}
