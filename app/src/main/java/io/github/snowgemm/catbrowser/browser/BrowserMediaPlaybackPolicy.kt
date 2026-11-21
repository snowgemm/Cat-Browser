package io.github.snowgemm.catbrowser.browser

internal object BrowserMediaPlaybackPolicy {
    fun shouldKeepSessionActive(
        isVisible: Boolean,
        isPictureInPictureSource: Boolean,
        isPlayingMedia: Boolean,
    ): Boolean = isVisible || isPictureInPictureSource || isPlayingMedia
}
