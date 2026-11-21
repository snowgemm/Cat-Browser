package io.github.snowgemm.catbrowser.browser

/**
 * Tracks the newest asynchronous operation so stale callbacks cannot overwrite newer UI state.
 */
internal class BrowserRequestGeneration {
    private var generation = 0L

    fun next(): Long {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(token: Long): Boolean = token == generation
}
