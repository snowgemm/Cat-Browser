package io.github.snowgemm.catbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUpdateVersionTest {
    @Test
    fun stableReleaseIsNewerThanSameVersionBeta() {
        assertTrue(compareBrowserVersions("0.2.2", "0.2.2-beta.98") > 0)
        assertTrue(compareBrowserVersions("0.2.2-beta.98", "0.2.2-beta.1") > 0)
    }

    @Test
    fun nextBetaIsNewerThanPreviousStable() {
        assertTrue(compareBrowserVersions("0.2.2-beta.1", "0.2.1") > 0)
    }

    @Test
    fun numericPrereleaseIdentifiersUseNumericOrdering() {
        assertTrue(compareBrowserVersions("0.2.2-beta.10", "0.2.2-beta.2") > 0)
    }

    @Test
    fun mapsTagsToIncreasingAndroidVersionCodes() {
        assertEquals(20199L, browserVersionCodeFromTag("v0.2.1"))
        assertEquals(20201L, browserVersionCodeFromTag("v0.2.2-beta.1"))
        assertEquals(20202L, browserVersionCodeFromTag("0.2.2-beta.2"))
        assertEquals(20299L, browserVersionCodeFromTag("v0.2.2"))
    }

    @Test
    fun rejectsUnsupportedVersionCodeTags() {
        assertNull(browserVersionCodeFromTag("v0.2.2-beta.99"))
        assertNull(browserVersionCodeFromTag("0.2"))
        assertNull(browserVersionCodeFromTag("v0.2.2-rc.1"))
    }
}
