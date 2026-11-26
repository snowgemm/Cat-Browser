package io.github.snowgemm.catbrowser.ui

import androidx.compose.animation.EnterTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun pageProgressUsesScaleFadeWhenAnimationsAreEnabled() {
        assertNotEquals(EnterTransition.None, browserScaleFadeEnter(enabled = true, durationMillis = 120))
    }

    @Test
    fun pageProgressAppearsImmediatelyWhenAnimationsAreDisabled() {
        assertEquals(EnterTransition.None, browserScaleFadeEnter(enabled = false, durationMillis = 120))
    }

    @Test
    fun scaleFadeRejectsOutOfRangeInitialScale() {
        assertThrows(IllegalArgumentException::class.java) {
            browserScaleFadeEnter(enabled = true, durationMillis = 120, initialScale = 1.1f)
        }
    }
}