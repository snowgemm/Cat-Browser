package io.github.snowgemm.catbrowser.ui

import android.app.Activity
import android.content.Intent
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.R
import io.github.snowgemm.catbrowser.BrowserPictureInPictureActivity


private val CatDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C9F7), onPrimary = Color(0xFF10243C),
    secondary = Color(0xFFE6C47D), onSecondary = Color(0xFF2A210F),
    background = Color(0xFF10161E), onBackground = Color(0xFFF1F4F8),
    surface = Color(0xFF1B2633), onSurface = Color(0xFFF1F4F8),
    surfaceVariant = Color(0xFF293747), onSurfaceVariant = Color(0xFFBAC7D5),
    outline = Color(0xFF405164), secondaryContainer = Color(0xFF34465C),
    onSecondaryContainer = Color(0xFFEAF1FB)
)

private val CatLightColorScheme = lightColorScheme(
    primary = Color(0xFF416B9B), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F8), onPrimaryContainer = Color(0xFF173451),
    secondary = Color(0xFF8F713B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E7D4), onSecondaryContainer = Color(0xFF392B13),
    background = Color(0xFFF3F6FA), onBackground = Color(0xFF1B2530),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B2530),
    surfaceVariant = Color(0xFFE8EEF5), onSurfaceVariant = Color(0xFF526171),
    outline = Color(0xFFD2DCE8)
)


@Composable
fun CatBrowserTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    MaterialTheme(colorScheme = if (useDark) CatDarkColorScheme else CatLightColorScheme, content = content)
}

@Composable
fun BrowserStartupScreen(errorMessage: String?, onRetry: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "startup-logo")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "startup-logo-rotation"
    )

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Cat Browser",
                modifier = Modifier.size(76.dp).graphicsLayer { rotationZ = rotation }
            )
            Text(
                "Cat Browser",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (errorMessage == null) {
                Text(
                    "Đang khởi tạo trình duyệt…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    Modifier.width(116.dp).padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                    Text("Thử lại")
                }
            }
        }
    }
}

@Composable
fun BrowserScreen(controller: BrowserController, activity: Activity) {
    DisposableEffect(controller, activity) {
        controller.setPictureInPictureLaunchHost {
            if (
                !activity.packageManager.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
                )
            ) {
                false
            } else {
                runCatching {
                    activity.startActivity(
                        Intent(activity, BrowserPictureInPictureActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                }.getOrDefault(false)
            }
        }
        onDispose { controller.clearPictureInPictureLaunchHost() }
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
                    stiffness = Spring.StiffnessMediumLow
                )
            )) togetherWith
                (fadeOut(tween(130)) + slideOutHorizontally(
                    targetOffsetX = { width -> if (movingForward) -width / 7 else width / 7 },
                    animationSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ))
        },
        label = "browser-navigation"
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
                onBack = { destination = BrowserDestination.BROWSER }
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
                }
            )
            BrowserDestination.EXTENSIONS -> ExtensionsScreen(
                manager = controller.extensionsManager,
                onBack = { destination = BrowserDestination.BROWSER },
                onOpenListing = { url ->
                    controller.openExternalUrl(url)
                    destination = BrowserDestination.BROWSER
                }
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
