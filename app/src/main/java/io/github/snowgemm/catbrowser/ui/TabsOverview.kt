@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.snowgemm.catbrowser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserBookmarkFolder
import io.github.snowgemm.catbrowser.browser.BrowserBookmarkPolicy
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.browser.BrowserEntry
import io.github.snowgemm.catbrowser.browser.BrowserTab
import io.github.snowgemm.catbrowser.browser.BrowserTabGroup
import io.github.snowgemm.catbrowser.browser.BrowserTabGroupPolicy
import androidx.compose.foundation.lazy.grid.items as gridItems

private val CatDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA9C9F7),
        onPrimary = Color(0xFF10243C),
        secondary = Color(0xFFE6C47D),
        onSecondary = Color(0xFF2A210F),
        background = Color(0xFF10161E),
        onBackground = Color(0xFFF1F4F8),
        surface = Color(0xFF1B2633),
        onSurface = Color(0xFFF1F4F8),
        surfaceVariant = Color(0xFF293747),
        onSurfaceVariant = Color(0xFFBAC7D5),
        outline = Color(0xFF405164),
        secondaryContainer = Color(0xFF34465C),
        onSecondaryContainer = Color(0xFFEAF1FB),
    )

private val CatLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF416B9B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE9F8),
        onPrimaryContainer = Color(0xFF173451),
        secondary = Color(0xFF8F713B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1E7D4),
        onSecondaryContainer = Color(0xFF392B13),
        background = Color(0xFFF3F6FA),
        onBackground = Color(0xFF1B2530),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B2530),
        surfaceVariant = Color(0xFFE8EEF5),
        onSurfaceVariant = Color(0xFF526171),
        outline = Color(0xFFD2DCE8),
    )

private val tabGroupColorPalette =
    listOf(
        Color(0xFF5795E8),
        Color(0xFFB17AE8),
        Color(0xFF4FAE87),
        Color(0xFFE1A34B),
        Color(0xFFE17C92),
        Color(0xFF6F87C5),
    )

private fun tabGroupColor(colorIndex: Int): Color = tabGroupColorPalette[BrowserTabGroupPolicy.normalizeColorIndex(colorIndex)]

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun TabsOverview(
    controller: BrowserController,
    onBack: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
) {
    val motionEnabled = isBrowserMotionEnabled()
    var tabSearch by remember { mutableStateOf("") }
    var tabFilter by remember { mutableStateOf("regular") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var isSelectingTabs by remember { mutableStateOf(false) }
    var selectedTabIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showAddToGroupDialog by remember { mutableStateOf(false) }
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }
    var groupColorIndex by remember { mutableIntStateOf(0) }
    val cardCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var lastReorderTargetId by remember { mutableStateOf<String?>(null) }
    val selectedGroup = controller.tabGroups.firstOrNull { it.id == selectedGroupId }
    val isGroupDetail = tabFilter == "groups" && selectedGroupId != null
    val canReorderTabs = tabSearch.isBlank() && tabFilter == "regular" && !isSelectingTabs
    val visibleTabs by remember(controller, tabFilter, selectedGroupId, tabSearch) {
        derivedStateOf {
            controller.tabs.filter { tab ->
                val matchesMode =
                    when (tabFilter) {
                        "regular" -> !tab.isPrivate && tab.groupId == null
                        "private" -> tab.isPrivate
                        "groups" -> selectedGroupId != null && tab.groupId == selectedGroupId
                        else -> false
                    }
                matchesMode &&
                    (
                        tabSearch.isBlank() ||
                            tab.title.contains(tabSearch, ignoreCase = true) ||
                            tab.url.contains(tabSearch, ignoreCase = true)
                    )
            }
        }
    }
    val visibleGroups by remember(controller, tabSearch) {
        derivedStateOf {
            controller.tabGroups.filter { group ->
                tabSearch.isBlank() ||
                    group.name.contains(tabSearch, ignoreCase = true) ||
                    controller.tabs.any { tab ->
                        tab.groupId == group.id &&
                            (
                                tab.title.contains(tabSearch, ignoreCase = true) ||
                                    tab.url.contains(tabSearch, ignoreCase = true)
                            )
                    }
            }
        }
    }

    LaunchedEffect(selectedGroupId, controller.tabGroups.toList()) {
        if (selectedGroupId != null && selectedGroup == null) {
            selectedGroupId = null
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (isGroupDetail && selectedGroup != null) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedGroupId = null }) {
                    BrowserGlyph(BrowserGlyphType.BACK, iconSize = 21.dp)
                }
                Box(
                    Modifier
                        .size(11.dp)
                        .background(tabGroupColor(selectedGroup.colorIndex), CircleShape),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    selectedGroup.name,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { showGroupMenu = true }) {
                        BrowserGlyph(BrowserGlyphType.MORE, iconSize = 21.dp)
                    }
                    DropdownMenu(
                        expanded = showGroupMenu,
                        onDismissRequest = { showGroupMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa nhóm") },
                            onClick = {
                                showGroupMenu = false
                                editingGroupId = selectedGroup.id
                                groupName = selectedGroup.name
                                groupColorIndex = selectedGroup.colorIndex
                                showGroupDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bỏ nhóm") },
                            onClick = {
                                showGroupMenu = false
                                controller.deleteTabGroup(selectedGroup.id)
                                selectedGroupId = null
                                tabFilter = "regular"
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Đóng các tab trong nhóm") },
                            onClick = {
                                showGroupMenu = false
                                controller.closeTabGroup(selectedGroup.id)
                                selectedGroupId = null
                                tabFilter = "regular"
                            },
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                tabModeAction(
                    icon = BrowserGlyphType.TABS,
                    label = "Thường",
                    count = controller.ungroupedTabCount,
                    selected = tabFilter == "regular",
                    modifier = Modifier.weight(1f),
                ) {
                    tabFilter = "regular"
                    selectedGroupId = null
                    isSelectingTabs = false
                    selectedTabIds = emptySet()
                }
                tabModeAction(
                    icon = BrowserGlyphType.INCOGNITO,
                    label = "Riêng tư",
                    count = controller.privateTabCount,
                    selected = tabFilter == "private",
                    modifier = Modifier.weight(1f),
                ) {
                    tabFilter = "private"
                    selectedGroupId = null
                    isSelectingTabs = false
                    selectedTabIds = emptySet()
                }
                tabModeAction(
                    icon = BrowserGlyphType.TABS,
                    label = "Nhóm",
                    count = controller.groupedTabCount,
                    selected = tabFilter == "groups",
                    modifier = Modifier.weight(1f),
                ) {
                    tabFilter = "groups"
                    selectedGroupId = null
                    isSelectingTabs = false
                    selectedTabIds = emptySet()
                }
            }
        }

        if (tabFilter == "groups" && selectedGroupId == null) {
            if (visibleGroups.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrowserGlyph(
                        BrowserGlyphType.TABS,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        iconSize = 42.dp,
                    )
                    Text(
                        if (tabSearch.isBlank()) {
                            "Chưa có nhóm tab"
                        } else {
                            "Không tìm thấy nhóm phù hợp"
                        },
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (tabSearch.isBlank()) {
                        Text(
                            "Chọn ít nhất hai tab thường rồi tạo nhóm từ menu.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleGroups, key = { it.id }) { group ->
                        tabGroupCard(
                            group = group,
                            tabs = controller.tabs.filter { it.groupId == group.id },
                            onOpen = { selectedGroupId = group.id },
                            onEdit = {
                                editingGroupId = group.id
                                groupName = group.name
                                groupColorIndex = group.colorIndex
                                showGroupDialog = true
                            },
                            onDelete = { controller.deleteTabGroup(group.id) },
                            onClose = { controller.closeTabGroup(group.id) },
                        )
                    }
                }
            }
        }

        if (tabFilter != "groups" || selectedGroupId != null) {
            if (visibleTabs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrowserGlyph(
                        if (tabFilter == "private") BrowserGlyphType.INCOGNITO else BrowserGlyphType.TABS,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        iconSize = 42.dp,
                    )
                    Text(
                        if (tabSearch.isBlank()) {
                            when (tabFilter) {
                                "private" -> "Chưa có tab riêng tư nào"
                                "groups" -> "Nhóm này chưa có tab"
                                else -> "Chưa có tab thường chưa nhóm"
                            }
                        } else {
                            "Không tìm thấy tab phù hợp"
                        },
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (tabSearch.isBlank() && tabFilter == "private") {
                        Text(
                            "Tab riêng tư sẽ xuất hiện ở đây.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (visibleTabs.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 154.dp),
                modifier = Modifier.weight(1f),
                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 4.dp,
                        bottom = 18.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(
                    visibleTabs,
                    key = { it.id },
                    contentType = { "tab-card" },
                ) { tab ->
                    val domain =
                        tab.url
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .substringBefore('/')
                            .ifBlank { "Trang mới" }
                    val mark = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                    val active = tab.id == controller.selectedTabId
                    val selected =
                        if (isSelectingTabs) {
                            tab.id in selectedTabIds
                        } else {
                            active
                        }
                    val cardBackground =
                        if (tab.isPrivate) {
                            Color(0xFF292431)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    val cardText =
                        if (tab.isPrivate) {
                            Color(0xFFF2EDF7)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    val cardMuted =
                        if (tab.isPrivate) {
                            Color(0xFFC9C0D2)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    val targetBorderColor =
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            tab.isPrivate -> Color(0xFF776B86)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        }
                    val borderColor by animateColorAsState(
                        targetValue = targetBorderColor,
                        animationSpec = tween(BrowserMotionPolicy.durationMillis(motionEnabled, 180)),
                        label = "tab-card-border",
                    )
                    val borderWidth by animateDpAsState(
                        targetValue = if (selected) 1.6.dp else 1.dp,
                        animationSpec = if (motionEnabled) spring(dampingRatio = 0.9f, stiffness = 520f) else snap(),
                        label = "tab-card-border-width",
                    )
                    val cardScale by animateFloatAsState(
                        targetValue = if (draggingTabId == tab.id) 0.96f else 1f,
                        animationSpec = if (motionEnabled) spring(dampingRatio = 0.82f, stiffness = 460f) else snap(),
                        label = "tab-card-drag-scale",
                    )
                    val cardElevation by animateDpAsState(
                        targetValue = if (selected) 4.dp else 1.dp,
                        animationSpec = if (motionEnabled) spring(dampingRatio = 0.9f, stiffness = 500f) else snap(),
                        label = "tab-card-elevation",
                    )

                    Surface(
                        onClick = {
                            if (isSelectingTabs) {
                                selectedTabIds =
                                    if (tab.id in selectedTabIds) {
                                        selectedTabIds - tab.id
                                    } else {
                                        selectedTabIds + tab.id
                                    }
                            } else {
                                controller.selectTab(tab.id)
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(17.dp),
                        color = cardBackground,
                        tonalElevation = cardElevation,
                        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .animateContentSize(
                                    animationSpec = tween(BrowserMotionPolicy.durationMillis(motionEnabled, 180)),
                                )
                                .animateItem(
                                    fadeInSpec = if (motionEnabled) tween(120) else null,
                                    placementSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else null,
                                    fadeOutSpec = if (motionEnabled) tween(100) else null,
                                )
                                .graphicsLayer {
                                    alpha = if (draggingTabId == tab.id) 0.84f else 1f
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }.onGloballyPositioned { cardCoordinates[tab.id] = it }
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
                                                val position =
                                                    cardCoordinates[tab.id]
                                                        ?.localToRoot(change.position)
                                                        ?: return@detectDragGesturesAfterLongPress
                                                val targetId =
                                                    visibleTabs
                                                        .firstOrNull { candidate ->
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
                                            },
                                        )
                                    }
                                },
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color =
                                        if (tab.isPrivate) {
                                            Color(0xFF443B50)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    modifier = Modifier.size(25.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = mark,
                                            color =
                                                if (tab.isPrivate) {
                                                    Color(0xFFE6DDF0)
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = tab.title.ifBlank { domain },
                                    modifier = Modifier.weight(1f),
                                    color = cardText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { controller.closeTab(tab.id) },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    BrowserGlyph(
                                        BrowserGlyphType.CLOSE,
                                        iconSize = 16.dp,
                                        tint = cardMuted,
                                    )
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp)),
                            ) {
                                tab.previewBitmap?.let { preview ->
                                    Image(
                                        bitmap = preview.asImageBitmap(),
                                        contentDescription = "Ảnh xem trước: ${tab.title}",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } ?: Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BrowserGlyph(
                                        BrowserGlyphType.GLOBE,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        iconSize = 30.dp,
                                    )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (tab.isPrivate) {
                                    BrowserGlyph(
                                        BrowserGlyphType.INCOGNITO,
                                        tint = Color(0xFFE6DDF0),
                                        iconSize = 13.dp,
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Riêng tư",
                                        color = Color(0xFFE6DDF0),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                when {
                                    isSelectingTabs && selected ->
                                        Text(
                                            text = "Đã chọn",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    isSelectingTabs ->
                                        Text(
                                            text = "Chạm để chọn",
                                            color = cardMuted,
                                            fontSize = 10.sp,
                                        )
                                    active ->
                                        Text(
                                            text = "Đang mở",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isSelectingTabs) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selectedTabIds.size} tab đã chọn",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                isSelectingTabs = false
                                selectedTabIds = emptySet()
                            },
                        ) {
                            Text("Hủy")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editingGroupId = null
                                groupName = ""
                                groupColorIndex = 0
                                showGroupDialog = true
                            },
                            enabled =
                                selectedTabIds.size >= 2 &&
                                    controller.groupedTabCount < BrowserTabGroupPolicy.MAX_GROUPS,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Tạo nhóm")
                        }
                        OutlinedButton(
                            onClick = { showAddToGroupDialog = true },
                            enabled = selectedTabIds.isNotEmpty() && controller.tabGroups.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Thêm vào nhóm")
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = tabSearch,
                        onValueChange = { tabSearch = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Tìm thẻ hoặc nhóm", maxLines = 1) },
                        leadingIcon = {
                            BrowserGlyph(BrowserGlyphType.SEARCH, iconSize = 18.dp)
                        },
                        trailingIcon = {
                            if (tabSearch.isNotEmpty()) {
                                IconButton(onClick = { tabSearch = "" }) {
                                    BrowserGlyph(BrowserGlyphType.CLEAR, iconSize = 16.dp)
                                }
                            }
                        },
                    )
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            BrowserGlyph(BrowserGlyphType.MORE, iconSize = 21.dp)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chọn tab") },
                                leadingIcon = {
                                    BrowserGlyph(BrowserGlyphType.TABS, iconSize = 18.dp)
                                },
                                enabled =
                                    visibleTabs.isNotEmpty() &&
                                        tabFilter != "private",
                                onClick = {
                                    showMoreMenu = false
                                    selectedTabIds = emptySet()
                                    isSelectingTabs = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Khôi phục tab đã đóng") },
                                enabled =
                                    controller.recentlyClosedTabs.isNotEmpty() &&
                                        controller.canCreateTab,
                                onClick = {
                                    showMoreMenu = false
                                    if (controller.restoreRecentlyClosedTab()) onBack()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Đóng tab đang mở") },
                                leadingIcon = {
                                    BrowserGlyph(BrowserGlyphType.CLOSE, iconSize = 18.dp)
                                },
                                enabled = controller.tabs.size > 1,
                                onClick = {
                                    showMoreMenu = false
                                    controller.closeTab(controller.selectedTabId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Đóng tất cả tab riêng tư") },
                                leadingIcon = {
                                    BrowserGlyph(BrowserGlyphType.INCOGNITO, iconSize = 18.dp)
                                },
                                enabled = controller.privateTabCount > 0,
                                onClick = {
                                    showMoreMenu = false
                                    controller.closeAllPrivateTabs()
                                },
                            )
                        }
                    }
                    Box {
                        FloatingActionButton(
                            onClick = { showAddMenu = true },
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(17.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            BrowserGlyph(
                                BrowserGlyphType.NEW_TAB,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                iconSize = 23.dp,
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isGroupDetail) "Tab mới trong nhóm" else "Tab thường",
                                    )
                                },
                                leadingIcon = {
                                    BrowserGlyph(BrowserGlyphType.NEW_TAB, iconSize = 18.dp)
                                },
                                enabled = controller.canCreateTab,
                                onClick = {
                                    showAddMenu = false
                                    if (isGroupDetail && selectedGroupId != null) {
                                        controller.newTab(selectedGroupId)
                                        onBack()
                                    } else {
                                        onNewTab()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Tab riêng tư") },
                                leadingIcon = {
                                    BrowserGlyph(BrowserGlyphType.INCOGNITO, iconSize = 18.dp)
                                },
                                enabled = controller.canCreateTab,
                                onClick = {
                                    showAddMenu = false
                                    onNewPrivateTab()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showGroupDialog) {
            val editingGroup = controller.tabGroups.firstOrNull { it.id == editingGroupId }
            AlertDialog(
                onDismissRequest = { showGroupDialog = false },
                title = { Text(if (editingGroupId == null) "Tạo nhóm tab" else "Chỉnh sửa nhóm") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = {
                                groupName = it.take(BrowserTabGroupPolicy.MAX_NAME_LENGTH)
                            },
                            singleLine = true,
                            label = { Text("Tên nhóm") },
                            placeholder = { Text("Ví dụ: Học tập") },
                        )
                        Text(
                            "Màu nhóm",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            tabGroupColorPalette.forEachIndexed { index, color ->
                                Surface(
                                    onClick = { groupColorIndex = index },
                                    shape = CircleShape,
                                    color = color,
                                    border =
                                        if (groupColorIndex == index) {
                                            androidx.compose.foundation.BorderStroke(
                                                3.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                            )
                                        } else {
                                            null
                                        },
                                    modifier = Modifier.size(30.dp),
                                ) {}
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled =
                            groupName.isNotBlank() &&
                                (
                                    editingGroupId != null ||
                                        (
                                            selectedTabIds.size >= 2 &&
                                                controller.groupedTabCount < BrowserTabGroupPolicy.MAX_GROUPS
                                        )
                                ),
                        onClick = {
                            val saved =
                                if (editingGroupId == null) {
                                    controller.createTabGroup(
                                        tabIds = selectedTabIds,
                                        name = groupName,
                                        colorIndex = groupColorIndex,
                                    )
                                } else {
                                    val updated =
                                        controller.updateTabGroup(
                                            groupId = editingGroupId.orEmpty(),
                                            name = groupName,
                                            colorIndex = groupColorIndex,
                                        )
                                    if (updated) editingGroup else null
                                }
                            if (saved != null) {
                                tabFilter = "groups"
                                selectedGroupId = saved.id
                                isSelectingTabs = false
                                selectedTabIds = emptySet()
                                showGroupDialog = false
                            }
                        },
                    ) {
                        Text(if (editingGroupId == null) "Tạo" else "Lưu")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGroupDialog = false }) {
                        Text("Hủy")
                    }
                },
            )
        }

        if (showAddToGroupDialog) {
            AlertDialog(
                onDismissRequest = { showAddToGroupDialog = false },
                title = { Text("Thêm tab vào nhóm") },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(controller.tabGroups, key = { it.id }) { group ->
                            TextButton(
                                onClick = {
                                    selectedTabIds.forEach { tabId ->
                                        controller.assignTabToGroup(tabId, group.id)
                                    }
                                    selectedTabIds = emptySet()
                                    isSelectingTabs = false
                                    showAddToGroupDialog = false
                                    tabFilter = "groups"
                                    selectedGroupId = group.id
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(tabGroupColor(group.colorIndex), CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(group.name, maxLines = 1)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddToGroupDialog = false }) {
                        Text("Đóng")
                    }
                },
            )
        }
    }
}

@Composable
private fun tabGroupCard(
    group: BrowserTabGroup,
    tabs: List<BrowserTab>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val groupColor = tabGroupColor(group.colorIndex)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                groupColor.copy(alpha = 0.85f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(groupColor, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    group.name,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        BrowserGlyph(BrowserGlyphType.MORE, iconSize = 18.dp)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa nhóm") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bỏ nhóm") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Đóng các tab trong nhóm") },
                            onClick = {
                                showMenu = false
                                onClose()
                            },
                        )
                    }
                }
            }
            val previews = tabs.mapNotNull { it.previewBitmap }.take(3)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (previews.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        BrowserGlyph(
                            BrowserGlyphType.TABS,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconSize = 24.dp,
                        )
                    }
                } else {
                    previews.forEach { preview ->
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Xem trước nhóm ${group.name}",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(9.dp)),
                        )
                    }
                }
            }
            Text(
                "${tabs.size} tab",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun tabModeAction(
    icon: BrowserGlyphType,
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(13.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BrowserGlyph(
                icon,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = count.toString(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun BookmarkManagerDialog(
    entries: List<BrowserEntry>,
    folders: List<BrowserBookmarkFolder>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (BrowserEntry) -> Unit,
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Boolean,
    onMoveToFolder: (String, String?) -> Boolean,
    onCreateFolder: (String) -> Boolean,
    onRenameFolder: (String, String) -> Boolean,
    onDeleteFolder: (String) -> Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    isTransferBusy: Boolean = false,
    isLoading: Boolean = false,
) {
    var sortAlphabetically by remember { mutableStateOf(false) }
    var transferMenuExpanded by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var showUnfiledOnly by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var movingEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var editedTitle by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }

    LaunchedEffect(folders, selectedFolderId) {
        if (selectedFolderId != null && folders.none { it.id == selectedFolderId }) {
            selectedFolderId = null
        }
    }

    val visibleEntries =
        remember(entries, selectedFolderId, showUnfiledOnly, sortAlphabetically) {
            val filtered =
                when {
                    showUnfiledOnly -> entries.filter { it.folderId == null }
                    selectedFolderId != null -> entries.filter { it.folderId == selectedFolderId }
                    else -> entries
                }
            if (sortAlphabetically) {
                BrowserBookmarkPolicy.sortAlphabetically(filtered)
            } else {
                filtered.sortedByDescending { it.timestamp }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dấu trang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Đang tải dữ liệu…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${visibleEntries.size}/${entries.size} dấu trang",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showFolderManager = true }) {
                                Text("Thư mục")
                            }
                            TextButton(onClick = { sortAlphabetically = !sortAlphabetically }) {
                                Text(if (sortAlphabetically) "Mới nhất" else "Tên A–Z")
                            }
                            Box {
                                TextButton(
                                    onClick = { transferMenuExpanded = true },
                                    enabled = !isTransferBusy,
                                ) {
                                    Text("⋮")
                                }
                                DropdownMenu(
                                    expanded = transferMenuExpanded,
                                    onDismissRequest = { transferMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Nhập từ JSON") },
                                        enabled = !isTransferBusy,
                                        onClick = {
                                            transferMenuExpanded = false
                                            onImport()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Xuất ra JSON") },
                                        enabled = !isTransferBusy,
                                        onClick = {
                                            transferMenuExpanded = false
                                            onExport()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedFolderId == null && !showUnfiledOnly,
                                onClick = {
                                    selectedFolderId = null
                                    showUnfiledOnly = false
                                },
                                label = { Text("Tất cả") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = showUnfiledOnly,
                                onClick = {
                                    selectedFolderId = null
                                    showUnfiledOnly = true
                                },
                                label = { Text("Chưa phân loại") },
                            )
                        }
                        items(folders, key = { it.id }) { folder ->
                            FilterChip(
                                selected = selectedFolderId == folder.id,
                                onClick = {
                                    selectedFolderId = folder.id
                                    showUnfiledOnly = false
                                },
                                label = { Text(folder.name, maxLines = 1) },
                            )
                        }
                    }
                    if (visibleEntries.isEmpty()) {
                        Text(
                            if (entries.isEmpty()) emptyMessage else "Thư mục này chưa có dấu trang.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(visibleEntries, key = { it.url }) { entry ->
                                var menuExpanded by remember(entry.url) { mutableStateOf(false) }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { onSelect(entry) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                entry.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                entry.url,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Box {
                                        TextButton(onClick = { menuExpanded = true }) {
                                            Text("⋮")
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Chuyển thư mục") },
                                                onClick = {
                                                    menuExpanded = false
                                                    movingEntry = entry
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Sửa tên") },
                                                onClick = {
                                                    menuExpanded = false
                                                    editingEntry = entry
                                                    editedTitle = entry.title
                                                    editError = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Xóa dấu trang") },
                                                onClick = {
                                                    menuExpanded = false
                                                    deletingEntry = entry
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Xong") }
        },
    )

    if (showFolderManager) {
        BookmarkFolderManagerDialog(
            folders = folders,
            onDismiss = { showFolderManager = false },
            onCreate = onCreateFolder,
            onRename = onRenameFolder,
            onDelete = onDeleteFolder,
        )
    }

    movingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { movingEntry = null },
            title = { Text("Chuyển dấu trang") },
            text = {
                Column {
                    Text(
                        entry.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            onMoveToFolder(entry.url, null)
                            movingEntry = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Chưa phân loại", modifier = Modifier.fillMaxWidth())
                    }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                onMoveToFolder(entry.url, folder.id)
                                movingEntry = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(folder.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { movingEntry = null }) { Text("Đóng") }
            },
        )
    }

    editingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = {
                editingEntry = null
                editError = false
            },
            title = { Text("Sửa tên dấu trang") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = {
                            editedTitle = it.take(BrowserBookmarkPolicy.MAX_TITLE_LENGTH)
                            editError = false
                        },
                        singleLine = true,
                        label = { Text("Tên dấu trang") },
                        isError = editError,
                    )
                    if (editError) {
                        Text(
                            "Tên không được để trống.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onRename(entry.url, editedTitle)) {
                            editingEntry = null
                            editError = false
                        } else {
                            editError = true
                        }
                    },
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingEntry = null
                        editError = false
                    },
                ) {
                    Text("Hủy")
                }
            },
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Xóa dấu trang?") },
            text = {
                Text(
                    "Xóa “${entry.title}” khỏi danh sách dấu trang trên thiết bị này?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry.url)
                        deletingEntry = null
                    },
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Hủy") }
            },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun BookmarkFolderManagerDialog(
    folders: List<BrowserBookmarkFolder>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Boolean,
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Boolean,
) {
    var creatingFolder by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<BrowserBookmarkFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<BrowserBookmarkFolder?>(null) }
    var folderName by remember { mutableStateOf("") }
    var folderError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thư mục dấu trang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        creatingFolder = true
                        editingFolder = null
                        folderName = ""
                        folderError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("＋ Tạo thư mục", modifier = Modifier.fillMaxWidth())
                }
                if (folders.isEmpty()) {
                    Text(
                        "Chưa có thư mục. Dấu trang mới sẽ nằm trong nhóm Chưa phân loại.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    folder.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = {
                                        editingFolder = folder
                                        creatingFolder = false
                                        folderName = folder.name
                                        folderError = false
                                    },
                                ) {
                                    Text("Sửa")
                                }
                                TextButton(onClick = { deletingFolder = folder }) {
                                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Xong") }
        },
    )

    if (creatingFolder || editingFolder != null) {
        val currentFolder = editingFolder
        AlertDialog(
            onDismissRequest = {
                creatingFolder = false
                editingFolder = null
                folderError = false
            },
            title = { Text(if (currentFolder == null) "Tạo thư mục" else "Đổi tên thư mục") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = {
                            folderName = it.take(BrowserBookmarkPolicy.MAX_FOLDER_NAME_LENGTH)
                            folderError = false
                        },
                        singleLine = true,
                        label = { Text("Tên thư mục") },
                        isError = folderError,
                    )
                    if (folderError) {
                        Text(
                            "Tên trống, trùng hoặc đã đạt giới hạn 30 thư mục.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val saved =
                            if (currentFolder == null) {
                                onCreate(folderName)
                            } else {
                                onRename(currentFolder.id, folderName)
                            }
                        if (saved) {
                            creatingFolder = false
                            editingFolder = null
                            folderError = false
                        } else {
                            folderError = true
                        }
                    },
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creatingFolder = false
                        editingFolder = null
                        folderError = false
                    },
                ) {
                    Text("Hủy")
                }
            },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Xóa thư mục?") },
            text = {
                Text(
                    "Các dấu trang trong “${folder.name}” sẽ được chuyển về Chưa phân loại.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(folder.id)
                        deletingFolder = null
                    },
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) { Text("Hủy") }
            },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun EntryDialog(
    title: String,
    entries: List<BrowserEntry>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (BrowserEntry) -> Unit,
    onClear: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (isLoading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
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
                                    overflow = TextOverflow.Ellipsis,
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
        },
    )
}
