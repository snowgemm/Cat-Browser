package io.github.snowgemm.catbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.ContentBlockingController

class BrowserPrivacyStatsTest {
    @Test
    fun aggregatesSevenDayEventsByTrackingCategory() {
        val stats =
            aggregatePrivacyBlockingEvents(
                listOf(
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.TRACKERS_ID, 12),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.TRACKING_COOKIES_ID, 5),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.FINGERPRINTERS_ID, 3),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.SUSPICIOUS_FINGERPRINTERS_ID, 2),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.SOCIAL_ID, 4),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.CRYPTOMINERS_ID, 1),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.BOUNCETRACKERS_ID, 6),
                ),
            )

        assertEquals(33L, stats.totalBlockedLastSevenDays)
        assertEquals(12L, stats.trackers)
        assertEquals(5L, stats.trackingCookies)
        assertEquals(5L, stats.fingerprinters)
        assertEquals(4L, stats.socialTrackers)
        assertEquals(1L, stats.cryptominers)
        assertEquals(6L, stats.bounceTrackers)
        assertTrue(stats.isAvailable)
    }

    @Test
    fun ignoresNegativeCountsAndKeepsUnknownTypesInTheTotal() {
        val stats =
            aggregatePrivacyBlockingEvents(
                listOf(
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.TRACKERS_ID, 7),
                    PrivacyBlockingEvent(ContentBlockingController.TrackingDbEvent.TRACKERS_ID, -3),
                    PrivacyBlockingEvent(Int.MAX_VALUE, 2),
                ),
            )

        assertEquals(9L, stats.totalBlockedLastSevenDays)
        assertEquals(7L, stats.trackers)
    }

    @Test
    fun emptyRangeReturnsAvailableZeroStats() {
        val stats = aggregatePrivacyBlockingEvents(emptyList())

        assertEquals(0L, stats.totalBlockedLastSevenDays)
        assertEquals(0L, stats.trackers)
        assertTrue(stats.isAvailable)
    }
}
