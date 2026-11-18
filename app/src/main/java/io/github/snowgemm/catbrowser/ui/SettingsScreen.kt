package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.snowgemm.catbrowser.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserController
import io.github.snowgemm.catbrowser.update.BrowserUpdateManager
import io.github.snowgemm.catbrowser.update.UpdateChannel
import io.github.snowgemm.catbrowser.update.UpdateUiState

@Composable
fun SettingsScreen(controller: BrowserController, onBack: () -> Unit) {
    val context = LocalContext.current
    val updateManager = remember { BrowserUpdateManager(context) }
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }
    val stableUpdateState = updateManager.stableState
    val betaUpdateState = updateManager.betaState
    var homePage by remember(controller.homePage) { mutableStateOf(controller.homePage) }
    var homePageError by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }
    var clearHistorySelected by remember { mutableStateOf(true) }
    var clearCookiesSelected by remember { mutableStateOf(true) }
    var clearCacheSelected by remember { mutableStateOf(true) }
    var clearPermissionsSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        updateManager.checkForUpdates(UpdateChannel.STABLE)
        controller.loadHistory()
        controller.loadBookmarks()
        controller.loadSitePermissions()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  Quay lại") }
            Text(
                "Cài đặt",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            item {
                SettingsSectionTitle("CAT BROWSER")
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Image(painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Cat Browser",
                                modifier = Modifier.size(54.dp).padding(4.dp),
                                contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cat Browser", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("Riêng tư · Nhanh · Linh hoạt",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("GECKOVIEW", Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("CẬP NHẬT PHẦN MỀM")
                UpdateChannelCard(
                    title = "Stable",
                    description = "Bản ổn định dành cho sử dụng hằng ngày.",
                    channel = UpdateChannel.STABLE,
                    state = stableUpdateState,
                    manager = updateManager
                )
                UpdateChannelCard(
                    title = "Beta · pre-release",
                    description = "Bản thử nghiệm mới nhất. Có thể còn lỗi hoặc thay đổi chưa hoàn thiện.",
                    channel = UpdateChannel.BETA,
                    state = betaUpdateState,
                    manager = updateManager
                )
                Text(
                    "Mỗi kênh tải APK đúng kiến trúc thiết bị và xác minh SHA-256 trước khi mở trình cài đặt Android.",
                    Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
                    Text("Mặc định là sảnh Cat Browser với lối tắt và website vừa truy cập. Bạn cũng có thể nhập URL riêng.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(homePage, { homePage = it; homePageError = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true, label = { Text("URL trang chủ") }, isError = homePageError)
                    if (homePageError) Text("URL không hợp lệ. Hãy nhập about:blank hoặc địa chỉ http/https.",
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
                    SettingsChoice("Mở trang chủ", "Mở sảnh Cat Browser (about:blank) hoặc URL bạn đã đặt",
                        controller.startupMode == "home") { controller.updateStartupMode("home") }
                }
            }
            item {
                SettingsSectionTitle("DỮ LIỆU DUYỆT WEB")
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lịch sử duyệt web", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (controller.isHistoryLoaded) {
                                    controller.history.size.toString() + " mục đã lưu"
                                } else {
                                    "Đang tải lịch sử…"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = { showClearHistoryDialog = true },
                            enabled = controller.isHistoryLoaded && controller.history.isNotEmpty()
                        ) { Text("Xóa") }
                    }
                    if (historyCleared) Text("Đã xóa lịch sử.",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    Text(
                        if (controller.isBookmarksLoaded) {
                            "Dấu trang: " + controller.bookmarks.size
                        } else {
                            "Đang tải dấu trang…"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        enabled = !controller.isClearingData,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (controller.isClearingData) "Đang xóa dữ liệu…" else "Xóa dữ liệu duyệt web")
                    }
                    controller.clearDataMessage?.let { message ->
                        Text(
                            message,
                            color = if (controller.clearDataError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                SettingsSectionTitle("QUYỀN WEBSITE")
                SettingsCard {
                    Text(
                        "Quyền đã được lưu theo website. Tab ẩn danh dùng quyền tạm thời của phiên riêng.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (controller.isSitePermissionsLoading) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp))
                            Text("Đang tải quyền website…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (controller.sitePermissions.isEmpty()) {
                        Text(
                            controller.sitePermissionsMessage ?: "Chưa có website nào lưu quyền.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        controller.sitePermissions.take(30).forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        entry.origin,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        entry.permissionLabel + " · " + entry.stateLabel +
                                            if (entry.isPrivate) " · Ẩn danh" else "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!entry.canReset) {
                                        Text(
                                            "Quyền này có giới hạn thu hồi theo GeckoView.",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { controller.resetSitePermission(entry) },
                                    enabled = entry.canReset
                                ) { Text("Đặt lại") }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }
                        if (controller.sitePermissions.size > 30) {
                            Text(
                                "Đang hiển thị 30 trên " + controller.sitePermissions.size + " quyền.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    controller.sitePermissionsMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SettingsSectionTitle("THÔNG TIN")
                SettingsCard {
                    Text("Cat Browser", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Phiên bản $appVersion · Cập nhật qua GitHub Releases.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (showClearDataDialog) AlertDialog(
        onDismissRequest = { if (!controller.isClearingData) showClearDataDialog = false },
        title = { Text("Xóa dữ liệu duyệt web") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClearDataOption(
                    "Lịch sử duyệt web",
                    "Xóa danh sách trang đã truy cập và lịch sử quay lại của tab.",
                    clearHistorySelected
                ) { clearHistorySelected = it }
                ClearDataOption(
                    "Cookie, web storage và đăng nhập",
                    "Xóa cookie, DOM storage và auth sessions; có thể đăng xuất khỏi website.",
                    clearCookiesSelected
                ) { clearCookiesSelected = it }
                ClearDataOption(
                    "Cache và ảnh đã lưu",
                    "Xóa tệp tạm để giải phóng bộ nhớ; website có thể tải lại chậm hơn lần đầu.",
                    clearCacheSelected
                ) { clearCacheSelected = it }
                ClearDataOption(
                    "Quyền website",
                    "Đặt lại các quyền vị trí, thông báo và quyền nội dung đã lưu.",
                    clearPermissionsSelected
                ) { clearPermissionsSelected = it }
                Text(
                    "Các tab đang mở sẽ được tải lại sau khi xóa. Dấu trang và tệp đã tải xuống không bị xóa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    controller.clearBrowsingData(
                        clearHistory = clearHistorySelected,
                        clearCookiesAndSiteData = clearCookiesSelected,
                        clearCache = clearCacheSelected,
                        clearSitePermissions = clearPermissionsSelected
                    )
                    showClearDataDialog = false
                },
                enabled = !controller.isClearingData
            ) { Text("Xóa dữ liệu đã chọn") }
        },
        dismissButton = {
            TextButton(
                onClick = { showClearDataDialog = false },
                enabled = !controller.isClearingData
            ) { Text("Hủy") }
        }
    )
}

@Composable
private fun ClearDataOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun UpdateChannelCard(
    title: String,
    description: String,
    channel: UpdateChannel,
    state: UpdateUiState,
    manager: BrowserUpdateManager
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.isChecking || state.isDownloading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            state.message,
            color = if (state.error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isDownloading) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                state.progress.toString() + "%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (state.updateAvailable) {
            Text(
                "Phiên bản " + state.version,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            if (state.releaseNotes.isNotBlank()) {
                Text(
                    state.releaseNotes,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        val onAction: () -> Unit = {
            when {
                state.downloaded -> {
                    manager.installDownloadedApk(channel)
                    Unit
                }
                state.updateAvailable -> manager.downloadUpdate(channel)
                else -> manager.checkForUpdates(channel)
            }
        }
        val buttonEnabled = !state.isChecking && !state.isDownloading
        val buttonText = when {
            state.isDownloading -> "Đang tải…"
            state.downloaded -> "Cài đặt " + title
            state.updateAvailable -> "Tải " + title
            channel == UpdateChannel.BETA -> "Kiểm tra Beta"
            else -> "Kiểm tra Stable"
        }
        if (channel == UpdateChannel.BETA) {
            OutlinedButton(
                onClick = onAction,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text(buttonText) }
        } else {
            Button(
                onClick = onAction,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text(buttonText) }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, Modifier.padding(start = 4.dp, bottom = 7.dp),
        color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
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
