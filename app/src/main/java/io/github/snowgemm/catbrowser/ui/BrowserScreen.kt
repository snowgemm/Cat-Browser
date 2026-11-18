package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import io.github.snowgemm.catbrowser.R
import androidx.compose.ui.viewinterop.AndroidView
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.BrowserEntry
import org.mozilla.geckoview.GeckoView
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val CatDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD6B96C), onPrimary = Color(0xFF211A08),
    secondary = Color(0xFFE3C77E), onSecondary = Color(0xFF241C0A),
    background = Color(0xFF101318), onBackground = Color(0xFFF1F1ED),
    surface = Color(0xFF1C222B), onSurface = Color(0xFFF1F1ED),
    surfaceVariant = Color(0xFF272E38), onSurfaceVariant = Color(0xFFB8BDC5),
    outline = Color(0xFF343A43), secondaryContainer = Color(0xFF3A3321),
    onSecondaryContainer = Color(0xFFEAD69D)
)

private val CatLightColorScheme = lightColorScheme(
    primary = Color(0xFFA98535), onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E4C5), onPrimaryContainer = Color(0xFF35280C),
    secondary = Color(0xFFA98535), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E8D5), onSecondaryContainer = Color(0xFF3D321C),
    background = Color(0xFFF8F7F3), onBackground = Color(0xFF242424),
    surface = Color.White, onSurface = Color(0xFF242424),
    surfaceVariant = Color(0xFFF0EEE8), onSurfaceVariant = Color(0xFF5F5B51),
    outline = Color(0xFFE5E1D7)
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
fun BrowserScreen(controller: BrowserController) {
    var destination by remember { mutableStateOf(BrowserDestination.BROWSER) }

    BackHandler(enabled = destination != BrowserDestination.BROWSER) {
        destination = BrowserDestination.BROWSER
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            (fadeIn(tween(190)) + slideInVertically(tween(190)) { it / 24 }) togetherWith
                (fadeOut(tween(130)) + slideOutVertically(tween(130)) { -it / 24 })
        },
        label = "browser-navigation"
    ) { target ->
        when (target) {
            BrowserDestination.BROWSER -> BrowserMainScreen(
                controller = controller,
                onOpenTabs = { destination = BrowserDestination.TABS },
                onOpenExtensions = { destination = BrowserDestination.EXTENSIONS },
                onOpenSettings = { destination = BrowserDestination.SETTINGS },
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
                destination = BrowserDestination.BROWSER
            }
            BrowserDestination.DOWNLOADS -> BrowserDownloadsScreen(controller.downloadManager) {
                destination = BrowserDestination.BROWSER
            }
        }
    }
}

private enum class BrowserDestination { BROWSER, TABS, EXTENSIONS, SETTINGS, DOWNLOADS }

@Composable
private fun BrowserMainScreen(
    controller: BrowserController,
    onOpenTabs: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    LaunchedEffect(showHistory) {
        if (showHistory) controller.loadHistory()
    }
    LaunchedEffect(showBookmarks) {
        if (showBookmarks) controller.loadBookmarks()
    }

    val tab = controller.activeTab ?: return
    var address by remember(tab.id) { mutableStateOf(TextFieldValue(tab.url)) }
    var addressFocused by remember(tab.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(tab.id, tab.url) {
        if (!addressFocused) address = TextFieldValue(tab.url)
    }

    fun navigate() {
        val input = address.text.trim()
        if (input.isNotEmpty()) {
            controller.navigate(input)
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = showFind || tab.canGoBack) {
        if (showFind) {
            showFind = false
            findQuery = ""
            controller.clearFindInPage()
        } else {
            controller.goBack()
        }
    }

    val context = LocalContext.current
    val androidPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        controller.resolveAndroidPermissionRequest(
            results.isNotEmpty() && results.values.all { it }
        )
    }

    LaunchedEffect(controller.pendingAndroidPermissionRequest?.id) {
        val request = controller.pendingAndroidPermissionRequest ?: return@LaunchedEffect
        val alreadyGranted = request.permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            controller.resolveAndroidPermissionRequest(true)
        } else {
            androidPermissionLauncher.launch(request.permissions.toTypedArray())
        }
    }

    LaunchedEffect(showFind, findQuery, tab.id) {
        if (showFind) {
            if (findQuery.isBlank()) {
                controller.clearFindInPage()
            } else {
                delay(250)
                controller.findInPage(findQuery)
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        AnimatedVisibility(
            visible = tab.isPrivate,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120))
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF292431))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrowserGlyph(
                    BrowserGlyphType.INCOGNITO,
                    tint = Color(0xFFE6DDF0),
                    iconSize = 19.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Chế độ ẩn danh",
                        color = Color(0xFFF2EDF7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Không lưu lịch sử trên máy; website và mạng vẫn có thể thấy lượt truy cập.",
                        color = Color(0xFFC9C0D2),
                        fontSize = 10.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = tab.error != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 3 },
            exit = fadeOut(tween(100))
        ) {
            tab.error?.let { message ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 15.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { controller.stopOrReload() }) { Text("Thử lại") }
                }
            }
        }

        Surface(
            Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.background
        ) {
            val session = tab.session
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Đang khởi tạo trang…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                key(tab.id) {
                    AndroidView(
                        factory = { context ->
                            GeckoView(context).apply {
                                setSession(session)
                                controller.onSessionViewAttached(tab.id, session)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (view.getSession() !== session) {
                                view.releaseSession()
                                view.setSession(session)
                                controller.onSessionViewAttached(tab.id, session)
                            }
                        },
                        onRelease = { view ->
                            controller.onSessionViewDetached(tab.id, session)
                            view.releaseSession()
                        }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            DockAction(BrowserGlyphType.BACK, "Quay lại", tab.canGoBack) { controller.goBack() }
            DockAction(BrowserGlyphType.FORWARD, "Tiến", tab.canGoForward) { controller.goForward() }

            Surface(
                modifier = Modifier.weight(1f).height(46.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrowserGlyph(
                        if (tab.url.startsWith("https://", ignoreCase = true)) {
                            BrowserGlyphType.LOCK
                        } else {
                            BrowserGlyphType.GLOBE
                        },
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(7.dp))
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f).onFocusChanged { state ->
                            if (state.isFocused && !addressFocused) {
                                address = address.copy(
                                    selection = TextRange(0, address.text.length)
                                )
                            } else if (!state.isFocused && addressFocused) {
                                address = TextFieldValue(tab.url)
                            }
                            addressFocused = state.isFocused
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() }),
                        decorationBox = { inner ->
                            Box {
                                if (address.text.isEmpty()) {
                                    Text(
                                        "Tìm kiếm hoặc nhập địa chỉ",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (addressFocused) {
                        DockAction(
                            BrowserGlyphType.CLEAR,
                            "Xoá nội dung",
                            address.text.isNotEmpty()
                        ) {
                            address = TextFieldValue("")
                        }
                    } else if (tab.isLoading) {
                        Text(
                            tab.progress.toString() + "%",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        DockAction(BrowserGlyphType.GO, "Truy cập", true) { navigate() }
                    }
                }
            }

            TabSwitcherAction(controller.tabs.size) { onOpenTabs() }
            Box {
                DockAction(BrowserGlyphType.MORE, "Menu", true) { showMenu = true }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(248.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tab mới") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.NEW_TAB) },
                        onClick = { showMenu = false; controller.newTab() }
                    )
                    DropdownMenuItem(
                        text = { Text("Trang chủ") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.HOME) },
                        onClick = { showMenu = false; controller.goHome() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (tab.isLoading) "Dừng tải" else "Tải lại") },
                        leadingIcon = {
                            BrowserGlyph(
                                if (tab.isLoading) BrowserGlyphType.CLEAR else BrowserGlyphType.RELOAD
                            )
                        },
                        onClick = { showMenu = false; controller.stopOrReload() }
                    )
                    DropdownMenuItem(
                        text = { Text("Tab ẩn danh mới") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.INCOGNITO) },
                        onClick = { showMenu = false; controller.newPrivateTab() },
                        enabled = controller.tabs.size < 20
                    )
                    if (controller.privateTabCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Đóng tất cả tab ẩn danh (" + controller.privateTabCount + ")") },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.CLOSE) },
                            onClick = { showMenu = false; controller.closeAllPrivateTabs() }
                        )
                    }
                    if (!tab.isPrivate) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (controller.isBookmarked(tab.url)) {
                                        "Bỏ dấu trang"
                                    } else {
                                        "Thêm dấu trang"
                                    }
                                )
                            },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.BOOKMARK) },
                            onClick = { showMenu = false; controller.toggleBookmark() }
                        )
                    }
                    MenuDivider()
                    DropdownMenuItem(
                        text = { Text("Tiện ích mở rộng") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.EXTENSIONS) },
                        onClick = { showMenu = false; onOpenExtensions() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (controller.isBookmarksLoaded) {
                                    "Dấu trang (" + controller.bookmarks.size + ")"
                                } else {
                                    "Dấu trang"
                                }
                            )
                        },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.BOOKS) },
                        onClick = { showMenu = false; showBookmarks = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Lịch sử") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.HISTORY) },
                        onClick = { showMenu = false; showHistory = true }
                    )
                    MenuDivider()
                    DropdownMenuItem(
                        text = { Text("Cài đặt") },
                        leadingIcon = { BrowserGlyph(BrowserGlyphType.SETTINGS) },
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                }
            }
        }
    }

    if (showHistory) {
        EntryDialog(
            "Lịch sử",
            controller.history,
            "Chưa có lịch sử duyệt web.",
            { showHistory = false },
            { controller.openEntry(it.url); showHistory = false },
            { controller.clearHistory() },
            isLoading = !controller.isHistoryLoaded || controller.isHistoryLoading
        )
    }
    if (showBookmarks) {
        EntryDialog(
            "Dấu trang",
            controller.bookmarks,
            "Bạn chưa lưu trang nào.",
            { showBookmarks = false },
            { controller.openEntry(it.url); showBookmarks = false },
            isLoading = !controller.isBookmarksLoaded || controller.isBookmarksLoading
        )
    }
}

private enum class BrowserGlyphType {
    BACK, FORWARD, LOCK, GLOBE, CLEAR, CLOSE, GO, TABS, MORE, INCOGNITO,
    HOME, RELOAD, BOOKMARK, EXTENSIONS, BOOKS, HISTORY, NEW_TAB, SETTINGS
}

@Composable
private fun BrowserGlyph(
    type: BrowserGlyphType,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    Canvas(modifier.size(iconSize)) {
        val unit = size.minDimension / 24f
        val strokeWidth = 1.8f * unit

        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = tint,
                start = point(x1, y1),
                end = point(x2, y2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        when (type) {
            BrowserGlyphType.BACK -> {
                line(19f, 12f, 5f, 12f)
                line(5f, 12f, 11f, 6f)
                line(5f, 12f, 11f, 18f)
            }
            BrowserGlyphType.FORWARD, BrowserGlyphType.GO -> {
                line(5f, 12f, 19f, 12f)
                line(19f, 12f, 13f, 6f)
                line(19f, 12f, 13f, 18f)
            }
            BrowserGlyphType.LOCK -> {
                drawRoundRect(
                    tint,
                    topLeft = point(5f, 10f),
                    size = Size(14f * unit, 10f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                val shackle = Path().apply {
                    moveTo(8f * unit, 10f * unit)
                    lineTo(8f * unit, 7.5f * unit)
                    cubicTo(8f * unit, 2.5f * unit, 16f * unit, 2.5f * unit, 16f * unit, 7.5f * unit)
                    lineTo(16f * unit, 10f * unit)
                }
                drawPath(shackle, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                line(12f, 14f, 12f, 17f)
            }
            BrowserGlyphType.GLOBE -> {
                drawCircle(tint, 9f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                drawOval(
                    tint,
                    topLeft = point(8f, 3f),
                    size = Size(8f * unit, 18f * unit),
                    style = Stroke(strokeWidth)
                )
                line(3.5f, 12f, 20.5f, 12f)
            }
            BrowserGlyphType.CLEAR, BrowserGlyphType.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            BrowserGlyphType.INCOGNITO -> {
                // Hat brim and glasses create a recognizable private-browsing mark.
                line(3f, 9f, 21f, 9f)
                line(7f, 9f, 9f, 5f)
                line(9f, 5f, 15f, 5f)
                line(15f, 5f, 17f, 9f)
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 12f),
                    size = Size(6f * unit, 5f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                drawRoundRect(
                    tint,
                    topLeft = point(14f, 12f),
                    size = Size(6f * unit, 5f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                line(10f, 14.5f, 14f, 14.5f)
                line(7f, 18f, 12f, 21f)
                line(12f, 21f, 17f, 18f)
            }
            BrowserGlyphType.TABS -> {
                drawRoundRect(
                    tint,
                    topLeft = point(7f, 4f),
                    size = Size(13f * unit, 15f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 7f),
                    size = Size(13f * unit, 14f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(strokeWidth)
                )
            }
            BrowserGlyphType.MORE -> {
                for (y in listOf(5f, 12f, 19f)) {
                    drawCircle(tint, 1.6f * unit, point(12f, y))
                }
            }
            BrowserGlyphType.HOME -> {
                val home = Path().apply {
                    moveTo(3f * unit, 11f * unit)
                    lineTo(12f * unit, 4f * unit)
                    lineTo(21f * unit, 11f * unit)
                    moveTo(5.5f * unit, 10f * unit)
                    lineTo(5.5f * unit, 20f * unit)
                    lineTo(18.5f * unit, 20f * unit)
                    lineTo(18.5f * unit, 10f * unit)
                    moveTo(10f * unit, 20f * unit)
                    lineTo(10f * unit, 14f * unit)
                    lineTo(14f * unit, 14f * unit)
                    lineTo(14f * unit, 20f * unit)
                }
                drawPath(home, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.RELOAD -> {
                drawArc(
                    tint,
                    startAngle = 42f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = point(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                line(17f, 4.5f, 20f, 5f)
                line(20f, 5f, 19.5f, 8f)
            }
            BrowserGlyphType.BOOKMARK -> {
                val bookmark = Path().apply {
                    moveTo(6f * unit, 4f * unit)
                    lineTo(18f * unit, 4f * unit)
                    lineTo(18f * unit, 21f * unit)
                    lineTo(12f * unit, 16.5f * unit)
                    lineTo(6f * unit, 21f * unit)
                    close()
                }
                drawPath(bookmark, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.EXTENSIONS -> {
                val puzzle = Path().apply {
                    moveTo(9f * unit, 4f * unit)
                    lineTo(15f * unit, 4f * unit)
                    lineTo(15f * unit, 7f * unit)
                    cubicTo(15f * unit, 9f * unit, 19f * unit, 8.5f * unit, 19f * unit, 11.5f * unit)
                    cubicTo(19f * unit, 14.5f * unit, 15f * unit, 14f * unit, 15f * unit, 17f * unit)
                    lineTo(15f * unit, 20f * unit)
                    lineTo(9f * unit, 20f * unit)
                    lineTo(9f * unit, 17f * unit)
                    cubicTo(9f * unit, 14f * unit, 5f * unit, 14.5f * unit, 5f * unit, 11.5f * unit)
                    cubicTo(5f * unit, 8.5f * unit, 9f * unit, 9f * unit, 9f * unit, 7f * unit)
                    close()
                }
                drawPath(puzzle, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.BOOKS -> {
                val books = Path().apply {
                    moveTo(4f * unit, 6f * unit)
                    lineTo(10f * unit, 4f * unit)
                    lineTo(10f * unit, 19f * unit)
                    lineTo(4f * unit, 21f * unit)
                    close()
                    moveTo(10f * unit, 4f * unit)
                    lineTo(16f * unit, 6f * unit)
                    lineTo(16f * unit, 21f * unit)
                    lineTo(10f * unit, 19f * unit)
                    moveTo(18f * unit, 6f * unit)
                    lineTo(21f * unit, 7f * unit)
                    lineTo(21f * unit, 20f * unit)
                    lineTo(18f * unit, 19f * unit)
                    close()
                }
                drawPath(books, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BrowserGlyphType.HISTORY -> {
                drawArc(
                    tint,
                    startAngle = 35f,
                    sweepAngle = 325f,
                    useCenter = false,
                    topLeft = point(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                line(12f, 7f, 12f, 12f)
                line(12f, 12f, 16f, 14f)
                line(3f, 8f, 3f, 4f)
                line(3f, 4f, 7f, 4f)
            }
            BrowserGlyphType.NEW_TAB -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            BrowserGlyphType.SETTINGS -> {
                drawCircle(tint, 7.5f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                drawCircle(tint, 2.4f * unit, point(12f, 12f), style = Stroke(strokeWidth))
                for (index in 0 until 8) {
                    val angle = index * Math.PI / 4.0
                    val inner = 8.5f
                    val outer = 10.8f
                    line(
                        12f + cos(angle).toFloat() * inner,
                        12f + sin(angle).toFloat() * inner,
                        12f + cos(angle).toFloat() * outer,
                        12f + sin(angle).toFloat() * outer
                    )
                }
            }
        }
    }
}

@Composable
private fun DockAction(
    icon: BrowserGlyphType,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.widthIn(min = 40.dp)
            .height(44.dp)
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            BrowserGlyph(icon)
        }
    }
}

@Composable
private fun TabSwitcherAction(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(min = 42.dp)
            .height(44.dp)
            .semantics { contentDescription = "Các tab, " + count + " tab đang mở" }
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BrowserGlyph(BrowserGlyphType.TABS, iconSize = 18.dp)
            Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    )
}

@Composable
private fun TabsOverview(
    controller: BrowserController,
    onBack: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Các tab",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    controller.regularTabCount.toString() + " thường · " +
                        controller.privateTabCount + " ẩn danh · " + controller.tabs.size + "/20",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (controller.privateTabCount > 0) {
                TextButton(onClick = controller::closeAllPrivateTabs) {
                    Text("Đóng tab ẩn danh")
                }
            }
            IconButton(onClick = onBack) {
                BrowserGlyph(BrowserGlyphType.CLOSE, tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 154.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(controller.tabs, key = { it.id }) { tab ->
                val domain = tab.url
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                    .ifBlank { "Trang mới" }
                val mark = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                val selected = tab.id == controller.selectedTabId
                val cardBackground = if (tab.isPrivate) Color(0xFF292431)
                    else MaterialTheme.colorScheme.surface
                val cardText = if (tab.isPrivate) Color(0xFFF2EDF7)
                    else MaterialTheme.colorScheme.onSurface
                val cardMuted = if (tab.isPrivate) Color(0xFFC9C0D2)
                    else MaterialTheme.colorScheme.onSurfaceVariant

                Surface(
                    onClick = {
                        controller.selectTab(tab.id)
                        onBack()
                    },
                    shape = RoundedCornerShape(19.dp),
                    color = cardBackground,
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 1.25.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else if (tab.isPrivate) Color(0xFF776B86)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(164.dp).animateItem()
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(11.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (tab.isPrivate) Color(0xFF443B50)
                                else if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        mark,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                domain,
                                Modifier.weight(1f),
                                color = cardMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { controller.closeTab(tab.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                BrowserGlyph(
                                    BrowserGlyphType.CLEAR,
                                    iconSize = 17.dp,
                                    tint = cardMuted
                                )
                            }
                        }

                        if (tab.isPrivate) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BrowserGlyph(
                                    BrowserGlyphType.INCOGNITO,
                                    tint = Color(0xFFE6DDF0),
                                    iconSize = 14.dp
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Tab ẩn danh",
                                    color = Color(0xFFE6DDF0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            tab.title.ifBlank { "Tab mới" },
                            color = cardText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected) {
                                Box(
                                    Modifier.size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Đang xem",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    "Chuyển đến tab",
                                    color = cardMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNewTab,
                    enabled = controller.tabs.size < 20,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    BrowserGlyph(
                        BrowserGlyphType.NEW_TAB,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Tab thường", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onNewPrivateTab,
                    enabled = controller.tabs.size < 20,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    BrowserGlyph(
                        BrowserGlyphType.INCOGNITO,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Ẩn danh", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    entries: List<BrowserEntry>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (BrowserEntry) -> Unit,
    onClear: (() -> Unit)? = null,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (isLoading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Đang tải dữ liệu…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (entries.isEmpty()) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(entries, key = { it.url }) { entry ->
                        TextButton(onClick = { onSelect(entry) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    entry.url,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onClear != null) {
                TextButton(onClick = onClear) { Text("Xoá lịch sử") }
            } else {
                TextButton(onClick = onDismiss) { Text("Xong") }
            }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    )
}
