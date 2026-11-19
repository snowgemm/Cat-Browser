package io.github.snowgemm.catbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.R
import io.github.snowgemm.catbrowser.browser.BrowserEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BrowserAddressSuggestion(
    val title: String,
    val url: String,
    val source: String,
    val tabId: String? = null
)

internal const val HOME_APPEARANCE_PREFERENCES = "cat_browser_appearance"
internal const val HOME_WALLPAPER_URI_KEY = "new_tab_wallpaper_uri"

@Composable
internal fun NewTabPage(
    isPrivate: Boolean,
    bookmarks: List<BrowserEntry>,
    recentHistory: List<BrowserEntry>,
    openTabCount: Int,
    onOpenUrl: (String) -> Unit,
    onFocusAddress: () -> Unit,
    onAddShortcut: (String, String) -> Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaperUri = remember(context) {
        context.getSharedPreferences(HOME_APPEARANCE_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .getString(HOME_WALLPAPER_URI_KEY, null)
    }
    val wallpaperBitmap by produceState<Bitmap?>(null, wallpaperUri) {
        value = null
        if (!wallpaperUri.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) {
                decodeHomeWallpaper(context, Uri.parse(wallpaperUri))
            }
        }
    }
    val hasWallpaper = wallpaperBitmap != null
    val heroTextColor = if (hasWallpaper) androidx.compose.ui.graphics.Color.White
        else MaterialTheme.colorScheme.onBackground
    val mutedTextColor = if (hasWallpaper) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val sectionCardColor = if (hasWallpaper) androidx.compose.ui.graphics.Color(0xCC192634)
        else MaterialTheme.colorScheme.surfaceVariant
    val tileColor = if (hasWallpaper) androidx.compose.ui.graphics.Color(0xB8202D3C)
        else MaterialTheme.colorScheme.surface
    val recentCardColor = if (hasWallpaper) androidx.compose.ui.graphics.Color(0xCC192634)
        else MaterialTheme.colorScheme.surface

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
            bookmarks
                .filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
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

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        wallpaperBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.44f))
            )
        }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Cat Browser",
                        color = heroTextColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isPrivate) "Không gian duyệt riêng tư" else "Không gian duyệt web của bạn",
                        color = mutedTextColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isPrivate) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "ẨN DANH",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )
                    }
                }
            }

            if (isPrivate) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = sectionCardColor
                ) {
                    Row(
                        Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("◉", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 18.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Tab ẩn danh",
                                color = heroTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Cat Browser không lưu lịch sử của phiên này trên thiết bị.",
                                color = mutedTextColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(22.dp))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(260)) + slideInVertically(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 430f)
                    ) { it / 14 }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = sectionCardColor
                    ) {
                        Column(
                            Modifier.padding(horizontal = 17.dp, vertical = 17.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Không gian của bạn",
                                    modifier = Modifier.weight(1f),
                                    color = heroTextColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "TRÊN THIẾT BỊ",
                                    color = mutedTextColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HomeStatCell(bookmarks.size.toString(), "Dấu trang", Modifier.weight(1f), heroTextColor, mutedTextColor)
                                Box(
                                    Modifier.width(1.dp).height(38.dp)
                                        .background(mutedTextColor.copy(alpha = 0.35f))
                                )
                                HomeStatCell(visibleHistory.size.toString(), "Website gần đây", Modifier.weight(1f), heroTextColor, mutedTextColor)
                                Box(
                                    Modifier.width(1.dp).height(38.dp)
                                        .background(mutedTextColor.copy(alpha = 0.35f))
                                )
                                HomeStatCell(openTabCount.coerceAtLeast(1).toString(), "Tab thường", Modifier.weight(1f), heroTextColor, mutedTextColor)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Trang web yêu thích",
                    color = heroTextColor,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                shortcutEntries.forEach { entry ->
                    ShortcutTile(
                        entry = entry,
                        allowNetworkIcon = !isPrivate,
                        containerColor = tileColor,
                        onClick = { onOpenUrl(entry.url) }
                    )
                }
                if (!isPrivate) {
                    AddShortcutTile(
                        containerColor = tileColor
                    ) {
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
                        color = mutedTextColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Truy cập gần đây",
                    color = heroTextColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (!isPrivate && visibleHistory.isNotEmpty()) {
                    Text(
                        visibleHistory.size.toString() + " website",
                        color = mutedTextColor,
                        fontSize = 10.sp
                    )
                }
            }

            when {
                isPrivate -> Text(
                    "Lịch sử thường không xuất hiện trong tab ẩn danh.",
                    color = mutedTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
                visibleHistory.isEmpty() -> Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = recentCardColor
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Chưa có website gần đây",
                            color = heroTextColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Những trang bạn truy cập sẽ được hiển thị tại đây.",
                            modifier = Modifier.padding(top = 4.dp),
                            color = mutedTextColor,
                            fontSize = 11.sp
                        )
                    }
                }
                else -> {
                    visibleHistory.take(6).chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            row.forEach { entry ->
                                RecentSiteCard(
                                    entry = entry,
                                    allowNetworkIcon = true,
                                    containerColor = recentCardColor,
                                    onClick = { onOpenUrl(entry.url) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                if (isPrivate) "Tab ẩn danh không ghi lịch sử vào Cat Browser."
                else "Dấu trang, lịch sử và ảnh nền được lưu cục bộ trên thiết bị.",
                color = mutedTextColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
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

@Composable
private fun HomeStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier.padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = valueColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            color = labelColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun decodeHomeWallpaper(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 2400) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
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
    containerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(82.dp),
        shape = RoundedCornerShape(17.dp),
        color = containerColor
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
private fun AddShortcutTile(containerColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(82.dp),
        shape = RoundedCornerShape(17.dp),
        color = containerColor
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
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
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
