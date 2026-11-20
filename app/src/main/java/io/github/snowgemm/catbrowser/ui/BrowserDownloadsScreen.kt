package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserDownloadItem
import io.github.snowgemm.catbrowser.browser.BrowserDownloadManager
import io.github.snowgemm.catbrowser.browser.BrowserDownloadStatus

@Composable
fun BrowserDownloadsScreen(
    manager: BrowserDownloadManager,
    onBack: () -> Unit
) {
    val downloads = manager.downloads

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
            TextButton(onClick = onBack) { Text("‹  Quay lại") }
            Column(Modifier.weight(1f)) {
                Text(
                    "Tải xuống",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    downloads.size.toString() + " mục trong phiên hiện tại",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(
                onClick = { manager.clearCompleted(deleteFiles = false) },
                enabled = downloads.any {
                    it.status == BrowserDownloadStatus.COMPLETE ||
                        it.status == BrowserDownloadStatus.FAILED ||
                        it.status == BrowserDownloadStatus.CANCELLED
                }
            ) { Text("Dọn danh sách") }
        }

        if (downloads.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Chưa có tệp nào", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Tệp tải từ trang web sẽ xuất hiện ở đây.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadItemCard(item, manager)
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: BrowserDownloadItem,
    manager: BrowserDownloadManager
) {
    val isActive = item.status == BrowserDownloadStatus.QUEUED ||
        item.status == BrowserDownloadStatus.DOWNLOADING
    val isComplete = item.status == BrowserDownloadStatus.COMPLETE
    val statusText = when (item.status) {
        BrowserDownloadStatus.QUEUED -> "Đang chờ"
        BrowserDownloadStatus.DOWNLOADING -> "Đang tải"
        BrowserDownloadStatus.COMPLETE -> "Hoàn tất"
        BrowserDownloadStatus.FAILED -> "Thất bại"
        BrowserDownloadStatus.CANCELLED -> "Đã hủy"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                item.fileName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusText + " · " + formatBytes(item.receivedBytes) +
                    if (item.totalBytes > 0) " / " + formatBytes(item.totalBytes) else "",
                color = when (item.status) {
                    BrowserDownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    BrowserDownloadStatus.COMPLETE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp
            )
            if (item.isPrivate) {
                Text(
                    "Được tải từ tab ẩn danh · Tệp đã lưu vẫn còn trong thư mục Download.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            if (isActive) {
                if (item.totalBytes > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = {
                            (item.receivedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (item.status == BrowserDownloadStatus.DOWNLOADING) {
                    Text(
                        "Đang nhận dữ liệu từ trang web…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            item.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { manager.open(item) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Mở tệp") }
                    OutlinedButton(
                        onClick = { manager.share(item) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Chia sẻ") }
                }
                TextButton(
                    onClick = { manager.remove(item, deleteFile = true) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Xóa tệp") }
            } else if (isActive) {
                TextButton(
                    onClick = { manager.cancel(item) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Hủy tải xuống") }
            } else {
                TextButton(
                    onClick = { manager.remove(item) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Xóa khỏi danh sách") }
            }
            if (!item.isPrivate) {
                Text(
                    item.sourceUrl,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
