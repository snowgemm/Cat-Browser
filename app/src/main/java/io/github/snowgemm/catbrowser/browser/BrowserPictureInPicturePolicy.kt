package io.github.snowgemm.catbrowser.browser

internal object BrowserPictureInPicturePolicy {
    fun canOpen(
        placementToken: String,
        sourceSessionIsOpen: Boolean,
        sourceTabIsSelected: Boolean,
        pictureInPictureAlreadyOpen: Boolean,
    ): Boolean =
        placementToken.isNotBlank() &&
            sourceSessionIsOpen &&
            sourceTabIsSelected &&
            !pictureInPictureAlreadyOpen
}
