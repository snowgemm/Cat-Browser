package io.github.snowgemm.catbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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

data class ExtensionToolbarAction(
    val extensionId: String,
    val extensionName: String,
    val title: String,
    val isPageAction: Boolean,
    val enabled: Boolean,
    val badgeText: String?,
    val badgeBackgroundColor: Int?,
    val iconBitmap: Bitmap?,
    val sessionIdentity: Int?,
    val action: WebExtension.Action
)

data class ExtensionPopupState(
    val extensionId: String,
    val extensionName: String,
    val session: GeckoSession
)

/** Owns installed WebExtensions, browser actions and the Mozilla Add-ons catalog. */
class BrowserExtensionsManager(
    private val runtime: GeckoRuntime,
    private val context: Context,
    private val sessionProvider: () -> List<GeckoSession>,
    private val onCreateTabFromExtension: () -> GeckoSession?,
    private val onOpenOptionsPage: (String) -> Unit
) {
    private val controller = runtime.webExtensionController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()

    val installed = mutableStateListOf<WebExtension>()
    val catalog = mutableStateListOf<ExtensionCatalogEntry>()
    val toolbarActions = mutableStateListOf<ExtensionToolbarAction>()

    var popup by mutableStateOf<ExtensionPopupState?>(null)
        private set

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
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onReady(extension: WebExtension) {
                attachExtension(extension)
                sessionProvider().forEach(::attachSession)
            }

            override fun onEnabled(extension: WebExtension) {
                attachExtension(extension)
                sessionProvider().forEach(::attachSession)
            }

            override fun onDisabled(extension: WebExtension) {
                toolbarActions.removeAll { it.extensionId == extension.id }
                if (popup?.extensionId == extension.id) closePopup()
            }

            override fun onUninstalled(extension: WebExtension) {
                toolbarActions.removeAll { it.extensionId == extension.id }
                if (popup?.extensionId == extension.id) closePopup()
            }
        })

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

    private fun attachExtension(extension: WebExtension) {
        extension.setActionDelegate(createActionDelegate(extension))
        extension.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession>? {
                val session = onCreateTabFromExtension() ?: return null
                return GeckoResult.fromValue(session)
            }

            override fun onOpenOptionsPage(source: WebExtension) {
                val optionsUrl = source.metaData.optionsPageUrl.orEmpty()
                val optionsUri = Uri.parse(optionsUrl)
                val baseUri = Uri.parse(source.metaData.baseUrl)
                if (optionsUri.scheme == "moz-extension" &&
                    optionsUri.host != null &&
                    optionsUri.host == baseUri.host
                ) {
                    onOpenOptionsPage(optionsUrl)
                } else {
                    statusMessage = "Trang tuỳ chọn chưa sẵn sàng hoặc không có trong tiện ích này."
                }
            }
        })
    }

    fun attachSession(session: GeckoSession) {
        installed.filter { it.metaData.enabled }.forEach { extension ->
            session.getWebExtensionController().setActionDelegate(
                extension,
                createActionDelegate(extension, session)
            )
        }
    }

    private fun createActionDelegate(
        extension: WebExtension,
        boundSession: GeckoSession? = null
    ): WebExtension.ActionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action
        ) {
            updateAction(extension, session ?: boundSession, action, isPageAction = false)
        }

        override fun onPageAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action
        ) {
            updateAction(extension, session ?: boundSession, action, isPageAction = true)
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? = openPopup(extension)

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            if (popup?.extensionId == extension.id) {
                closePopup()
                return null
            }
            return openPopup(extension)
        }
    }

    private fun updateAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action,
        isPageAction: Boolean
    ) {
        val identity = session?.let { System.identityHashCode(it) }
        toolbarActions.removeAll {
            it.extensionId == extension.id &&
                it.isPageAction == isPageAction &&
                it.sessionIdentity == identity
        }
        toolbarActions.add(
            ExtensionToolbarAction(
                extensionId = extension.id,
                extensionName = extension.metaData.name ?: extension.id,
                title = action.title?.takeIf(String::isNotBlank)
                    ?: extension.metaData.name ?: extension.id,
                isPageAction = isPageAction,
                enabled = action.enabled ?: !isPageAction,
                badgeText = action.badgeText,
                badgeBackgroundColor = action.badgeBackgroundColor,
                iconBitmap = null,
                sessionIdentity = identity,
                action = action
            )
        )
        action.icon?.getBitmap(64)?.accept(
            { bitmap ->
                mainHandler.post {
                    val currentIndex = toolbarActions.indexOfFirst {
                        it.extensionId == extension.id &&
                            it.isPageAction == isPageAction &&
                            it.sessionIdentity == identity &&
                            it.action === action
                    }
                    if (currentIndex >= 0) {
                        toolbarActions[currentIndex] = toolbarActions[currentIndex].copy(iconBitmap = bitmap)
                    }
                }
            },
            { /* Keep the extension's monogram fallback if its icon cannot be decoded. */ }
        )
    }

    fun actionsFor(session: GeckoSession?): List<ExtensionToolbarAction> {
        val sessionIdentity = session?.let { System.identityHashCode(it) }
        val defaults = toolbarActions.filter { it.sessionIdentity == null }
        val overrides = toolbarActions.filter {
            sessionIdentity != null && it.sessionIdentity == sessionIdentity
        }
        val merged = defaults.map { default ->
            val override = overrides.firstOrNull {
                it.extensionId == default.extensionId &&
                    it.isPageAction == default.isPageAction
            }
            if (override == null) default else override.copy(
                title = override.title.ifBlank { default.title },
                iconBitmap = override.iconBitmap ?: default.iconBitmap,
                action = override.action.withDefault(default.action)
            )
        }
        return (merged + overrides.filter { override ->
            defaults.none {
                it.extensionId == override.extensionId &&
                    it.isPageAction == override.isPageAction
            }
        }).filter { it.enabled }.sortedBy { it.extensionName.lowercase() }
    }

    private fun openPopup(extension: WebExtension): GeckoResult<GeckoSession>? {
        if (popup != null) closePopup()
        val session = GeckoSession()
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                closePopup(session)
            }
        }
        session.open(runtime)
        session.setActive(true)
        session.setFocused(true)
        popup = ExtensionPopupState(
            extensionId = extension.id,
            extensionName = extension.metaData.name ?: extension.id,
            session = session
        )
        return GeckoResult.fromValue(session)
    }

    fun closePopup(expectedSession: GeckoSession? = null) {
        val current = popup ?: return
        if (expectedSession != null && current.session !== expectedSession) return
        popup = null
        current.session.contentDelegate = null
        current.session.setFocused(false)
        current.session.setActive(false)
        if (current.session.isOpen) current.session.close()
    }

    fun setAllowedInPrivateBrowsing(extension: WebExtension, allowed: Boolean) {
        if (isWorking) return
        isWorking = true
        controller.setAllowedInPrivateBrowsing(extension, allowed).accept(
            { updated ->
                isWorking = false
                statusMessage = if (allowed) {
                    "Đã cho phép ${updated?.metaData?.name ?: extension.id} chạy trong tab ẩn danh."
                } else {
                    "Đã tắt quyền chạy trong tab ẩn danh."
                }
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Không thể đổi quyền ẩn danh: ${error?.message ?: "GeckoView từ chối yêu cầu"}"
            }
        )
    }

    fun refreshInstalled() {
        if (isLoadingInstalled) return
        isLoadingInstalled = true
        controller.list().accept(
            { extensions ->
                val available = extensions.orEmpty()
                installed.clear()
                installed.addAll(
                    available.sortedBy {
                        it.metaData.name?.lowercase().orEmpty().ifBlank { it.id.lowercase() }
                    }
                )
                val enabledIds = available.filter { it.metaData.enabled }.mapTo(mutableSetOf()) { it.id }
                toolbarActions.removeAll { it.extensionId !in enabledIds }
                if (popup?.extensionId?.let { it !in enabledIds } == true) closePopup()
                available.filter { it.metaData.enabled }.forEach(::attachExtension)
                sessionProvider().forEach(::attachSession)
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
                    .appendQueryParameter("lang", "en-US")
                query.trim().take(100).takeIf(String::isNotBlank)?.let {
                    builder.appendQueryParameter("q", it)
                }

                connection = URL(builder.build().toString())
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "CatBrowser-Android")

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val payload = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (responseCode !in 200..299) {
                    val detail = runCatching {
                        JSONObject(payload).optString("detail")
                    }.getOrNull().orEmpty()
                    throw IllegalStateException(
                        detail.ifBlank { "Mozilla Add-ons trả về HTTP $responseCode." }
                    )
                }

                val response = JSONObject(payload)
                val results = response.optJSONArray("results")
                    ?: throw IllegalStateException("Phản hồi Mozilla không chứa danh sách tiện ích.")

                val entries = buildList {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        if (item.optString("status") != "public") continue

                        val version = item.optJSONObject("current_version") ?: continue
                        // AMO v5 exposes the approved XPI as current_version.file.
                        // Accept the legacy files array as a defensive fallback.
                        val file = version.optJSONObject("file")
                            ?: version.optJSONArray("files")?.let { files ->
                                (0 until files.length())
                                    .mapNotNull(files::optJSONObject)
                                    .firstOrNull()
                            }
                            ?: continue

                        val downloadUrl = file.optString("url")
                        val downloadUri = Uri.parse(downloadUrl)
                        if (downloadUri.scheme != "https" ||
                            downloadUri.host != "addons.mozilla.org" ||
                            downloadUrl.isBlank()
                        ) {
                            continue
                        }

                        // GeckoView only installs Mozilla-signed WebExtensions.
                        // If AMO explicitly says a file is unsigned, don't offer it.
                        if (file.has("is_mozilla_signed_extension") &&
                            !file.optBoolean("is_mozilla_signed_extension")
                        ) {
                            continue
                        }

                        val slug = item.optString("slug")
                        if (slug.isBlank()) continue

                        val locale = item.optString("default_locale", "en-US")
                        val ratings = item.optJSONObject("ratings")
                        add(
                            ExtensionCatalogEntry(
                                id = item.optString("id", slug),
                                slug = slug,
                                name = localizedField(item, "name", locale).ifBlank { slug },
                                summary = localizedField(item, "summary", locale),
                                version = version.optString("version"),
                                downloadUrl = downloadUrl,
                                listingUrl = "https://addons.mozilla.org/android/addon/$slug/",
                                averageRating = ratings?.optDouble("average", 0.0) ?: 0.0,
                                dailyUsers = item.optInt("average_daily_users", 0)
                            )
                        )
                    }
                }

                mainHandler.post {
                    catalog.clear()
                    catalog.addAll(entries)
                    isSearching = false
                    statusMessage = if (entries.isEmpty()) {
                        "Mozilla Add-ons hiện không trả về tiện ích công khai phù hợp với Android."
                    } else {
                        null
                    }
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf(String::isNotBlank) ?: "Lỗi kết nối"
                mainHandler.post {
                    isSearching = false
                    statusMessage = "Không thể tải kho Mozilla Add-ons: $message"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun localizedField(item: JSONObject, field: String, fallbackLocale: String): String {
        val value = item.opt(field) ?: return ""
        if (value is String) return value
        if (value !is JSONObject) return ""

        return value.optString("en-US")
            .ifBlank { value.optString(fallbackLocale) }
            .ifBlank {
                val keys = value.keys()
                var translated = ""
                while (keys.hasNext() && translated.isBlank()) {
                    translated = value.optString(keys.next())
                }
                translated
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
                statusMessage = "Đã cài ${extension?.metaData?.name ?: entry.name}."
                refreshInstalled()
            },
            { error ->
                isWorking = false
                statusMessage = "Cài đặt thất bại: ${error?.message ?: "Extension không hợp lệ hoặc không được hỗ trợ"}"
            }
        )
    }

    fun installFromFile(contentUri: Uri) {
        if (isWorking) return
        isWorking = true
        statusMessage = "Đang đọc gói XPI…"

        networkExecutor.execute {
            val target = File(context.cacheDir, "extension-import-${UUID.randomUUID()}.xpi")
            try {
                val displayName = context.contentResolver.query(
                    contentUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty()
                if (!displayName.endsWith(".xpi", ignoreCase = true)) {
                    throw IllegalArgumentException("Chỉ chấp nhận tệp .xpi.")
                }

                val input = context.contentResolver.openInputStream(contentUri)
                    ?: throw IllegalStateException("Không thể đọc tệp đã chọn.")
                var copied = 0L
                input.use { source ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_XPI_SIZE) {
                                throw IllegalArgumentException("Gói XPI vượt quá giới hạn 50 MB.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (copied == 0L) throw IllegalArgumentException("Tệp XPI trống.")

                mainHandler.post {
                    statusMessage = "Đang xác minh chữ ký và cài tiện ích…"
                    controller.install(
                        Uri.fromFile(target).toString(),
                        WebExtensionController.INSTALLATION_METHOD_FROM_FILE
                    ).accept(
                        { extension ->
                            target.delete()
                            isWorking = false
                            statusMessage = "Đã cài ${extension?.metaData?.name ?: displayName}."
                            extension?.let(::attachExtension)
                            refreshInstalled()
                        },
                        { error ->
                            target.delete()
                            isWorking = false
                            statusMessage = "Không thể cài XPI: ${error?.message ?: "Gói không hợp lệ hoặc chữ ký không được chấp nhận"}"
                        }
                    )
                }
            } catch (error: Exception) {
                target.delete()
                mainHandler.post {
                    isWorking = false
                    statusMessage = error.message?.takeIf(String::isNotBlank)
                        ?: "Không thể đọc gói XPI."
                }
            }
        }
    }

    fun reportStatus(message: String) {
        statusMessage = message
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
        const val MAX_XPI_SIZE = 50L * 1024L * 1024L
    }
}
