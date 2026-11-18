package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAddShortcut: (String, String) -> Boolean
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutTitle by remember { mutableStateOf("") }
    var shortcutUrl by remember { mutableStateOf("") }
    var shortcutError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            "Cat Browser",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (isPrivate) "Tab ẩn danh mới" else "Mọi thứ bạn cần, ngay tại đây.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Lối tắt",
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

        val shortcuts = bookmarks.take(12)
        if (shortcuts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (isPrivate) "Dấu trang đã lưu sẽ xuất hiện tại đây."
                    else "Thêm website yêu thích để truy cập nhanh tại đây.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            shortcuts.chunked(4).forEach { rowEntries ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowEntries.forEach { entry ->
                        ShortcutTile(
                            entry = entry,
                            onClick = { onOpenUrl(entry.url) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Truy cập gần đây",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (isPrivate) {
            Text(
                "Lịch sử không được hiển thị trong tab ẩn danh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 9.dp)
            )
        } else if (recentHistory.isEmpty()) {
            Text(
                "Các website bạn vừa truy cập sẽ xuất hiện ở đây.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 9.dp)
            )
        } else {
            recentHistory.take(7).forEach { entry ->
                RecentSiteRow(entry = entry, onClick = { onOpenUrl(entry.url) })
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Dấu trang và lịch sử được lưu trên thiết bị.",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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

@Composable
private fun ShortcutTile(entry: BrowserEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val domain = entry.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        domain.firstOrNull()?.uppercaseChar()?.toString() ?: "W",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                entry.title,
                modifier = Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentSiteRow(entry: BrowserEntry, onClick: () -> Unit) {
    val domain = entry.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        domain.firstOrNull()?.uppercaseChar()?.toString() ?: "W",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(domain, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun AddressSuggestionsPanel(
    query: String,
    suggestions: List<BrowserAddressSuggestion>,
    onSelect: (BrowserAddressSuggestion) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
