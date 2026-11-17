package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    primary = Color(0xFF72D7A2),
    onPrimary = Color(0xFF062417),
    secondary = Color(0xFFB6D9C2),
    onSecondary = Color(0xFF14251B),
    background = Color(0xFF0D1722),
    onBackground = Color(0xFFEAF1F5),
    surface = Color(0xFF16232F),
    onSurface = Color(0xFFEAF1F5),
    surfaceVariant = Color(0xFF253442),
    onSurfaceVariant = Color(0xFFB8C6D1),
    outline = Color(0xFF536575)
)

@Composable
fun CatBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CatColorScheme, content = content)
}

@Composable
fun BrowserScreen(controller: BrowserController) {
    var showExtensions by remember { mutableStateOf(false) }
    if (showExtensions) {
        ExtensionsScreen(
            manager = controller.extensionsManager,
            onBack = { showExtensions = false }
        )
        return
    }

    val activeTab = controller.activeTab
    if (activeTab == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active tab")
        }
        return
    }

    var addressText by remember(activeTab.id) { mutableStateOf(activeTab.url) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(activeTab.id, activeTab.url) {
        addressText = activeTab.url
    }

    fun submitAddress() {
        if (addressText.isBlank()) return
        controller.navigate(addressText)
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    BackHandler(enabled = activeTab.canGoBack) {
        controller.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "CAT BROWSER",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = activeTab.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showTabsDialog = true }) {
                    Text("▢ " + controller.tabs.size)
                }
                Button(
                    onClick = { controller.newTab() },
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text("+")
                }
                Box {
                    TextButton(onClick = { showOverflowMenu = true }) {
                        Text("⋮", fontSize = 22.sp)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tiện ích mở rộng") },
                            onClick = {
                                showOverflowMenu = false
                                showExtensions = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dấu trang (" + controller.bookmarks.size + ")") },
                            onClick = {
                                showOverflowMenu = false
                                showBookmarksDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lịch sử") },
                            onClick = {
                                showOverflowMenu = false
                                showHistoryDialog = true
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submitAddress() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(
                onClick = { submitAddress() },
                enabled = addressText.isNotBlank(),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text("Go")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (activeTab.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((activeTab.progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (activeTab.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeTab.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                TextButton(onClick = { controller.stopOrReload() }) {
                    Text("Retry")
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surface
        ) {
            val session = activeTab.session
            if (session == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Opening browser…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                key(activeTab.id) {
                    AndroidView(
                        factory = { context ->
                            GeckoView(context).apply {
                                setSession(session)
                                controller.onSessionViewAttached(activeTab.id, session)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (view.getSession() !== session) {
                                view.releaseSession()
                                view.setSession(session)
                                controller.onSessionViewAttached(activeTab.id, session)
                            }
                        },
                        onRelease = { view ->
                            controller.onSessionViewDetached(activeTab.id, session)
                            view.releaseSession()
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationAction("Back", "‹", activeTab.canGoBack) { controller.goBack() }
            NavigationAction("Forward", "›", activeTab.canGoForward) { controller.goForward() }
            NavigationAction("Home", "⌂", true) { controller.goHome() }
            NavigationAction(
                if (activeTab.isLoading) "Stop" else "Reload",
                if (activeTab.isLoading) "×" else "↻",
                true
            ) { controller.stopOrReload() }
            NavigationAction(
                "Bookmark",
                if (controller.isBookmarked(activeTab.url)) "★" else "☆",
                true
            ) { controller.toggleBookmark() }
            NavigationAction("History", "◷", true) { showHistoryDialog = true }
        }
    }

    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Open tabs (" + controller.tabs.size + ")") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(controller.tabs, key = { it.id }) { tab ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    controller.selectTab(tab.id)
                                    showTabsDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = (if (tab.id == controller.selectedTabId) "● " else "") + tab.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(onClick = { controller.closeTab(tab.id) }) {
                                Text("×")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.newTab()
                    showTabsDialog = false
                }) {
                    Text("New tab")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTabsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showHistoryDialog) {
        EntryDialog(
            title = "History",
            entries = controller.history,
            emptyMessage = "No browsing history yet.",
            onDismiss = { showHistoryDialog = false },
            onSelect = { entry ->
                controller.openEntry(entry.url)
                showHistoryDialog = false
            },
            onClear = {
                controller.clearHistory()
            }
        )
    }

    if (showBookmarksDialog) {
        EntryDialog(
            title = "Bookmarks",
            entries = controller.bookmarks,
            emptyMessage = "No bookmarks saved yet.",
            onDismiss = { showBookmarksDialog = false },
            onSelect = { entry ->
                controller.openEntry(entry.url)
                showBookmarksDialog = false
            }
        )
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
            if (entries.isEmpty()) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = { it.url }) { entry ->
                        TextButton(
                            onClick = { onSelect(entry) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    text = entry.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.url,
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
                TextButton(onClick = onClear) { Text("Clear history") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    )
}

@Composable
private fun NavigationAction(
    label: String,
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = symbol,
                fontSize = 21.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Text(text = label, fontSize = 9.sp, maxLines = 1)
        }
    }
}
