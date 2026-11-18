package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserController

@Composable
fun SettingsScreen(controller: BrowserController, onBack: () -> Unit) {
    var homePage by remember(controller.homePage) { mutableStateOf(controller.homePage) }
    var homePageError by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Quay lại") }
            Text("Cài đặt", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                SettingsSectionTitle("GIAO DIỆN")
                SettingsCard {
                    Text("Chủ đề", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Chọn giao diện cho Cat Browser", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    SettingsChoice("Theo hệ thống", "Tự đổi theo giao diện điện thoại",
                        controller.themeMode == "system") { controller.updateThemeMode("system") }
                    SettingsChoice("Sáng", "Nền sáng, chữ tối", controller.themeMode == "light") {
                        controller.updateThemeMode("light")
                    }
                    SettingsChoice("Tối", "Nền tối dịu mắt", controller.themeMode == "dark") {
                        controller.updateThemeMode("dark")
                    }
                }
            }
            item {
                SettingsSectionTitle("TÌM KIẾM")
                SettingsCard {
                    Text("Công cụ tìm kiếm", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Dùng khi nhập từ khóa vào thanh địa chỉ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    listOf("google" to "Google", "duckduckgo" to "DuckDuckGo",
                        "bing" to "Bing", "brave" to "Brave Search").forEach { (id, label) ->
                        SettingsChoice(label, null, controller.searchEngine == id) {
                            controller.updateSearchEngine(id)
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("TRANG CHỦ")
                SettingsCard {
                    Text("Địa chỉ trang chủ", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Dùng cho nút Trang chủ và chế độ khởi động bằng trang chủ.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(homePage, { homePage = it; homePageError = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true, label = { Text("URL trang chủ") }, isError = homePageError)
                    if (homePageError) Text("URL không hợp lệ. Hãy nhập địa chỉ http/https.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { homePageError = !controller.setHomePage(homePage) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("Lưu trang chủ")
                    }
                }
            }
            item {
                SettingsSectionTitle("KHI MỞ CAT BROWSER")
                SettingsCard {
                    SettingsChoice("Tiếp tục phiên trước", "Khôi phục các tab đang mở",
                        controller.startupMode == "continue") { controller.updateStartupMode("continue") }
                    SettingsChoice("Mở trang chủ", "Bắt đầu bằng một tab trang chủ mới",
                        controller.startupMode == "home") { controller.updateStartupMode("home") }
                }
            }
            item {
                SettingsSectionTitle("DỮ LIỆU DUYỆT WEB")
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lịch sử duyệt web", fontWeight = FontWeight.SemiBold)
                            Text("${controller.history.size} mục đã lưu",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showClearHistoryDialog = true },
                            enabled = controller.history.isNotEmpty()) { Text("Xóa") }
                    }
                    if (historyCleared) Text("Đã xóa lịch sử.",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    Text("Dấu trang: ${controller.bookmarks.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SettingsSectionTitle("VỀ CAT BROWSER")
                SettingsCard {
                    Text("Cat Browser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Phiên bản 0.2.0 · GeckoView", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text("Chủ đề chỉ thay đổi giao diện trình duyệt, không tự ép màu các trang web.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showClearHistoryDialog) AlertDialog(
        onDismissRequest = { showClearHistoryDialog = false },
        title = { Text("Xóa lịch sử duyệt web?") },
        text = { Text("Thao tác này không thể hoàn tác. Dấu trang và các tab sẽ được giữ nguyên.") },
        confirmButton = {
            TextButton(onClick = {
                controller.clearHistory()
                historyCleared = true
                showClearHistoryDialog = false
            }) { Text("Xóa lịch sử") }
        },
        dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Hủy") } }
    )
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun SettingsChoice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
