package io.github.snowgemm.catbrowser.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGenerationTrackerTest {
    @Test
    fun invalidatingBetaDiscardsStaleResponseWithoutAffectingStable() {
        val tracker = UpdateGenerationTracker()
        val betaRequest = tracker.next(UpdateChannel.BETA)
        val stableRequest = tracker.next(UpdateChannel.STABLE)

        tracker.invalidate(UpdateChannel.BETA)

        assertFalse(tracker.isCurrent(UpdateChannel.BETA, betaRequest))
        assertTrue(tracker.isCurrent(UpdateChannel.STABLE, stableRequest))
    }

    @Test
    fun onlyLatestRequestForChannelCanPublishResults() {
        val tracker = UpdateGenerationTracker()
        val first = tracker.next(UpdateChannel.STABLE)
        val second = tracker.next(UpdateChannel.STABLE)

        assertFalse(tracker.isCurrent(UpdateChannel.STABLE, first))
        assertTrue(tracker.isCurrent(UpdateChannel.STABLE, second))
    }
}
