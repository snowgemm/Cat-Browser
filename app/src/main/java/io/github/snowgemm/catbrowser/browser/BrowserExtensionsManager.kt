package io.github.snowgemm.catbrowser.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ExtensionCatalogEntry(
    val id: String,
    val slug: String,
    val name: String,
    val summary: String,
    val version: String,
    val downloadUrl: String,
    val listingUrl: String,
    val averageRating: Double,
    val dailyUsers: Int
)

data class ExtensionPermissionPrompt(
    val extensionName: String,
    val description: String,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String>,
    val kind: Kind
) {
    enum class Kind(val title: String) {
        INSTALL("Cài đặt tiện ích"),
        OPTIONAL("Yêu cầu quyền mới"),
        UPDATE("Cập nhật cần quyền mới")
    }
}

/** Owns installed WebExtensions and the Mozilla Add-ons catalog. */
class BrowserExtensionsManager(runtime: GeckoRuntime) {
    private val controller = runtime.webExtensionController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()

    val installed = mutableStateListOf<WebExtension>()
    val catalog = mutableStateListOf<ExtensionCatalogEntry>()

    var isLoadingInstalled by mutableStateOf(false)
        private set
    var isSearching by mutableStateOf(false)
        private set
    var isWorking by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var permissionPrompt by mutableStateOf<ExtensionPermissionPrompt?>(null)
        private set

    private var pendingPermissionDecision: ((Boolean, Boolean) -> Unit)? = null

    init {
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                showPermissionPrompt(
                    extension, permissions, origins, dataCollectionPermissions,
                    ExtensionPermissionPrompt.Kind.INSTALL
                ) { approved, allowData ->
                    result.complete(
                        WebExtension.PermissionPromptResponse(
                            approved, false, approved && allowData
                        )
                    )
                }
                return result
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                showPermissionPrompt(
                    extension, permissions, origins, dataCollectionPermissions,
                    ExtensionPermissionPrompt.Kind.OPTIONAL
                ) { approved, _ ->
                    result.complete(if (approved) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                showPermissionPrompt(
                    extension, newPermissions, newOrigins, newDataCollectionPermissions,
                    ExtensionPermissionPrompt.Kind.UPDATE
                ) { approved, _ ->
                    result.complete(if (approved) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
                return result
            }
        })
    }

    private fun showPermissionPrompt(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
        kind: ExtensionPermissionPrompt.Kind,
        decision: (Boolean, Boolean) -> Unit
    ) {
        pendingPermissionDecision = decision
        permissionPrompt = ExtensionPermissionPrompt(
            extensionName = extension.metaData.name?.takeIf(String::isNotBlank) ?: extension.id,
            description = extension.metaData.description.orEmpty(),
            permissions = permissions.toList(),
            origins = origins.toList(),
            dataCollectionPermissions = dataCollectionPermissions.toList(),
            kind = kind
        )
    }

    fun resolvePermissionPrompt(approved: Boolean, allowDataCollection: Boolean = false) {
        val decision = pendingPermissionDecision
        pendingPermissionDecision = null
        permissionPrompt = null
        decision?.invoke(approved, allowDataCollection)
    }

    fun refreshInstalled() {
        if (isLoadingInstalled) return
        isLoadingInstalled = true
        controller.list().accept(
            { extensions ->
                installed.clear()
                installed.addAll(
                    extensions.orEmpty().sortedBy {
                        it.metaData.name?.lowercase().orEmpty().ifBlank { it.id.lowercase() }
                    }
                )
                isLoadingInstalled = false
            },
            { error ->
                isLoadingInstalled = false
                statusMessage = "Không thể đọc danh sách tiện ích: ${error?.message ?: "Lỗi GeckoView"}"
            }
        )
    }

    fun searchCatalog(query: String) {
        if (isSearching) return
        isSearching = true
        statusMessage = null

        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val builder = Uri.parse(AMO_SEARCH_URL).buildUpon()
                    .appendQueryParameter("app", "android")
                    .appendQueryParameter("type", "extension")
                    .appendQueryParameter("page_size", "24")
                    .appendQueryParameter("sort", "users")
                query.trim().take(100).takeIf(String::isNotBlank)?.let {
                    builder.appendQueryParameter("q", it)
                }

                connection = URL(builder.build().toString()).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "CatBrowser-Android")

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(payload).optJSONArray("results")
                val entries = buildList {
                    if (results != null) {
                        for (index in 0 until results.length()) {
                            val item = results.optJSONObject(index) ?: continue
                            val currentVersion = item.optJSONObject("current_version") ?: continue
                            val files = currentVersion.optJSONArray("files") ?: continue
                            val file = (0 until files.length())
                                .mapNotNull(files::optJSONObject)
                                .firstOrNull { candidate ->
                                    candidate.optString("url").startsWith("https://addons.mozilla.org/") &&
                                        candidate.optString("status").let { it.isBlank() || it == "public" }
                                } ?: continue

                            val slug = item.optString("slug")
                            if (slug.isBlank()) continue
                            val ratings = item.optJSONObject("ratings")
                            add(
                                ExtensionCatalogEntry(
                                    id = item.optString("id", slug),
                                    slug = slug,
                                    name = item.optString("name", slug),
                                    summary = item.optString("summary"),
                                    version = currentVersion.optString("version"),
                                    downloadUrl = file.optString("url"),
                                    listingUrl = "https://addons.mozilla.org/android/addon/$slug/",
                                    averageRating = ratings?.optDouble("average", 0.0) ?: 0.0,
                                    dailyUsers = item.optInt("average_daily_users", 0)
                                )
                            )
                        }
                    }
                }

                mainHandler.post {
                    catalog.clear()
                    catalog.addAll(entries)
                    isSearching = false
                    statusMessage = if (entries.isEmpty()) {
                        "Không tìm thấy tiện ích tương thích Android."
                    } else null
                }
            } catch (error: Exception) {
                mainHandler.post {
                    isSearching = false
                    statusMessage = "Không thể tải kho Mozilla Add-ons: ${error?.message ?: "Lỗi kết nối"}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun isInstalled(entry: ExtensionCatalogEntry): Boolean =
        installed.any {
            it.metaData.amoListingUrl == entry.listingUrl ||
                it.metaData.downloadUrl == entry.downloadUrl
        }

    fun install(entry: ExtensionCatalogEntry) {
        val uri = Uri.parse(entry.downloadUrl)
        if (uri.scheme != "https" || uri.host != "addons.mozilla.org") {
            statusMessage = "Đã chặn nguồn cài đặt không hợp lệ."
            return
        }
        if (isWorking) return

        isWorking = true
        statusMessage = "Đang xác minh và cài ${entry.name}…"
        controller.install(
            entry.downloadUrl,
            WebExtensionController.INSTALLATION_METHOD_MANAGER
        ).accept(
            { extension ->
                isWorking = false
                statusMessage = "Đã cài ${extension.metaData.name ?: entry.name}."
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Cài đặt thất bại: ${error?.message ?: "Extension không hợp lệ hoặc không được hỗ trợ"}"
            }
        )
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        if (isWorking) return
        isWorking = true
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            {
                isWorking = false
                statusMessage = if (enabled) "Đã bật tiện ích." else "Đã tắt tiện ích."
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Không thể đổi trạng thái: ${error?.message ?: "Lỗi GeckoView"}"
                refreshInstalled()
            }
        )
    }

    fun update(extension: WebExtension) {
        if (isWorking) return
        isWorking = true
        statusMessage = "Đang kiểm tra cập nhật…"
        controller.update(extension).accept(
            { updated ->
                isWorking = false
                statusMessage = if (updated == null) {
                    "Tiện ích đã ở phiên bản mới nhất."
                } else {
                    "Đã cập nhật ${updated?.metaData?.name ?: updated?.id ?: extension.id}."
                }
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Cập nhật thất bại: ${error?.message ?: "Không thể kiểm tra bản mới"}"
            }
        )
    }

    fun uninstall(extension: WebExtension) {
        if (isWorking) return
        isWorking = true
        controller.uninstall(extension).accept(
            {
                isWorking = false
                statusMessage = "Đã gỡ ${extension.metaData.name ?: extension.id}."
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Không thể gỡ tiện ích: ${error?.message ?: "Lỗi GeckoView"}"
            }
        )
    }

    private companion object {
        const val AMO_SEARCH_URL = "https://addons.mozilla.org/api/v5/addons/search/"
    }
}
