package io.github.snowgemm.catbrowser.ui

import android.animation.ValueAnimator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically

/**
 * Keeps non-essential UI motion aligned with Android's system animator setting.
 * Compose animation specs still provide the normal path; disabled system animators
 * make state changes immediate instead of fading or sliding.
 */
internal fun isBrowserMotionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

internal object BrowserMotionPolicy {
    fun durationMillis(enabled: Boolean, preferredDurationMillis: Int): Int {
        require(preferredDurationMillis >= 0) { "Animation duration must not be negative." }
        return if (enabled) preferredDurationMillis else 0
    }
}

internal fun browserFadeEnter(
    enabled: Boolean,
    durationMillis: Int,
): EnterTransition =
    if (enabled) {
        fadeIn(tween(durationMillis))
    } else {
        EnterTransition.None
    }

internal fun browserFadeSlideEnter(
    enabled: Boolean,
    durationMillis: Int,
    offsetDivisor: Int,
): EnterTransition {
    require(offsetDivisor > 0) { "Slide offset divisor must be positive." }
    return if (enabled) {
        fadeIn(tween(durationMillis)) +
            slideInVertically(tween(durationMillis)) { fullHeight -> -fullHeight / offsetDivisor }
    } else {
        EnterTransition.None
    }
}

internal fun browserFadeExit(
    enabled: Boolean,
    durationMillis: Int,
): ExitTransition =
    if (enabled) {
        fadeOut(tween(durationMillis))
    } else {
        ExitTransition.None
    }
