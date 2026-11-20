package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPictureInPicturePolicyTest {
    @Test
    fun opensOnlyForSelectedOpenSessionWithValidToken() {
        assertTrue(
            BrowserPictureInPicturePolicy.canOpen(
                placementToken = "valid-placement-token",
                sourceSessionIsOpen = true,
                sourceTabIsSelected = true,
                pictureInPictureAlreadyOpen = false,
            ),
        )
    }

    @Test
    fun rejectsBlankTokens() {
        assertFalse(
            BrowserPictureInPicturePolicy.canOpen(
                placementToken = " ",
                sourceSessionIsOpen = true,
                sourceTabIsSelected = true,
                pictureInPictureAlreadyOpen = false,
            ),
        )
    }

    @Test
    fun rejectsBackgroundTabs() {
        assertFalse(
            BrowserPictureInPicturePolicy.canOpen(
                placementToken = "valid-placement-token",
                sourceSessionIsOpen = true,
                sourceTabIsSelected = false,
                pictureInPictureAlreadyOpen = false,
            ),
        )
    }

    @Test
    fun rejectsDuplicatePictureInPictureWindows() {
        assertFalse(
            BrowserPictureInPicturePolicy.canOpen(
                placementToken = "valid-placement-token",
                sourceSessionIsOpen = true,
                sourceTabIsSelected = true,
                pictureInPictureAlreadyOpen = true,
            ),
        )
    }
}
