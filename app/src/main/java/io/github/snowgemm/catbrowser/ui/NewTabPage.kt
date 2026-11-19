package io.github.snowgemm.catbrowser.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.R
import io.github.snowgemm.catbrowser.browser.BrowserEntry

internal data class BrowserAddressSuggestion(
    val title: String,
    val url: String,
    val source: String,
    val tabId: String? = null
)

@Composable
internal fun NewTabPage(
    isPrivate: Boolean,
    bookmarks: List<BrowserEntry>,
    recentHistory: List<BrowserEntry>,
    onOpenUrl: (String) -> Unit,
    onFocusAddress: () -> Unit,
    onAddShortcut: (String, String) -> Boolean
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutTitle by remember { mutableStateOf("") }
    var shortcutUrl by remember { mutableStateOf("") }
    var shortcutError by remember { mutableStateOf<String?>(null) }

    val visibleHistory = remember(recentHistory, isPrivate) {
        if (isPrivate) emptyList() else recentHistory
            .filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
            .distinctBy { siteHost(it.url) }
    }
    val shortcutEntries = remember(bookmarks, visibleHistory, isPrivate) {
        buildList {
            val seenHosts = mutableSetOf<String>()
            bookmarks.filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
                .forEach { entry ->
                    if (seenHosts.add(siteHost(entry.url))) add(entry)
                }
            if (!isPrivate) {
                visibleHistory.forEach { entry ->
                    if (seenHosts.add(siteHost(entry.url))) add(entry)
                }
            }
        }.take(8)
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Cat Browser",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isPrivate) "Không gian duyệt riêng tư" else "Không gian duyệt web của bạn",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (isPrivate) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "ẨN DANH",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween = 260) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f)
            ) { it / 12 }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    Modifier.padding(horizontal = 19.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text(
                        if (isPrivate) "Duyệt web riêng tư" else "Bắt đầu khám phá",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isPrivate)
                            "Cat Browser không lưu lịch sử duyệt web của tab ẩn danh trên thiết bị."
                        else
                            "Mọi thứ bạn cần để tiếp tục hành trình trên web, ngay tại đây.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        onClick = onFocusAddress,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrowserGlyph(
                                BrowserGlyphType.SEARCH,
                                tint = MaterialTheme.colorScheme.primary,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.width(11.dp))
                            Text(
                                "Tìm kiếm hoặc nhập địa chỉ",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            BrowserGlyph(
                                BrowserGlyphType.GO,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconSize = 17.dp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Trang web yêu thích",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!isPrivate) {
                TextButton(onClick = {
                    shortcutTitle = ""
                    shortcutUrl = ""
                    shortcutError = null
                    showAddDialog = true
                }) { Text("＋ Thêm") }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            shortcutEntries.forEach { entry ->
                ShortcutTile(
                    entry = entry,
                    allowNetworkIcon = !isPrivate,
                    onClick = { onOpenUrl(entry.url) }
                )
            }
            if (!isPrivate) {
                AddShortcutTile {
                    shortcutTitle = ""
                    shortcutUrl = ""
                    shortcutError = null
                    showAddDialog = true
                }
            }
            if (shortcutEntries.isEmpty() && isPrivate) {
                Text(
                    "Dấu trang đã lưu sẽ xuất hiện tại đây.",
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gần đây",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!isPrivate && visibleHistory.isNotEmpty()) {
                Text(
                    visibleHistory.size.coerceAtMost(200).toString() + " gần nhất",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        when {
            isPrivate -> Text(
                "Lịch sử thường không xuất hiện trong tab ẩn danh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
            visibleHistory.isEmpty() -> Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Chưa có website gần đây",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Những trang bạn truy cập sẽ được hiển thị gọn tại đây.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            else -> {
                visibleHistory.take(6).chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { entry ->
                            RecentSiteCard(
                                entry = entry,
                                allowNetworkIcon = true,
                                onClick = { onOpenUrl(entry.url) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (isPrivate) "Tab ẩn danh không ghi lịch sử vào Cat Browser."
            else "Dấu trang và lịch sử được lưu trên thiết bị của bạn.",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Thêm lối tắt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shortcutTitle,
                        onValueChange = { shortcutTitle = it; shortcutError = null },
                        label = { Text("Tên website") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = shortcutUrl,
                        onValueChange = { shortcutUrl = it; shortcutError = null },
                        label = { Text("Địa chỉ website") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true
                    )
                    shortcutError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onAddShortcut(shortcutTitle, shortcutUrl)) {
                        showAddDialog = false
                    } else {
                        shortcutError = "Địa chỉ chưa hợp lệ hoặc dấu trang đang được tải."
                    }
                }) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Hủy") }
            }
        )
    }
}

private fun siteHost(url: String): String =
    runCatching { Uri.parse(url).host?.removePrefix("www.")?.lowercase() }
        .getOrNull().orEmpty().ifBlank { url.lowercase() }

@Composable
private fun FaviconBadge(
    url: String,
    size: androidx.compose.ui.unit.Dp,
    allowNetworkIcon: Boolean,
    fallbackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer
) {
    var bitmap by remember(url, allowNetworkIcon) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, allowNetworkIcon) {
        if (allowNetworkIcon) FaviconRepository.load(url) { bitmap = it }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = fallbackColor,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(7.dp)
                )
            } else {
                Text(
                    siteHost(url).firstOrNull()?.uppercaseChar()?.toString() ?: "W",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.34f).sp
                )
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    entry: BrowserEntry,
    allowNetworkIcon: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(82.dp),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FaviconBadge(entry.url, 44.dp, allowNetworkIcon)
            Text(
                entry.title.ifBlank { siteHost(entry.url) },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                siteHost(entry.url),
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddShortcutTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(82.dp),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                }
            }
            Text(
                "Thêm",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecentSiteCard(
    entry: BrowserEntry,
    allowNetworkIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaviconBadge(
                entry.url,
                size = 36.dp,
                allowNetworkIcon = allowNetworkIcon,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { siteHost(entry.url) },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    siteHost(entry.url),
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AddressSuggestionsPanel(
    query: String,
    suggestions: List<BrowserAddressSuggestion>,
    onSelect: (BrowserAddressSuggestion) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (query.isBlank()) "Gần đây và đang mở" else "Gợi ý",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSelect(suggestion) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (suggestion.source == "Tab đang mở") "▣" else "⌕",
                            color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(suggestion.title, color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(suggestion.url, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(suggestion.source, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp)
                    }
                }
            }
            if (query.isNotBlank()) {
                Surface(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Tìm kiếm “" + query + "”",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            } else if (suggestions.isEmpty()) {
                Text(
                    "Chưa có website gần đây. Nhập địa chỉ hoặc từ khóa để bắt đầu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
