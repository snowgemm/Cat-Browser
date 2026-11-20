package io.github.snowgemm.catbrowser.browser

internal data class BrowserPageSecurity(
    val isSecure: Boolean,
    val isException: Boolean,
    val activeMixedContentLoaded: Boolean,
    val passiveMixedContentLoaded: Boolean,
) {
    val isVerifiedSecure: Boolean
        get() =
            isSecure &&
            !isException &&
            !activeMixedContentLoaded &&
            !passiveMixedContentLoaded
}
