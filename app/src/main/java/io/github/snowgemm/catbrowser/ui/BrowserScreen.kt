package io.github.snowgemm.catbrowser.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.R
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    controller: BrowserController,
    activity: Activity,
    isPictureInPictureMode: Boolean,
) {
    DisposableEffect(controller, activity) {
        controller.setPictureInPictureHost(
            onEnter = {
                if (
                    !activity.packageManager.hasSystemFeature(
                        android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
                    )
                ) {
                    false
                } else {
                    runCatching {
                        activity.enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build(),
                        )
                    }.getOrDefault(false)
                }
            },
            onExit = { activity.moveTaskToBack(true) },
        )
        onDispose { controller.clearPictureInPictureHost() }
    }

    if (isPictureInPictureMode) {
        val session = controller.pictureInPictureSession
        if (session == null) {
            Box(Modifier.fillMaxSize())
        } else {
            key(session) {
                AndroidView(
                    factory = { context ->
                        GeckoView(context).apply {
                            setActivityContextDelegate { activity }
                            setSession(session)
                            controller.onPictureInPictureViewAttached(session)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.setActivityContextDelegate { activity }
                        if (view.getSession() !== session) {
                            view.releaseSession()
                            view.setSession(session)
                            controller.onPictureInPictureViewAttached(session)
                        }
                    },
                    onRelease = { view ->
                        controller.onPictureInPictureViewDetached(session)
                        view.releaseSession()
                    },
                )
            }
        }
        return
    }

    val isFullScreen = controller.activeTab?.isFullScreen == true

    SideEffect {
        val insetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isFullScreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        onDispose {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var destination by remember { mutableStateOf(BrowserDestination.BROWSER) }
    var settingsReturnDestination by remember { mutableStateOf(BrowserDestination.BROWSER) }

    BackHandler(enabled = destination != BrowserDestination.BROWSER) {
        destination = if (destination == BrowserDestination.SETTINGS) {
            settingsReturnDestination
        } else {
            BrowserDestination.BROWSER
        }
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val movingForward = targetState != BrowserDestination.BROWSER
            (fadeIn(tween(170)) + slideInHorizontally(
                initialOffsetX = { width -> if (movingForward) width / 5 else -width / 5 },
                animationSpec = spring<IntOffset>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )) togetherWith
                (fadeOut(tween(130)) + slideOutHorizontally(
                    targetOffsetX = { width -> if (movingForward) -width / 7 else width / 7 },
                    animationSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ))
        },
        label = "browser-navigation",
    ) { target ->
        when (target) {
            BrowserDestination.BROWSER -> BrowserMainScreen(
                controller = controller,
                activity = activity,
                onOpenTabs = { destination = BrowserDestination.TABS },
                onOpenExtensions = { destination = BrowserDestination.EXTENSIONS },
                onOpenAssistant = { destination = BrowserDestination.ASSISTANT },
                onOpenSettings = {
                    settingsReturnDestination = BrowserDestination.BROWSER
                    destination = BrowserDestination.SETTINGS
                },
                onOpenDownloads = { destination = BrowserDestination.DOWNLOADS },
            )
            BrowserDestination.ASSISTANT -> AssistantScreen(
                controller = controller,
                onBack = { destination = BrowserDestination.BROWSER },
            )
            BrowserDestination.TABS -> TabsOverview(
                controller = controller,
                onBack = { destination = BrowserDestination.BROWSER },
                onNewTab = {
                    controller.newTab()
                    destination = BrowserDestination.BROWSER
                },
                onNewPrivateTab = {
                    controller.newPrivateTab()
                    destination = BrowserDestination.BROWSER
                },
            )
            BrowserDestination.EXTENSIONS -> ExtensionsScreen(
                manager = controller.extensionsManager,
                onBack = { destination = BrowserDestination.BROWSER },
                onOpenListing = { url ->
                    controller.openExternalUrl(url)
                    destination = BrowserDestination.BROWSER
                },
            )
            BrowserDestination.SETTINGS -> SettingsScreen(controller) {
                destination = settingsReturnDestination
            }
            BrowserDestination.DOWNLOADS -> BrowserDownloadsScreen(controller.downloadManager) {
                destination = BrowserDestination.BROWSER
            }
        }
    }
}

private enum class BrowserDestination { BROWSER, TABS, EXTENSIONS, ASSISTANT, SETTINGS, DOWNLOADS }
