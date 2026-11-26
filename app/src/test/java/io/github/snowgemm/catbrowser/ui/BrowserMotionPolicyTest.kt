package io.github.snowgemm.catbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrowserMotionPolicyTest {
    @Test
    fun keepsRequestedDurationWhenSystemAnimationsAreEnabled() {
        assertEquals(180, BrowserMotionPolicy.durationMillis(enabled = true, preferredDurationMillis = 180))
    }

    @Test
    fun removesNonEssentialDurationWhenSystemAnimationsAreDisabled() {
        assertEquals(0, BrowserMotionPolicy.durationMillis(enabled = false, preferredDurationMillis = 180))
    }

    @Test
    fun rejectsNegativeAnimationDurations() {
        assertThrows(IllegalArgumentException::class.java) {
            BrowserMotionPolicy.durationMillis(enabled = true, preferredDurationMillis = -1)
        }
    }
}
