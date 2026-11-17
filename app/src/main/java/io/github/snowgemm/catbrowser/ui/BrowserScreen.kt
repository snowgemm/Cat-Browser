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
    primary = Color(0xFF55C98A),
    onPrimary = Color(0xFF082417),
    secondary = Color(0xFFE6B35A),
    onSecondary = Color(0xFF241A08),
    background = Color(0xFF101612),
    onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF171F1A),
    onSurface = Color(0xFFE8EEE9),
    surfaceVariant = Color(0xFF222C26),
    onSurfaceVariant = Color(0xFFB2BDB5),
    outline = Color(0xFF39483E)
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
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DockAction("‹", "Quay lại", tab.canGoBack) { controller.goBack() }
            DockAction("›", "Tiến", tab.canGoForward) { controller.goForward() }
            Surface(
                modifier = Modifier.weight(1f).height(46.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(start = 10.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tab.url.startsWith("https://")) "◈" else "⌕",
                        color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                    Spacer(Modifier.width(7.dp))
                    BasicTextField(
                        value = address, onValueChange = { address = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() }),
                        decorationBox = { inner ->
                            Box {
                                if (address.isEmpty()) Text("Tìm kiếm hoặc nhập địa chỉ",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                                inner()
                            }
                        }
                    )
                    if (tab.isLoading) {
                        Text("${tab.progress}%", color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        TextButton(onClick = { navigate() }, contentPadding = PaddingValues(5.dp)) {
                            Text("→", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp)
                        }
                    }
                }
            }
            DockAction(controller.tabs.size.toString(), "Các tab", true) { showTabs = true }
            Box {
                DockAction("⋮", "Menu", true) { showMenu = true }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Trang chủ") },
                        onClick = { showMenu = false; controller.goHome() })
                    DropdownMenuItem(text = { Text(if (tab.isLoading) "Dừng tải" else "Tải lại") },
                        onClick = { showMenu = false; controller.stopOrReload() })
                    DropdownMenuItem(text = { Text(if (controller.isBookmarked(tab.url)) "Bỏ dấu trang" else "Thêm dấu trang") },
                        onClick = { showMenu = false; controller.toggleBookmark() })
                    DropdownMenuItem(text = { Text("Tiện ích mở rộng") },
                        onClick = { showMenu = false; showExtensions = true })
                    DropdownMenuItem(text = { Text("Dấu trang (${controller.bookmarks.size})") },
                        onClick = { showMenu = false; showBookmarks = true })
                    DropdownMenuItem(text = { Text("Lịch sử") },
                        onClick = { showMenu = false; showHistory = true })
                    DropdownMenuItem(text = { Text("Tab mới") },
                        onClick = { showMenu = false; controller.newTab() })
                }
            }
        }
    }

    if (showHistory) EntryDialog("Lịch sử", controller.history, "Chưa có lịch sử duyệt web.",
        { showHistory = false }, { controller.openEntry(it.url); showHistory = false },
        { controller.clearHistory() })
    if (showBookmarks) EntryDialog("Dấu trang", controller.bookmarks, "Bạn chưa lưu trang nào.",
        { showBookmarks = false }, { controller.openEntry(it.url); showBookmarks = false })
}

@Composable
private fun DockAction(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.widthIn(min = 32.dp).height(42.dp)
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
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
