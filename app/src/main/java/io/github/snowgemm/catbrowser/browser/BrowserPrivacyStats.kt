package io.github.snowgemm.catbrowser.browser

import org.mozilla.geckoview.ContentBlockingController

internal data class BrowserPrivacyStats(
    val totalBlockedLastSevenDays: Long = 0,
    val trackers: Long = 0,
    val trackingCookies: Long = 0,
    val fingerprinters: Long = 0,
    val socialTrackers: Long = 0,
    val cryptominers: Long = 0,
    val bounceTrackers: Long = 0,
    val isAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)

internal data class PrivacyBlockingEvent(
    val type: Int,
    val count: Int
)

internal fun aggregatePrivacyBlockingEvents(
    events: List<PrivacyBlockingEvent>
): BrowserPrivacyStats {
    val validEvents = events.filter { it.count > 0 }
    fun countFor(vararg types: Int): Long {
        val acceptedTypes = types.toSet()
        return validEvents.asSequence()
            .filter { it.type in acceptedTypes }
            .sumOf { it.count.toLong() }
    }

    return BrowserPrivacyStats(
        totalBlockedLastSevenDays = validEvents.sumOf { it.count.toLong() },
        trackers = countFor(ContentBlockingController.TrackingDbEvent.TRACKERS_ID),
        trackingCookies = countFor(ContentBlockingController.TrackingDbEvent.TRACKING_COOKIES_ID),
        fingerprinters = countFor(
            ContentBlockingController.TrackingDbEvent.FINGERPRINTERS_ID,
            ContentBlockingController.TrackingDbEvent.SUSPICIOUS_FINGERPRINTERS_ID
        ),
        socialTrackers = countFor(ContentBlockingController.TrackingDbEvent.SOCIAL_ID),
        cryptominers = countFor(ContentBlockingController.TrackingDbEvent.CRYPTOMINERS_ID),
        bounceTrackers = countFor(ContentBlockingController.TrackingDbEvent.BOUNCETRACKERS_ID),
        isAvailable = true
    )
}
