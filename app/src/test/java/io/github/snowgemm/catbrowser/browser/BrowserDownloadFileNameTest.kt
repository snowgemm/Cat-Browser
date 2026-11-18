package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDownloadFileNameTest {
    @Test
    fun stripsUnixAndWindowsPathComponents() {
        assertEquals("report.pdf", sanitizeDownloadFileName("../../private/report.pdf"))
        assertEquals("photo.png", sanitizeDownloadFileName("C:\\Users\\Public\\photo.png"))
    }

    @Test
    fun replacesUnsafeCharactersAndControlCodes() {
        assertEquals("my_file_.zip", sanitizeDownloadFileName("my:file?.zip"))
        assertEquals("bad_name.txt", sanitizeDownloadFileName("bad\u0000name.txt"))
    }

    @Test
    fun fallsBackForEmptyDotOnlyNamesAndLimitsLength() {
        assertEquals("download", sanitizeDownloadFileName("..."))
        assertEquals("download", sanitizeDownloadFileName("///"))
        assertEquals(120, sanitizeDownloadFileName("a".repeat(160)).length)
    }
}
