package io.github.snowgemm.catbrowser.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import io.github.snowgemm.catbrowser.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.START_PAGE_URL
import io.github.snowgemm.catbrowser.browser.areRequestedAndroidPermissionsGranted
import io.github.snowgemm.catbrowser.browser.BrowserEntry
import io.github.snowgemm.catbrowser.update.UpdateChannel
import org.mozilla.geckoview.GeckoView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
                onOpenTabs = { destination = BrowserDestination.TABS },
                onOpenExtensions = { destination = BrowserDestination.EXTENSIONS },
                onOpenSettings = {
                    settingsReturnDestination = BrowserDestination.BROWSER
                    destination = BrowserDestination.SETTINGS
                },
                onOpenDownloads = { destination = BrowserDestination.DOWNLOADS }
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
            BrowserDestination.EXTENSIONS -> ExtensionsScreen(controller.extensionsManager) {
                destination = BrowserDestination.BROWSER
            }
            BrowserDestination.SETTINGS -> SettingsScreen(controller) {
                destination = settingsReturnDestination
            }
            BrowserDestination.DOWNLOADS -> BrowserDownloadsScreen(controller.downloadManager) {
                destination = BrowserDestination.BROWSER
            }
        }
    }
}

private enum class BrowserDestination { BROWSER, TABS, EXTENSIONS, SETTINGS, DOWNLOADS }

@OptIn(ExperimentalMaterial3Api::class)
