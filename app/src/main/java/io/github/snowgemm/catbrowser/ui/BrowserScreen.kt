package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.BrowserEntry
import org.mozilla.geckoview.GeckoView

private val CatColorScheme = darkColorScheme(
    primary = Color(0xFF78DDB0),
    onPrimary = Color(0xFF09271B),
    secondary = Color(0xFFB6D9C7),
    onSecondary = Color(0xFF14251D),
    background = Color(0xFF0B121A),
    onBackground = Color(0xFFEAF2F5),
    surface = Color(0xFF131F2A),
    onSurface = Color(0xFFEAF2F5),
    surfaceVariant = Color(0xFF202E3B),
    onSurfaceVariant = Color(0xFFAABBC7),
    outline = Color(0xFF3A4B59)
)

@Composable
fun CatBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CatColorScheme, content = content)
}

@Composable
fun BrowserScreen(controller: BrowserController) {
    var showExtensions by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showExtensions) {
        ExtensionsScreen(controller.extensionsManager) { showExtensions = false }
        return
    }
    if (showTabs) {
        TabsOverview(controller, { showTabs = false }, {
            controller.newTab()
            showTabs = false
        })
        return
    }

    val tab = controller.activeTab ?: return
    var address by remember(tab.id) { mutableStateOf(tab.url) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(tab.id, tab.url) { address = tab.url }

    fun navigate() {
        if (address.isNotBlank()) {
            controller.navigate(address)
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = tab.canGoBack) { controller.goBack() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("CAT BROWSER", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.25.sp)
                Text(tab.title.ifBlank { "Tab mới" }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                onClick = { showTabs = true }, shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.semantics { contentDescription = "Danh sách tab" }
            ) {
                Text(controller.tabs.size.toString(), Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(9.dp))
            Surface(onClick = { controller.newTab() }, shape = CircleShape,
                color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Tab mới" }) {
                Box(contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp) }
            }
            Box {
                TextButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Text("⋮", fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Tiện ích mở rộng") },
                        onClick = { showMenu = false; showExtensions = true })
                    DropdownMenuItem(text = { Text("Dấu trang (" + controller.bookmarks.size + ")") },
                        onClick = { showMenu = false; showBookmarks = true })
                    DropdownMenuItem(text = { Text("Lịch sử") },
                        onClick = { showMenu = false; showHistory = true })
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(54.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(17.dp)),
                shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tab.url.startsWith("https://")) "⌑" else "◎",
                        color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = address, onValueChange = { address = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() }),
                        decorationBox = { inner ->
                            Box {
                                if (address.isEmpty()) Text("Tìm kiếm hoặc nhập địa chỉ",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                inner()
                            }
                        }
                    )
                    if (tab.isLoading) {
                        Text(tab.progress.toString() + "%", color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        TextButton(onClick = { address = tab.url }) {
                            Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { navigate() }, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("→", fontSize = 23.sp) }
            }
        }

        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (tab.isLoading) Box(
                Modifier.fillMaxWidth((tab.progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.035f))
                    .fillMaxHeight().background(MaterialTheme.colorScheme.primary)
            )
        }

        tab.error?.let { message ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 15.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                TextButton(onClick = { controller.stopOrReload() }) { Text("Thử lại") }
            }
        }

        Surface(Modifier.fillMaxWidth().weight(1f), color = Color(0xFF080D13)) {
            val session = tab.session
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Đang khởi tạo trang…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                key(tab.id) {
                    AndroidView(
                        factory = { context -> GeckoView(context).apply {
                            setSession(session)
                            controller.onSessionViewAttached(tab.id, session)
                        } },
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
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserAction("Quay lại", "‹", tab.canGoBack) { controller.goBack() }
            BrowserAction("Tiếp", "›", tab.canGoForward) { controller.goForward() }
            BrowserAction("Trang chủ", "⌂", true) { controller.goHome() }
            BrowserAction(if (tab.isLoading) "Dừng" else "Tải lại",
                if (tab.isLoading) "×" else "↻", true) { controller.stopOrReload() }
            BrowserAction("Dấu trang", if (controller.isBookmarked(tab.url)) "★" else "☆", true) {
                controller.toggleBookmark()
            }
            BrowserAction("Tiện ích", "✧", true) { showExtensions = true }
        }
    }

    if (showHistory) EntryDialog("Lịch sử", controller.history, "Chưa có lịch sử duyệt web.",
        { showHistory = false }, { controller.openEntry(it.url); showHistory = false },
        { controller.clearHistory() })
    if (showBookmarks) EntryDialog("Dấu trang", controller.bookmarks, "Bạn chưa lưu trang nào.",
        { showBookmarks = false }, { controller.openEntry(it.url); showBookmarks = false })
}

@Composable
private fun BrowserAction(label: String, symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.alpha(if (enabled) 1f else 0.38f).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, lineHeight = 24.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun TabsOverview(controller: BrowserController, onBack: () -> Unit, onNewTab: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Các tab", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold)
                Text(controller.tabs.size.toString() + " tab đang mở",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Đóng") }
            Button(onClick = onNewTab, shape = RoundedCornerShape(13.dp)) { Text("+ Tab mới") }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            items(controller.tabs, key = { it.id }) { tab ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (tab.id == controller.selectedTabId) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().border(
                        if (tab.id == controller.selectedTabId) 1.dp else 0.dp,
                        if (tab.id == controller.selectedTabId) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(18.dp))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("◉", Modifier.padding(11.dp), color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).clickable {
                            controller.selectTab(tab.id)
                            onBack()
                        }.padding(horizontal = 12.dp)) {
                            Text(tab.title.ifBlank { "Tab mới" }, color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tab.url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { controller.closeTab(tab.id) }) {
                            Text("×", fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
    onClear: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (entries.isEmpty()) Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(entries, key = { it.url }) { entry ->
                    TextButton(onClick = { onSelect(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.url, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onClear != null) TextButton(onClick = onClear) { Text("Xoá lịch sử") }
            else TextButton(onClick = onDismiss) { Text("Xong") }
        },
        dismissButton = {
            if (onClear != null) TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}
