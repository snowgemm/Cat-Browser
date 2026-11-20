package io.github.snowgemm.catbrowser.browser

internal object GeckoSessionVisibilityPolicy {
    fun isVisible(appForegrounded: Boolean, isSelected: Boolean, isViewAttached: Boolean): Boolean =
        appForegrounded && isSelected && isViewAttached
}
