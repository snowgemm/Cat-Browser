package io.github.snowgemm.catbrowser.update

internal class UpdateGenerationTracker {
    private val generations = mutableMapOf<UpdateChannel, Long>()

    fun next(channel: UpdateChannel): Long {
        val next = (generations[channel] ?: 0L) + 1L
        generations[channel] = next
        return next
    }

    fun invalidate(channel: UpdateChannel): Long = next(channel)

    fun isCurrent(channel: UpdateChannel, generation: Long): Boolean =
        generations[channel] == generation
}
