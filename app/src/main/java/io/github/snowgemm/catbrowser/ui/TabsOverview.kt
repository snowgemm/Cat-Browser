package io.github.snowgemm.catbrowser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.BrowserEntry


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
internal fun TabsOverview(
    controller: BrowserController,
    onBack: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit
) {
    var tabSearch by remember { mutableStateOf("") }
    var tabFilter by remember { mutableStateOf("all") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val cardCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var lastReorderTargetId by remember { mutableStateOf<String?>(null) }
    val canReorderTabs = tabSearch.isBlank() && tabFilter == "all"
    val visibleTabs by remember(controller) {
        derivedStateOf {
            controller.tabs.filter { tab ->
                val matchesFilter = when (tabFilter) {
                    "regular" -> !tab.isPrivate
                    "private" -> tab.isPrivate
                    else -> true
                }
                matchesFilter && (
                    tabSearch.isBlank() || tab.title.contains(tabSearch, ignoreCase = true) ||
                        tab.url.contains(tabSearch, ignoreCase = true)
                )
            }
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            TabModeAction(
                icon = BrowserGlyphType.TABS,
                label = "Tất cả",
                count = controller.tabs.size,
                selected = tabFilter == "all",
                modifier = Modifier.weight(1f)
            ) { tabFilter = "all" }
            TabModeAction(
                icon = BrowserGlyphType.INCOGNITO,
                label = "Riêng tư",
                count = controller.privateTabCount,
                selected = tabFilter == "private",
                modifier = Modifier.weight(1f)
            ) { tabFilter = "private" }
            TabModeAction(
                icon = BrowserGlyphType.TABS,
                label = "Thường",
                count = controller.regularTabCount,
                selected = tabFilter == "regular",
                modifier = Modifier.weight(1f)
            ) { tabFilter = "regular" }
        }

        if (visibleTabs.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrowserGlyph(
                    if (tabFilter == "private") BrowserGlyphType.INCOGNITO else BrowserGlyphType.TABS,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    iconSize = 42.dp
                )
                Text(
                    if (tabSearch.isBlank()) when (tabFilter) {
                        "private" -> "Chưa có tab riêng tư nào"
                        "regular" -> "Chưa có tab thường nào"
                        else -> "Chưa có tab nào"
                    } else "Không tìm thấy tab phù hợp",
                    modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                if (tabSearch.isBlank() && tabFilter != "all") {
                    Text(
                        if (tabFilter == "private") "Tab riêng tư sẽ xuất hiện ở đây."
                        else "Mở trang mới để bắt đầu duyệt web.",
                        modifier = Modifier.padding(top = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (visibleTabs.isNotEmpty()) LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 154.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(
                visibleTabs,
                key = { it.id },
                contentType = { "tab-card" }
            ) { tab ->
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
                val targetBorderColor = when {
                    selected -> MaterialTheme.colorScheme.primary
                    tab.isPrivate -> Color(0xFF776B86)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                }
                val borderColor by animateColorAsState(
                    targetValue = targetBorderColor,
                    animationSpec = tween(180),
                    label = "tab-card-border"
                )
                val borderWidth by animateDpAsState(
                    targetValue = if (selected) 1.6.dp else 1.dp,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                    label = "tab-card-border-width"
                )
                val cardScale by animateFloatAsState(
                    targetValue = if (draggingTabId == tab.id) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 460f),
                    label = "tab-card-drag-scale"
                )
                val cardElevation by animateDpAsState(
                    targetValue = if (selected) 4.dp else 1.dp,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
                    label = "tab-card-elevation"
                )

                Surface(
                    onClick = {
                        controller.selectTab(tab.id)
                        onBack()
                    },
                    shape = RoundedCornerShape(17.dp),
                    color = cardBackground,
                    tonalElevation = cardElevation,
                    border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
                    modifier = Modifier.fillMaxWidth()
                        .height(210.dp)
                        .animateContentSize()
                        .animateItem()
                        .graphicsLayer {
                            alpha = if (draggingTabId == tab.id) 0.84f else 1f
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .onGloballyPositioned { cardCoordinates[tab.id] = it }
                        .pointerInput(tab.id, canReorderTabs) {
                            if (canReorderTabs) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingTabId = tab.id
                                        lastReorderTargetId = null
                                    },
                                    onDragEnd = {
                                        draggingTabId = null
                                        lastReorderTargetId = null
                                    },
                                    onDragCancel = {
                                        draggingTabId = null
                                        lastReorderTargetId = null
                                    },
                                    onDrag = { change, _ ->
                                        val position = cardCoordinates[tab.id]
                                            ?.localToRoot(change.position)
                                            ?: return@detectDragGesturesAfterLongPress
                                        val targetId = visibleTabs.firstOrNull { candidate ->
                                            cardCoordinates[candidate.id]
                                                ?.boundsInRoot()
                                                ?.contains(position) == true
                                        }?.id
                                        if (targetId == null || targetId == tab.id) {
                                            lastReorderTargetId = null
                                        } else if (targetId != lastReorderTargetId) {
                                            if (controller.moveTab(tab.id, targetId)) {
                                                lastReorderTargetId = targetId
                                            }
                                        }
                                        change.consume()
                                    }
                                )
                            }
                        }
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(30.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (tab.isPrivate) Color(0xFF443B50)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(25.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        mark,
                                        color = if (tab.isPrivate) Color(0xFFE6DDF0)
                                            else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                tab.title.ifBlank { domain },
                                Modifier.weight(1f),
                                color = cardText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { controller.closeTab(tab.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                BrowserGlyph(
                                    BrowserGlyphType.CLOSE,
                                    iconSize = 16.dp,
                                    tint = cardMuted
                                )
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth().weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                        ) {
                            tab.previewBitmap?.let { preview ->
                                Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = "Ảnh xem trước: " + tab.title,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } ?: Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                BrowserGlyph(
                                    BrowserGlyphType.GLOBE,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    iconSize = 30.dp
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().height(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tab.isPrivate) {
                                BrowserGlyph(
                                    BrowserGlyphType.INCOGNITO,
                                    tint = Color(0xFFE6DDF0),
                                    iconSize = 13.dp
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Riêng tư",
                                    color = Color(0xFFE6DDF0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (selected) {
                                Text(
                                    "Đang mở",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
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
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tabSearch,
                    onValueChange = { tabSearch = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Tìm thẻ của bạn", maxLines = 1) },
                    leadingIcon = { BrowserGlyph(BrowserGlyphType.SEARCH, iconSize = 18.dp) },
                    trailingIcon = {
                        if (tabSearch.isNotEmpty()) {
                            IconButton(onClick = { tabSearch = "" }) {
                                BrowserGlyph(BrowserGlyphType.CLEAR, iconSize = 16.dp)
                            }
                        }
                    }
                )
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        BrowserGlyph(BrowserGlyphType.MORE, iconSize = 21.dp)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Đóng tab đang mở") },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.CLOSE, iconSize = 18.dp) },
                            onClick = {
                                showMoreMenu = false
                                controller.closeTab(controller.selectedTabId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Đóng tất cả tab riêng tư") },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.INCOGNITO, iconSize = 18.dp) },
                            enabled = controller.privateTabCount > 0,
                            onClick = {
                                showMoreMenu = false
                                controller.closeAllPrivateTabs()
                            }
                        )
                    }
                }
                Box {
                    FloatingActionButton(
                        onClick = { showAddMenu = true },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        BrowserGlyph(
                            BrowserGlyphType.NEW_TAB,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            iconSize = 23.dp
                        )
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tab thường") },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.NEW_TAB, iconSize = 18.dp) },
                            enabled = controller.canCreateTab,
                            onClick = {
                                showAddMenu = false
                                onNewTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tab riêng tư") },
                            leadingIcon = { BrowserGlyph(BrowserGlyphType.INCOGNITO, iconSize = 18.dp) },
                            enabled = controller.canCreateTab,
                            onClick = {
                                showAddMenu = false
                                onNewPrivateTab()
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TabModeAction(
    icon: BrowserGlyphType,
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BrowserGlyph(
                icon,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(count.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun EntryDialog(
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
