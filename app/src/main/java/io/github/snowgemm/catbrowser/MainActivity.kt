package io.github.snowgemm.catbrowser

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.ui.BrowserScreen
import io.github.snowgemm.catbrowser.ui.BrowserStartupScreen
import io.github.snowgemm.catbrowser.ui.CatBrowserTheme

class MainActivity : ComponentActivity() {
    private var activeController: BrowserController? = null
    private var pendingExternalUrl: String? = null
    private var isActivityStarted = false
    private var isPipMode by mutableStateOf(false)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
        currentController()?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        currentController()?.onAppForegrounded()
    }

    override fun onStop() {
        isActivityStarted = false
        currentController()?.onAppBackgrounded()
        super.onStop()
    }

    private fun currentController(): BrowserController? =
        activeController ?: (application as CatBrowserApplication).existingBrowserController()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingExternalUrl = intent?.dataString
        isPipMode = isInPictureInPictureMode

        setContent {
            var controller by remember { mutableStateOf<BrowserController?>(null) }
            var startupError by remember { mutableStateOf<String?>(null) }
            var startupAttempt by remember { mutableIntStateOf(0) }

            LaunchedEffect(startupAttempt) {
                startupError = null

                // Let the minimal launch UI reach a frame before initializing GeckoView.
                withFrameNanos { }

                runCatching {
                    (application as CatBrowserApplication).initializeBrowserController()
                }.onSuccess { initialized ->
                    pendingExternalUrl?.let(initialized::openExternalUrl)
                    pendingExternalUrl = null
                    activeController = initialized
                    if (isActivityStarted) initialized.onAppForegrounded()
                    else initialized.onAppBackgrounded()
                    controller = initialized
                    withFrameNanos { }
                    reportFullyDrawn()
                }.onFailure { error ->
                    startupError = error.message ?: "Không thể khởi tạo trình duyệt."
                }
            }

            CatBrowserTheme(themeMode = controller?.themeMode ?: "system") {
                AnimatedContent(
                    targetState = controller,
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 24 }) togetherWith
                            fadeOut(tween(140))
                    },
                    label = "browser-startup-transition"
                ) { initialized ->
                    if (initialized == null) {
                        BrowserStartupScreen(
                            errorMessage = startupError,
                            onRetry = { startupAttempt++ }
                        )
                    } else {
                        BrowserScreen(
                            controller = initialized,
                            activity = this@MainActivity,
                            isPictureInPictureMode = isPipMode,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val url = intent.dataString ?: return
        val controller = currentController()

        if (controller != null) {
            activeController = controller
            controller.openExternalUrl(url)
        } else {
            pendingExternalUrl = url
        }
    }
}
