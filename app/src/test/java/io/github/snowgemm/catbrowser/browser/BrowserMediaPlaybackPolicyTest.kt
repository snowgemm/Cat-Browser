package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMediaPlaybackPolicyTest {
    @Test
    fun visibleSessionRemainsActive() {
        assertTrue(
            BrowserMediaPlaybackPolicy.shouldKeepSessionActive(
                isVisible = true,
                isPictureInPictureSource = false,
                isPlayingMedia = false,
            ),
        )
    }

    @Test
    fun pictureInPictureSourceRemainsActive() {
        assertTrue(
            BrowserMediaPlaybackPolicy.shouldKeepSessionActive(
                isVisible = false,
                isPictureInPictureSource = true,
                isPlayingMedia = false,
            ),
        )
    }

    @Test
    fun backgroundMediaPlaybackRemainsActive() {
        assertTrue(
            BrowserMediaPlaybackPolicy.shouldKeepSessionActive(
                isVisible = false,
                isPictureInPictureSource = false,
                isPlayingMedia = true,
            ),
        )
    }

    @Test
    fun idleBackgroundSessionCanBecomeInactive() {
        assertFalse(
            BrowserMediaPlaybackPolicy.shouldKeepSessionActive(
                isVisible = false,
                isPictureInPictureSource = false,
                isPlayingMedia = false,
            ),
        )
    }
}
