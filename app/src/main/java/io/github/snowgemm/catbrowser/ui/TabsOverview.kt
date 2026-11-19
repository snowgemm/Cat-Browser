package io.github.snowgemm.catbrowser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

        OutlinedTextField(
            value = tabSearch,
            onValueChange = { tabSearch = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            singleLine = true,
            placeholder = { Text("Tìm trong các tab") },
            leadingIcon = { BrowserGlyph(BrowserGlyphType.SEARCH) }
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(
                selected = tabFilter == "all",
                onClick = { tabFilter = "all" },
                label = { Text("Tất cả " + controller.tabs.size) }
            )
            FilterChip(
                selected = tabFilter == "regular",
                onClick = { tabFilter = "regular" },
                label = { Text("Thường " + controller.regularTabCount) }
            )
            FilterChip(
                selected = tabFilter == "private",
                onClick = { tabFilter = "private" },
                label = { Text("Ẩn danh " + controller.privateTabCount) }
            )
        }
        if (canReorderTabs && visibleTabs.isNotEmpty()) {
            Text(
                "Nhấn giữ thẻ để kéo sắp xếp tab.",
                Modifier.padding(start = 18.dp, top = 2.dp, bottom = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (visibleTabs.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (tabSearch.isBlank()) "Không có tab trong nhóm này."
                    else "Không tìm thấy tab phù hợp.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
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
                    shape = RoundedCornerShape(19.dp),
                    color = cardBackground,
                    tonalElevation = cardElevation,
                    border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
                    modifier = Modifier.fillMaxWidth()
                        .height(if (tab.previewBitmap != null) 254.dp else 164.dp)
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
                        Modifier.fillMaxSize().padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

                        tab.previewBitmap?.let { preview ->
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Ảnh xem trước: " + tab.title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(11.dp))
                            )
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
