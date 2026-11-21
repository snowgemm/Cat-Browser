package io.github.snowgemm.catbrowser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snowgemm.catbrowser.browser.ExtensionPermissionPrompt

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
                    if (prompt.description.isNotBlank()) {
                        Text(
                            prompt.description,
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (prompt.permissions.isNotEmpty()) {
                    item { permissionGroup("Quyền truy cập", prompt.permissions) }
                }
                if (prompt.origins.isNotEmpty()) {
                    item { permissionGroup("Website có thể truy cập", prompt.origins) }
                }
                if (prompt.dataCollectionPermissions.isNotEmpty()) {
                    item {
                        Text(
                            "Thu thập dữ liệu",
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                        prompt.dataCollectionPermissions.forEach { Text("• $it", fontSize = 12.sp) }
                        if (
                            prompt.kind == ExtensionPermissionPrompt.Kind.INSTALL &&
                            "technicalAndInteraction" in prompt.dataCollectionPermissions
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allowDataCollection,
                                    onCheckedChange = { allowDataCollection = it },
                                )
                                Text("Cho phép gửi dữ liệu kỹ thuật và tương tác", fontSize = 12.sp)
                            }
                        } else if (prompt.kind != ExtensionPermissionPrompt.Kind.INSTALL) {
                            Text(
                                "Đồng ý sẽ chấp thuận toàn bộ quyền dữ liệu được liệt kê cùng yêu cầu này. " +
                                    "GeckoView không cung cấp lựa chọn tách riêng ở bước này.",
                                Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
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
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(true, allowDataCollection) }) {
                Text("Đồng ý")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false, false) }) {
                Text("Từ chối")
            }
        },
    )
}

@Composable
private fun permissionGroup(title: String, values: List<String>) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.SemiBold)
    values.forEach {
        Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
