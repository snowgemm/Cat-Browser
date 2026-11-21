package io.github.snowgemm.catbrowser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.BrowserExtensionsManager
import io.github.snowgemm.catbrowser.browser.ExtensionCatalogEntry
import io.github.snowgemm.catbrowser.browser.ExtensionPermissionPrompt
import org.mozilla.geckoview.WebExtension

@Composable
fun ExtensionsScreen(
    manager: BrowserExtensionsManager,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("discover") }
    var pendingRemoval by remember { mutableStateOf<WebExtension?>(null) }
    var pendingPrivateAccess by remember { mutableStateOf<WebExtension?>(null) }
    val xpiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(manager::installFromFile)
    }

    LaunchedEffect(Unit) {
        manager.refreshInstalled()
        manager.searchCatalog("")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(60.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onBack, shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 27.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text("CAT ADD-ONS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Tiện ích mở rộng", color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(manager.installed.size.toString() + " đã cài",
                    Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text("Mở rộng khả năng duyệt web", color = MaterialTheme.colorScheme.onSurface,
                fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text("Tìm và quản lý Firefox WebExtensions tương thích với GeckoView.",
                Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onOpenListing("https://addons.mozilla.org/android/") }) {
                    Text("Kho Mozilla")
                }
                TextButton(
                    onClick = { xpiPicker.launch(arrayOf("*/*")) },
                    enabled = !manager.isWorking
                ) {
                    Text("Cài từ tệp XPI")
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExtensionTab("Khám phá", section == "discover", Modifier.weight(1f)) { section = "discover" }
                ExtensionTab("Đã cài (" + manager.installed.size + ")", section == "installed",
                    Modifier.weight(1f)) {
                    section = "installed"
                    manager.refreshInstalled()
                }
            }

            if (section == "discover") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("⌕  Tìm tên tiện ích…") },
                    shape = RoundedCornerShape(15.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { manager.searchCatalog(query) }),
                    trailingIcon = {
                        Surface(onClick = { manager.searchCatalog(query) }, shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 5.dp).size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("→", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Nguồn chính thức · Mozilla Add-ons", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    if (manager.catalog.isNotEmpty()) Text(manager.catalog.size.toString() + " kết quả",
                        color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }

            manager.statusMessage?.let { message ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }

            when (section) {
                "discover" -> when {
                    manager.isSearching && manager.catalog.isEmpty() -> ExtensionState(
                        "Đang tìm tiện ích", "Đang tải dữ liệu từ Mozilla Add-ons…", Modifier.weight(1f), true)
                    manager.catalog.isEmpty() -> Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ExtensionState(
                            if (manager.isSearching) "Đang tải tiện ích" else "Chưa có kết quả",
                            if (manager.isSearching) "Đang truy vấn danh mục Mozilla Add-ons."
                            else "Thử từ khoá khác hoặc mở trực tiếp kho Mozilla Add-ons.",
                            Modifier.fillMaxWidth(),
                            manager.isSearching
                        )
                        if (!manager.isSearching) {
                            OutlinedButton(
                                onClick = { manager.searchCatalog(query) },
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Text("Tải lại danh sách")
                            }
                            TextButton(
                                onClick = { onOpenListing("https://addons.mozilla.org/android/") }
                            ) {
                                Text("Duyệt trên Mozilla Add-ons")
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(manager.catalog, key = { it.id }) { entry ->
                            CatalogExtensionCard(
                                entry = entry,
                                installed = manager.isInstalled(entry),
                                busy = manager.isWorking,
                                onInstall = { manager.install(entry) },
                                onOpenListing = onOpenListing
                            )
                        }
                    }
                }
                else -> when {
                    manager.isLoadingInstalled && manager.installed.isEmpty() -> ExtensionState(
                        "Đang kiểm tra tiện ích", "Đang đồng bộ danh sách từ GeckoView…", Modifier.weight(1f), true)
                    manager.installed.isEmpty() -> ExtensionState(
                        "Chưa có tiện ích nào", "Các tiện ích bạn cài sẽ xuất hiện tại đây.",
                        Modifier.weight(1f), false)
                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(manager.installed, key = { it.id }) { extension ->
                            InstalledExtensionCard(
                                extension, manager.isWorking,
                                { manager.setEnabled(extension, it) },
                                { manager.update(extension) },
                                { pendingRemoval = extension },
                                { allowed ->
                                    if (allowed && !extension.metaData.allowedInPrivateBrowsing) {
                                        pendingPrivateAccess = extension
                                    } else {
                                        manager.setAllowedInPrivateBrowsing(extension, allowed)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Text("Danh mục Android của Mozilla không đảm bảo mọi tiện ích đều tương thích GeckoView. Cat xác minh chữ ký và quyền trước khi cài.",
                Modifier.padding(top = 10.dp, bottom = 9.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp, lineHeight = 14.sp)
        }
    }

    /*
     * Permission dialogs are rendered at BrowserScreen's root so a pending
     * GeckoView request remains visible across every browser destination.
     *
     */

    pendingPrivateAccess?.let { extension ->
        AlertDialog(
            onDismissRequest = { pendingPrivateAccess = null },
            title = { Text("Cho phép tiện ích chạy trong tab ẩn danh?") },
            text = {
                Text(
                    (extension.metaData.name ?: extension.id) +
                        " có thể đọc hoặc thay đổi dữ liệu trên các trang được cấp quyền khi bạn duyệt riêng tư. " +
                        "Chỉ bật khi bạn tin tưởng tiện ích này."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPrivateAccess = null
                    manager.setAllowedInPrivateBrowsing(extension, true)
                }) { Text("Cho phép") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPrivateAccess = null }) { Text("Không cho phép") }
            }
        )
    }

    pendingRemoval?.let { extension ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Gỡ tiện ích?") },
            text = { Text("Bạn sẽ gỡ " + (extension.metaData.name ?: extension.id) + " khỏi Cat Browser.") },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; manager.uninstall(extension) }) { Text("Gỡ tiện ích") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Huỷ") } }
        )
    }
}

@Composable
private fun ExtensionTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
private fun CatalogExtensionCard(
    entry: ExtensionCatalogEntry,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(entry.name.firstOrNull()?.uppercase() ?: "E", color = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(entry.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("★ " + entry.averageRating + "  ·  " + entry.dailyUsers + " người dùng",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("AMO", Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (entry.summary.isNotBlank()) Text(entry.summary, Modifier.padding(top = 11.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("v" + entry.version, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                TextButton(onClick = { onOpenListing(entry.listingUrl) }) {
                    Text("Chi tiết")
                }
                Button(
                    onClick = onInstall,
                    enabled = !busy && !installed && entry.downloadUrl != null,
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text(
                        when {
                            installed -> "Đã cài"
                            entry.downloadUrl == null -> "Chưa có XPI"
                            else -> "Cài đặt"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    extension: WebExtension,
    busy: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
    onPrivateChange: (Boolean) -> Unit
) {
    val metadata = extension.metaData
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text((metadata.name ?: extension.id).firstOrNull()?.uppercase() ?: "E",
                            color = MaterialTheme.colorScheme.primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(metadata.name ?: extension.id, color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("v" + metadata.version + " · " + (metadata.creatorName ?: "Nhà phát triển không rõ"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = metadata.enabled, onCheckedChange = onEnabledChange, enabled = !busy)
            }
            metadata.description?.takeIf(String::isNotBlank)?.let {
                Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cho phép trong tab ẩn danh", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (metadata.allowedInPrivateBrowsing) "Đã cấp quyền" else "Mặc định đang tắt",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = metadata.allowedInPrivateBrowsing,
                    onCheckedChange = onPrivateChange,
                    enabled = !busy
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onUpdate, enabled = !busy) { Text("Cập nhật") }
                TextButton(onClick = onRemove, enabled = !busy) { Text("Gỡ") }
            }
        }
    }
}

@Composable
private fun ExtensionState(title: String, detail: String, modifier: Modifier, loading: Boolean) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(68.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (loading) "◌" else "✧", color = MaterialTheme.colorScheme.primary, fontSize = 31.sp)
                }
            }
            Text(title, Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
internal fun extensionPermissionDialog(
    prompt: ExtensionPermissionPrompt,
    onDecision: (Boolean, Boolean) -> Unit,
) {
    var allowDataCollection by remember(prompt) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDecision(false, false) },
        title = { Text(prompt.kind.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(prompt.extensionName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (prompt.description.isNotBlank()) Text(prompt.description, Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (prompt.permissions.isNotEmpty()) item { permissionGroup("Quyền truy cập", prompt.permissions) }
                if (prompt.origins.isNotEmpty()) item { permissionGroup("Website có thể truy cập", prompt.origins) }
                if (prompt.dataCollectionPermissions.isNotEmpty()) item {
                    Text("Thu thập dữ liệu", Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.SemiBold)
                    prompt.dataCollectionPermissions.forEach { Text("• " + it, fontSize = 12.sp) }
                    if (prompt.kind == ExtensionPermissionPrompt.Kind.INSTALL &&
                        "technicalAndInteraction" in prompt.dataCollectionPermissions
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = allowDataCollection, onCheckedChange = { allowDataCollection = it })
                            Text("Cho phép gửi dữ liệu kỹ thuật và tương tác", fontSize = 12.sp)
                        }
                    } else if (prompt.kind != ExtensionPermissionPrompt.Kind.INSTALL) {
                        Text(
                            "Đồng ý sẽ chấp thuận toàn bộ quyền dữ liệu được liệt kê cùng yêu cầu này. " +
                                "GeckoView không cung cấp lựa chọn tách riêng ở bước này.",
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                item {
                    Text(
                        if (prompt.kind == ExtensionPermissionPrompt.Kind.INSTALL) {
                            "Quyền chạy trong tab ẩn danh mặc định tắt. Bạn có thể xem xét cấp quyền riêng sau khi cài đặt."
                        } else {
                            "Yêu cầu này không thay đổi quyền chạy trong tab ẩn danh; quyền đó được quản lý riêng."
                        },
                        Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDecision(true, allowDataCollection) }) { Text("Đồng ý") } },
        dismissButton = { TextButton(onClick = { onDecision(false, false) }) { Text("Từ chối") } }
    )
}

@Composable
private fun permissionGroup(title: String, values: List<String>) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.SemiBold)
    values.forEach { Text("• " + it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
