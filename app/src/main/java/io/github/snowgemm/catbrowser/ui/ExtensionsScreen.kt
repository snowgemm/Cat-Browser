package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun ExtensionsScreen(manager: BrowserExtensionsManager, onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf("discover") }
    var pendingRemoval by remember { mutableStateOf<WebExtension?>(null) }

    LaunchedEffect(Unit) {
        manager.refreshInstalled()
        manager.searchCatalog("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  Quay lại") }
            Spacer(Modifier.weight(1f))
            Text(
                "EXTENSIONS",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Text(
            "Tiện ích mở rộng",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Khám phá và quản lý Firefox WebExtensions trên GeckoView.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionButton("Khám phá", selectedSection == "discover") {
                selectedSection = "discover"
            }
            SectionButton("Đã cài (${manager.installed.size})", selectedSection == "installed") {
                selectedSection = "installed"
                manager.refreshInstalled()
            }
        }

        if (selectedSection == "discover") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Tìm tiện ích…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { manager.searchCatalog(searchQuery) }
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Nguồn: Mozilla Add-ons · Lọc tiện ích dành cho Android",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { manager.searchCatalog(searchQuery) },
                enabled = !manager.isSearching && !manager.isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (manager.isSearching) "Đang tìm…" else "Tìm tiện ích")
            }
        }

        manager.statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        when (selectedSection) {
            "discover" -> {
                if (manager.catalog.isEmpty() && manager.isSearching) {
                    Text("Đang tải kho tiện ích…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (manager.catalog.isEmpty()) {
                    Text(
                        "Chưa có kết quả. Hãy tìm tên tiện ích.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(manager.catalog, key = { it.id }) { entry ->
                            CatalogExtensionCard(
                                entry = entry,
                                installed = manager.isInstalled(entry),
                                busy = manager.isWorking,
                                onInstall = { manager.install(entry) }
                            )
                        }
                    }
                }
            }
            else -> {
                if (manager.isLoadingInstalled && manager.installed.isEmpty()) {
                    Text("Đang tải tiện ích đã cài…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (manager.installed.isEmpty()) {
                    Text("Bạn chưa cài tiện ích nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(manager.installed, key = { it.id }) { extension ->
                            InstalledExtensionCard(
                                extension = extension,
                                busy = manager.isWorking,
                                onEnabledChange = { manager.setEnabled(extension, it) },
                                onUpdate = { manager.update(extension) },
                                onRemove = { pendingRemoval = extension }
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Cat dùng GeckoView để xác minh chữ ký, manifest và quyền. Không phải mọi Chrome Extension đều tương thích.",
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }

    manager.permissionPrompt?.let { prompt ->
        ExtensionPermissionDialog(prompt) { approved, allowData ->
            manager.resolvePermissionPrompt(approved, allowData)
        }
    }

    pendingRemoval?.let { extension ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Gỡ tiện ích?") },
            text = { Text("Bạn sẽ gỡ ${extension.metaData.name ?: extension.id} khỏi Cat Browser.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        manager.uninstall(extension)
                    }
                ) { Text("Gỡ") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Huỷ") }
            }
        )
    }
}

@Composable
private fun SectionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CatalogExtensionCard(
    entry: ExtensionCatalogEntry,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        entry.name.firstOrNull()?.uppercase() ?: "E",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        entry.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "v${entry.version} · ★ ${entry.averageRating} · ${entry.dailyUsers} người dùng",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    entry.summary,
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onInstall, enabled = !busy && !installed) {
                    Text(if (installed) "Đã cài" else "Cài đặt")
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
    onRemove: () -> Unit
) {
    val metadata = extension.metaData
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        metadata.name ?: extension.id,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "v${metadata.version} · ${metadata.creatorName ?: "Nhà phát triển không rõ"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = metadata.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !busy
                )
            }
            metadata.description?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onUpdate, enabled = !busy) { Text("Cập nhật") }
                TextButton(onClick = onRemove, enabled = !busy) { Text("Gỡ") }
            }
        }
    }
}

@Composable
private fun ExtensionPermissionDialog(
    prompt: ExtensionPermissionPrompt,
    onDecision: (Boolean, Boolean) -> Unit
) {
    var allowDataCollection by remember(prompt) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDecision(false, false) },
        title = { Text(prompt.kind.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(prompt.extensionName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (prompt.description.isNotBlank()) {
                        Text(
                            prompt.description,
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (prompt.permissions.isNotEmpty()) {
                    item { PermissionGroup("Quyền truy cập", prompt.permissions) }
                }
                if (prompt.origins.isNotEmpty()) {
                    item { PermissionGroup("Website có thể truy cập", prompt.origins) }
                }
                if (prompt.dataCollectionPermissions.isNotEmpty()) {
                    item {
                        Text(
                            "Thu thập dữ liệu",
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                        prompt.dataCollectionPermissions.forEach { Text("• $it", fontSize = 12.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allowDataCollection,
                                onCheckedChange = { allowDataCollection = it }
                            )
                            Text("Cho phép quyền thu thập dữ liệu này", fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Text(
                        "Cat không cấp quyền chạy trong chế độ riêng tư.",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(true, allowDataCollection) }) { Text("Đồng ý") }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false, false) }) { Text("Từ chối") }
        }
    )
}

@Composable
private fun PermissionGroup(title: String, values: List<String>) {
    Text(
        title,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        fontWeight = FontWeight.SemiBold
    )
    values.forEach { permission ->
        Text("• $permission", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
