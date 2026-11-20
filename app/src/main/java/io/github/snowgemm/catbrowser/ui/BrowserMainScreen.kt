package io.github.snowgemm.catbrowser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import io.github.snowgemm.catbrowser.browser.BrowserContextResourceType
import io.github.snowgemm.catbrowser.browser.areRequestedAndroidPermissionsGranted
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.START_PAGE_URL
import io.github.snowgemm.catbrowser.R
import io.github.snowgemm.catbrowser.update.UpdateChannel
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView


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

private fun suggestedPdfFileName(title: String): String {
    val safeTitle =
        title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(80)
            .ifBlank { "Cat Browser page" }
    return "$safeTitle.pdf"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserMainScreen(
    controller: BrowserController,
    activity: Activity,
    onOpenTabs: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        controller.loadHistory()
        controller.loadBookmarks()
        controller.updateManager.checkForStartupUpdates()
    }

    var promptRefresh by remember { mutableIntStateOf(0) }
    val updateManager = controller.updateManager
    val promptChannel = remember(updateManager.stableState, updateManager.betaState, updateManager.betaEnabled, promptRefresh) {
        updateManager.nextPromptChannel()
    }
    val promptState = when (promptChannel) {
        UpdateChannel.STABLE -> updateManager.stableState
        UpdateChannel.BETA -> updateManager.betaState
        null -> null
    }

    LaunchedEffect(showHistory) {
        if (showHistory) controller.loadHistory()
    }
    LaunchedEffect(showBookmarks) {
        if (showBookmarks) controller.loadBookmarks()
    }

    val tab = controller.activeTab ?: return
    val context = LocalContext.current
    val contextMenuRequest = controller.contextMenuRequest?.takeIf { it.tabId == tab.id }

    LaunchedEffect(tab.id) {
        controller.dismissContextMenu()
    }
    var pendingPdfTabId by remember { mutableStateOf<String?>(null) }
    val isVerifiedSecure = tab.pageSecurity?.isVerifiedSecure == true
    var attachedGeckoView by remember(tab.id) { mutableStateOf<GeckoView?>(null) }
    val extensionActions = controller.extensionsManager.actionsFor(tab.session)
    var address by remember(tab.id) {
        mutableStateOf(TextFieldValue(tab.url.takeUnless { it == START_PAGE_URL }.orEmpty()))
    }
    var addressFocused by remember(tab.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val addressFocusRequester = remember(tab.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val tabSwipeThreshold = with(LocalDensity.current) { 44.dp.toPx() }

    LaunchedEffect(tab.id, tab.url) {
        if (!addressFocused) {
            address = TextFieldValue(tab.url.takeUnless { it == START_PAGE_URL }.orEmpty())
        }
    }

    LaunchedEffect(
        tab.id,
        tab.url,
        tab.isLoading,
        attachedGeckoView,
        controller.isAppForegrounded
    ) {
        if (!controller.isAppForegrounded) return@LaunchedEffect
        val view = attachedGeckoView ?: return@LaunchedEffect
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        if (!tab.isPrivate && tab.previewBitmap == null &&
            thermalStatus < PowerManager.THERMAL_STATUS_MODERATE &&
            tab.url.startsWith("http", ignoreCase = true) && !tab.isLoading && tab.progress >= 100
        ) {
            delay(450)
            if (attachedGeckoView === view && view.getSession() === tab.session) {
                view.capturePixels().accept(
                    { bitmap -> bitmap?.let { controller.updateTabPreview(tab.id, it) } },
                    { /* Preview is optional; keep the previous thumbnail on capture errors. */ }
                )
            }
        }
    }

    val addressSuggestions by remember(tab.id) {
        derivedStateOf {
            buildList {
                val query = address.text.trim()
                fun matches(title: String, url: String): Boolean =
                    query.isBlank() || title.contains(query, ignoreCase = true) ||
                        url.contains(query, ignoreCase = true)

                controller.tabs
                    .filter { it.id != tab.id && it.isPrivate == tab.isPrivate && it.url.startsWith("http", true) }
                    .filter { matches(it.title, it.url) }
                    .forEach {
                        add(BrowserAddressSuggestion(it.title.ifBlank { it.url }, it.url, "Tab đang mở", it.id))
                    }
                controller.bookmarks
                    .filter { matches(it.title, it.url) }
                    .forEach { add(BrowserAddressSuggestion(it.title, it.url, "Dấu trang")) }
                if (!tab.isPrivate) {
                    controller.history
                        .filter { matches(it.title, it.url) }
                        .forEach { add(BrowserAddressSuggestion(it.title, it.url, "Lịch sử")) }
                }
            }.distinctBy { it.url }.take(8)
        }
    }

    fun navigate() {
        val input = address.text.trim()
        if (input.isNotEmpty()) {
            controller.navigate(input)
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = tab.isFullScreen || showFind || addressFocused || tab.canGoBack) {
        when {
            tab.isFullScreen -> controller.exitFullScreen()
            showFind -> {
                showFind = false
                findQuery = ""
                controller.clearFindInPage()
            }
            addressFocused -> {
                addressFocused = false
                focusManager.clearFocus()
                keyboard?.hide()
            }
            tab.canGoBack -> controller.goBack()
        }
    }

    val androidPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = controller.pendingAndroidPermissionRequest
        controller.resolveAndroidPermissionRequest(
            request != null && areRequestedAndroidPermissionsGranted(request.permissions, results)
        )
    }
    val extensionSaveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        controller.extensionsManager.resolveSaveAsRequest(uri)
    }
    val pdfSaveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf"),
        ) { uri ->
            val targetTabId = pendingPdfTabId
            pendingPdfTabId = null
            if (uri != null && targetTabId != null) {
                controller.saveTabAsPdf(targetTabId, uri)
            }
        }

    LaunchedEffect(controller.pdfExportMessage) {
        val message = controller.pdfExportMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        controller.consumePdfExportMessage()
    }

    LaunchedEffect(controller.extensionsManager.saveAsRequest?.id) {
        val request = controller.extensionsManager.saveAsRequest ?: return@LaunchedEffect
        extensionSaveAsLauncher.launch(request.suggestedFileName)
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

    LaunchedEffect(showFind, findQuery, tab.id, tab.url) {
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
            .then(if (tab.isFullScreen) Modifier else Modifier.statusBarsPadding())
            .then(if (tab.isFullScreen) Modifier else Modifier.navigationBarsPadding())
            .imePadding()
    ) {
        AnimatedVisibility(
            visible = tab.isPrivate && !tab.isFullScreen,
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
            visible = showFind && !tab.isFullScreen,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 3 },
            exit = fadeOut(tween(100))
        ) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Tìm trong trang") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    val result = controller.findInPageResult
                    Text(
                        when {
                            findQuery.isBlank() -> ""
                            result == null -> "…"
                            !result.found -> "0"
                            result.total < 0 -> result.current.toString()
                            else -> result.current.toString() + "/" + result.total
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    DockAction(
                        BrowserGlyphType.BACK,
                        "Kết quả trước",
                        findQuery.isNotBlank()
                    ) { controller.findNextInPage(false) }
                    DockAction(
                        BrowserGlyphType.FORWARD,
                        "Kết quả tiếp",
                        findQuery.isNotBlank()
                    ) { controller.findNextInPage(true) }
                    DockAction(BrowserGlyphType.CLEAR, "Đóng tìm kiếm", true) {
                        showFind = false
                        findQuery = ""
                        controller.clearFindInPage()
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = tab.error != null && !tab.isFullScreen,
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
            when {
                addressFocused -> AddressSuggestionsPanel(
                    query = address.text,
                    suggestions = addressSuggestions,
                    onSelect = { suggestion ->
                        if (suggestion.tabId != null) controller.selectTab(suggestion.tabId)
                        else controller.openEntry(suggestion.url)
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                    onSearch = ::navigate,
                    onDismiss = {
                        addressFocused = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                )
                tab.url == START_PAGE_URL && tab.session == null -> NewTabPage(
                    isPrivate = tab.isPrivate,
                    bookmarks = controller.bookmarks.toList(),
                    recentHistory = if (tab.isPrivate) emptyList() else controller.history.toList(),
                    openTabCount = controller.regularTabCount,
                    privacyStats = controller.privacyStats,
                    trackingProtectionEnabled = controller.trackingProtectionEnabled,
                    onRefreshPrivacyStats = { controller.refreshPrivacyStats(force = true) },
                    onOpenUrl = controller::openEntry,
                    onFocusAddress = {
                        addressFocused = true
                        addressFocusRequester.requestFocus()
                        keyboard?.show()
                    },
                    onAddShortcut = if (tab.isPrivate) { _, _ -> false } else controller::addBookmark
                )
                else -> {
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
                                        attachedGeckoView = this
                                        setActivityContextDelegate { activity }
                                        setSession(session)
                                        controller.onSessionViewAttached(tab.id, session)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    attachedGeckoView = view
                                    view.setActivityContextDelegate { activity }
                                    if (view.getSession() !== session) {
                                        view.releaseSession()
                                        view.setSession(session)
                                        controller.onSessionViewAttached(tab.id, session)
                                    }
                                },
                                onRelease = { view ->
                                    if (attachedGeckoView === view) attachedGeckoView = null
                                    controller.onSessionViewDetached(tab.id, session)
                                    view.releaseSession()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!tab.isFullScreen) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            ),
            tonalElevation = 1.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f)
                        .height(if (addressFocused) 52.dp else 48.dp)
                        .animateContentSize()
                        .pointerInput(tab.id, addressFocused, controller.tabs.size) {
                            if (!addressFocused && controller.tabs.size > 1) {
                                var dragDistance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                                    onDragEnd = {
                                        if (abs(dragDistance) >= tabSwipeThreshold) {
                                            controller.selectAdjacentTab(if (dragDistance < 0f) 1 else -1)
                                        }
                                        dragDistance = 0f
                                    },
                                    onDragCancel = { dragDistance = 0f }
                                )
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (addressFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.height(if (addressFocused) 48.dp else 46.dp)
                                .padding(start = 12.dp, end = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrowserGlyph(
                                when {
                                    addressFocused -> BrowserGlyphType.SEARCH
                                    isVerifiedSecure -> BrowserGlyphType.LOCK
                                    else -> BrowserGlyphType.GLOBE
                                },
                                modifier = Modifier.size(18.dp).semantics {
                                    contentDescription = when {
                                        addressFocused -> "Tìm kiếm"
                                        isVerifiedSecure -> "Kết nối HTTPS an toàn"
                                        else -> "Kết nối chưa được xác minh"
                                    }
                                },
                                tint = if (isVerifiedSecure) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(9.dp))
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                modifier = Modifier.weight(1f).focusRequester(addressFocusRequester).onFocusChanged { state ->
                                    if (state.isFocused && !addressFocused) {
                                        address = address.copy(selection = TextRange(0, address.text.length))
                                    } else if (!state.isFocused && addressFocused) {
                                        address = TextFieldValue(tab.url.takeUnless { it == START_PAGE_URL }.orEmpty())
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
                                if (address.text.isNotBlank()) {
                                    DockAction(BrowserGlyphType.GO, "Tìm kiếm hoặc truy cập", true) { navigate() }
                                }
                                DockAction(
                                    if (address.text.isNotEmpty()) BrowserGlyphType.CLEAR else BrowserGlyphType.CLOSE,
                                    if (address.text.isNotEmpty()) "Xoá nội dung" else "Đóng gợi ý",
                                    true
                                ) {
                                    if (address.text.isNotEmpty()) {
                                        address = TextFieldValue("")
                                    } else {
                                        addressFocused = false
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    }
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
                        AnimatedVisibility(
                            visible = tab.isLoading && !addressFocused,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(90))
                        ) {
                            LinearProgressIndicator(
                                progress = { tab.progress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                if (!addressFocused) {
                    if (extensionActions.isNotEmpty()) {
                        Row(
                            Modifier.widthIn(max = 88.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            extensionActions.take(2).forEach { action ->
                                Surface(
                                    onClick = { action.action.click() },
                                    modifier = Modifier.size(38.dp).semantics {
                                        contentDescription = action.title
                                    },
                                    shape = RoundedCornerShape(13.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val iconBitmap = action.iconBitmap
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap.asImageBitmap(),
                                                contentDescription = action.title,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        } else {
                                            Text(
                                                action.extensionName.firstOrNull()?.uppercase() ?: "E",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        action.badgeText?.takeIf(String::isNotBlank)?.let { badge ->
                                            Surface(
                                                modifier = Modifier.align(Alignment.TopEnd),
                                                color = Color(action.badgeBackgroundColor ?: 0xFF59616D.toInt()),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    badge.take(4),
                                                    Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TabSwitcherAction(controller.tabs.size, onOpenTabs)
                    DockAction(BrowserGlyphType.MORE, "Menu", true) { showMenu = true }
                }
            }
        }
        }
    }

    contextMenuRequest?.let { request ->
        val copyContextUrl: (String) -> Unit = { url ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                Toast.makeText(context, "Đã sao chép địa chỉ.", Toast.LENGTH_SHORT).show()
            }
            controller.dismissContextMenu()
        }
        val resourceLabel = when (request.resourceType) {
            BrowserContextResourceType.IMAGE -> "ảnh"
            BrowserContextResourceType.VIDEO -> "video"
            BrowserContextResourceType.AUDIO -> "âm thanh"
            BrowserContextResourceType.NONE -> "nội dung"
        }

        ModalBottomSheet(
            onDismissRequest = controller::dismissContextMenu,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    request.title ?: if (request.linkUrl != null) "Liên kết" else "Tùy chọn $resourceLabel",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                request.linkUrl?.let { url ->
                    BrowserMenuListAction(
                        BrowserGlyphType.GLOBE,
                        "Mở liên kết trong tab mới",
                        subtitle = url,
                        enabled = controller.canCreateTab
                    ) {
                        controller.openContextMenuTargetInNewTab(url)
                    }
                    BrowserMenuListAction(
                        BrowserGlyphType.BOOKMARK,
                        "Sao chép địa chỉ liên kết",
                        subtitle = url
                    ) {
                        copyContextUrl(url)
                    }
                }
                request.resourceUrl?.let { url ->
                    BrowserMenuListAction(
                        if (request.resourceType == BrowserContextResourceType.IMAGE) {
                            BrowserGlyphType.BOOKMARK
                        } else {
                            BrowserGlyphType.DOWNLOAD
                        },
                        "Mở $resourceLabel trong tab mới",
                        subtitle = url,
                        enabled = controller.canCreateTab
                    ) {
                        controller.openContextMenuTargetInNewTab(url)
                    }
                    BrowserMenuListAction(
                        BrowserGlyphType.GLOBE,
                        "Sao chép địa chỉ $resourceLabel",
                        subtitle = url
                    ) {
                        copyContextUrl(url)
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Cat Browser",
                                modifier = Modifier.size(29.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cat Browser", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            tab.title.ifBlank { "Tab mới" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (tab.isPrivate) Color(0xFF443B50)
                            else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            if (tab.isPrivate) "ẨN DANH" else "ĐANG DUYỆT",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = if (tab.isPrivate) Color(0xFFF2EDF7)
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.45.sp
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    BrowserMenuAction(BrowserGlyphType.BACK, "Quay lại", Modifier.weight(1f), tab.canGoBack) {
                        showMenu = false; controller.goBack()
                    }
                    BrowserMenuAction(BrowserGlyphType.FORWARD, "Tiến tới", Modifier.weight(1f), tab.canGoForward) {
                        showMenu = false; controller.goForward()
                    }
                    BrowserMenuAction(
                        if (tab.isLoading) BrowserGlyphType.CLEAR else BrowserGlyphType.RELOAD,
                        if (tab.isLoading) "Dừng tải" else "Tải lại",
                        Modifier.weight(1f)
                    ) { showMenu = false; controller.stopOrReload() }
                }
                Row(Modifier.fillMaxWidth()) {
                    BrowserMenuAction(BrowserGlyphType.NEW_TAB, "Tab mới", Modifier.weight(1f), controller.tabs.size < 20) {
                        showMenu = false; controller.newTab()
                    }
                    BrowserMenuAction(BrowserGlyphType.INCOGNITO, "Tab ẩn danh", Modifier.weight(1f), controller.tabs.size < 20) {
                        showMenu = false; controller.newPrivateTab()
                    }
                    BrowserMenuAction(BrowserGlyphType.HOME, "Trang chủ", Modifier.weight(1f)) {
                        showMenu = false; controller.goHome()
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Text("THƯ VIỆN", modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                BrowserMenuListAction(BrowserGlyphType.BOOKS, "Dấu trang",
                    subtitle = controller.bookmarks.size.toString() + " mục") {
                    showMenu = false; showBookmarks = true
                }
                BrowserMenuListAction(BrowserGlyphType.HISTORY, "Lịch sử") {
                    showMenu = false; showHistory = true
                }
                BrowserMenuListAction(BrowserGlyphType.DOWNLOAD, "Tải xuống") {
                    showMenu = false; onOpenDownloads()
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Text("CÔNG CỤ", modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                BrowserMenuListAction(
                    BrowserGlyphType.GLOBE,
                    if (tab.isDesktopMode) "Dùng giao diện di động" else "Yêu cầu trang máy tính",
                    subtitle = if (tab.isDesktopMode) "Đang bật chế độ máy tính" else "Đang dùng chế độ di động",
                ) {
                    showMenu = false
                    controller.setDesktopMode(tab.id, !tab.isDesktopMode)
                }
                BrowserMenuListAction(
                    BrowserGlyphType.DOWNLOAD,
                    "Lưu trang thành PDF",
                    subtitle = "Lưu bản sao PDF vào vị trí bạn chọn",
                    enabled = tab.session?.isOpen == true && !controller.isPdfExporting,
                ) {
                    showMenu = false
                    pendingPdfTabId = tab.id
                    pdfSaveLauncher.launch(suggestedPdfFileName(tab.title))
                }
                BrowserMenuListAction(
                    BrowserGlyphType.PRINT,
                    "In trang",
                    subtitle = "Mở hộp thoại in của Android",
                    enabled = tab.session?.isOpen == true,
                ) {
                    showMenu = false
                    if (!controller.printTab(tab.id)) {
                        Toast.makeText(context, "Trang chưa sẵn sàng để in.", Toast.LENGTH_SHORT).show()
                    }
                }
                BrowserMenuListAction(BrowserGlyphType.SEARCH, "Tìm trong trang") {
                    showMenu = false; showFind = true
                }
                BrowserMenuListAction(BrowserGlyphType.EXTENSIONS, "Tiện ích mở rộng") {
                    showMenu = false; onOpenExtensions()
                }
                BrowserMenuListAction(
                    BrowserGlyphType.AI,
                    "Cat AI",
                    subtitle = "Trợ lý có kiểm soát thao tác"
                ) {
                    showMenu = false; onOpenAssistant()
                }
                if (controller.recentlyClosedTabs.isNotEmpty()) {
                    BrowserMenuListAction(BrowserGlyphType.RELOAD, "Mở lại tab vừa đóng",
                        subtitle = "Tab gần đây đã đóng", enabled = controller.tabs.size < 20) {
                        showMenu = false; controller.restoreRecentlyClosedTab()
                    }
                }
                if (controller.privateTabCount > 0) {
                    BrowserMenuListAction(BrowserGlyphType.CLOSE, "Đóng tất cả tab ẩn danh",
                        subtitle = controller.privateTabCount.toString() + " tab") {
                        showMenu = false; controller.closeAllPrivateTabs()
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                BrowserMenuListAction(BrowserGlyphType.SETTINGS, "Cài đặt") {
                    showMenu = false; onOpenSettings()
                }
                if (!tab.isPrivate) {
                    BrowserMenuListAction(
                        BrowserGlyphType.BOOKMARK,
                        if (controller.isBookmarked(tab.url)) "Bỏ trang khỏi dấu trang" else "Thêm vào dấu trang"
                    ) {
                        showMenu = false; controller.toggleBookmark()
                    }
                }
            }
        }
    }

    if (promptChannel != null && promptState != null) {
        AlertDialog(
            onDismissRequest = {
                updateManager.dismissUpdatePrompt(promptChannel)
                promptRefresh++
            },
            title = { Text("Có bản cập nhật " + if (promptChannel == UpdateChannel.BETA) "Beta" else "Stable") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phiên bản " + promptState.version, fontWeight = FontWeight.SemiBold)
                    if (promptState.releaseNotes.isNotBlank()) {
                        Text(promptState.releaseNotes, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                    if (promptState.isDownloading) {
                        LinearProgressIndicator(progress = { promptState.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Đang tải " + promptState.progress + "%", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(promptState.message, color = if (promptState.error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !promptState.isChecking && !promptState.isDownloading,
                    onClick = {
                        when {
                            promptState.downloaded -> updateManager.installDownloadedApk(promptChannel)
                            promptState.updateAvailable -> updateManager.downloadUpdate(promptChannel)
                            else -> updateManager.checkForUpdates(promptChannel)
                        }
                    }
                ) {
                    Text(when {
                        promptState.downloaded -> "Cài đặt ngay"
                        promptState.isDownloading -> "Đang tải…"
                        promptState.updateAvailable -> "Tải cập nhật"
                        else -> "Thử lại"
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    updateManager.dismissUpdatePrompt(promptChannel)
                    promptRefresh++
                }) { Text("Để sau") }
            }
        )
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

    controller.sitePermissionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { controller.resolveSitePermission(false) },
            title = { Text("Quyền website") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        prompt.origin + " muốn sử dụng: " + prompt.permissionLabel + ".",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (prompt.isPrivate) {
                        Text(
                            "Yêu cầu đến từ tab ẩn danh. Lựa chọn này không được lưu thành quyền lâu dài.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Cat Browser sẽ ghi nhớ lựa chọn cho website này. Bạn có thể quản lý quyền đã lưu trong Cài đặt.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (prompt.isIrreversible) {
                        Text(
                            "Lưu ý: quyền lưu trữ lâu dài có giới hạn thu hồi theo GeckoView.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.resolveSitePermission(true) }) {
                    Text("Cho phép")
                }
            },
            dismissButton = {
                TextButton(onClick = { controller.resolveSitePermission(false) }) {
                    Text("Chặn")
                }
            }
        )
    }

    controller.extensionsManager.popup?.let { popupState ->
        Dialog(
            onDismissRequest = {
                controller.extensionsManager.closePopup(popupState.session)
            }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).heightIn(min = 300.dp, max = 560.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            popupState.extensionName,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                controller.extensionsManager.closePopup(popupState.session)
                            }
                        ) {
                            BrowserGlyph(BrowserGlyphType.CLOSE)
                        }
                    }
                    AndroidView(
                        factory = { context ->
                            GeckoView(context).apply {
                                setSession(popupState.session)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        update = { view ->
                            if (view.getSession() !== popupState.session && popupState.session.isOpen) {
                                view.releaseSession()
                                view.setSession(popupState.session)
                            }
                        },
                        onRelease = { view ->
                            view.releaseSession()
                        }
                    )
                }
            }
        }
    }

    controller.mediaPermissionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { controller.resolveMediaPermission(false) },
            title = { Text("Truy cập camera / microphone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(prompt.origin + " yêu cầu:")
                    if (prompt.hasCamera) Text("• Camera")
                    if (prompt.hasMicrophone) Text("• Microphone")
                    if (prompt.isPrivate) {
                        Text(
                            "Yêu cầu đến từ tab ẩn danh.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Android có thể hiển thị thêm hộp thoại quyền thiết bị.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.resolveMediaPermission(true) }) {
                    Text("Cho phép")
                }
            },
            dismissButton = {
                TextButton(onClick = { controller.resolveMediaPermission(false) }) {
                    Text("Từ chối")
                }
            }
        )
    }
}
