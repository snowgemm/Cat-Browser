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
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class ExtensionCatalogEntry(
    val id: String,
    val slug: String,
    val name: String,
    val summary: String,
    val version: String,
    val downloadUrl: String?,
    val listingUrl: String,
    val averageRating: Double,
    val dailyUsers: Int
) {
    val canInstall: Boolean
        get() = downloadUrl != null
}

data class ExtensionPermissionPrompt(
    val extensionId: String,
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

data class ExtensionSaveAsRequest(
    val id: String,
    val suggestedFileName: String
)

private data class PendingExtensionDownload(
    val extension: WebExtension,
    val request: WebExtension.DownloadRequest,
    val result: GeckoResult<WebExtension.DownloadInitData>
)

private data class PendingExtensionPermissionPrompt(
    val prompt: ExtensionPermissionPrompt,
    val decision: (Boolean, Boolean) -> Unit
)

/** Owns installed WebExtensions, browser actions and the Mozilla Add-ons catalog. */
class BrowserExtensionsManager(
    private val runtime: GeckoRuntime,
    private val context: Context,
    private val downloadManager: BrowserDownloadManager,
    private val sessionProvider: () -> List<GeckoSession>,
    private val onCreateTabFromExtension: (Boolean) -> GeckoSession?,
    private val onActivateSession: (GeckoSession) -> Unit,
    private val onCloseSession: (GeckoSession) -> Boolean,
    private val onOpenOptionsPage: (String) -> Unit,
    private val onRestoreBrowserFocus: () -> Unit
) {
    private val controller = runtime.webExtensionController
    private val webExecutor = GeckoWebExecutor(runtime)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val downloadIdPreferences = context.getSharedPreferences(
        "cat_extension_download_ids",
        Context.MODE_PRIVATE
    )
    private val downloadIdLock = Any()

    private val catalogSearchGeneration = AtomicInteger()
    @Volatile private var activeCatalogConnection: HttpURLConnection? = null

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
    var saveAsRequest by mutableStateOf<ExtensionSaveAsRequest?>(null)
        private set
    var isExtensionProcessSpawningDisabled by mutableStateOf(false)
        private set

    private val permissionPrompts = ExtensionPromptQueue<PendingExtensionPermissionPrompt>()
    private var refreshInstalledAgain = false
    private var pendingExtensionDownload: PendingExtensionDownload? = null
    private var isAppForegrounded = true

    init {
        controller.setAddonManagerDelegate(
            object : WebExtensionController.AddonManagerDelegate {
                override fun onInstalled(extension: WebExtension) {
                    attachExtension(extension)
                    sessionProvider().forEach(::attachSession)
                    refreshInstalled()
                }

                override fun onReady(extension: WebExtension) {
                    // GeckoView resolves extension-relative URLs by this point.
                    // Re-register delegates so optionsPageUrl and action metadata are current.
                    attachExtension(extension)
                    sessionProvider().forEach(::attachSession)
                    refreshInstalled()
                }

                override fun onEnabled(extension: WebExtension) {
                    attachExtension(extension)
                    sessionProvider().forEach(::attachSession)
                    refreshInstalled()
                }

                override fun onDisabled(extension: WebExtension) {
                    rejectPermissionPrompts(extension.id)
                    detachExtension(extension)
                    toolbarActions.removeAll { it.extensionId == extension.id }
                    if (popup?.extensionId == extension.id) closePopup()
                    refreshInstalled()
                }

                override fun onUninstalled(extension: WebExtension) {
                    rejectPermissionPrompts(extension.id)
                    detachExtension(extension)
                    toolbarActions.removeAll { it.extensionId == extension.id }
                    if (popup?.extensionId == extension.id) closePopup()
                    installed.removeAll { it.id == extension.id }
                    refreshInstalled()
                }

                override fun onOptionalPermissionsChanged(extension: WebExtension) {
                    if (extension.metaData.enabled) {
                        attachExtension(extension)
                        sessionProvider().forEach(::attachSession)
                    }
                    refreshInstalled()
                }

                override fun onInstallationFailed(
                    extension: WebExtension?,
                    installException: WebExtension.InstallException,
                ) {
                    val name = extension?.metaData?.name ?: extension?.id
                    statusMessage =
                        buildString {
                            append("Cài đặt tiện ích thất bại")
                            if (!name.isNullOrBlank()) append(" ($name)")
                            installException.message?.takeIf(String::isNotBlank)?.let {
                                append(": ")
                                append(it)
                            }
                        }
                }
            },
        )

        controller.setDebuggerDelegate(
            object : WebExtensionController.DebuggerDelegate {
                override fun onExtensionListUpdated() {
                    // Temporary extensions installed/removed with web-ext do not necessarily
                    // pass through the normal add-on manager UI callbacks.
                    refreshInstalled()
                }
            },
        )

        controller.setExtensionProcessDelegate(
            object : WebExtensionController.ExtensionProcessDelegate {
                override fun onDisabledProcessSpawning() {
                    isExtensionProcessSpawningDisabled = true
                    statusMessage =
                        "GeckoView đã tạm dừng tiến trình tiện ích sau nhiều lần lỗi. " +
                            "Bạn có thể thử khởi động lại tiến trình."
                }
            },
        )

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
        decision: (Boolean, Boolean) -> Unit,
    ) {
        val pending =
            PendingExtensionPermissionPrompt(
                prompt =
                    ExtensionPermissionPrompt(
                        extensionId = extension.id,
                        extensionName = extension.metaData.name?.takeIf(String::isNotBlank) ?: extension.id,
                        description = extension.metaData.description.orEmpty(),
                        permissions = permissions.toList(),
                        origins = origins.toList(),
                        dataCollectionPermissions = dataCollectionPermissions.toList(),
                        kind = kind,
                    ),
                decision = decision,
            )
        mainHandler.post {
            permissionPrompts.enqueue(pending)
            permissionPrompt = permissionPrompts.active?.prompt
        }
    }

    fun resolvePermissionPrompt(approved: Boolean, allowDataCollection: Boolean = false) {
        val active = permissionPrompts.active ?: return
        permissionPrompt = null
        val next = permissionPrompts.resolveActive()
        permissionPrompt = next?.prompt
        runCatching { active.decision(approved, allowDataCollection) }
            .onFailure { error ->
                statusMessage = "Không thể hoàn tất yêu cầu quyền: ${error.message ?: "lỗi không xác định"}"
            }
    }

    private fun rejectPermissionPrompts(extensionId: String) {
        mainHandler.post {
            val rejected = permissionPrompts.removeAll { it.prompt.extensionId == extensionId }
            permissionPrompt = permissionPrompts.active?.prompt
            rejected.forEach { pending ->
                runCatching { pending.decision(false, false) }
            }
        }
    }

    private fun attachExtension(extension: WebExtension) {
        extension.setActionDelegate(createActionDelegate(extension))
        extension.setDownloadDelegate(createDownloadDelegate())
        extension.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession>? {
                if (createDetails.pinned == true ||
                    createDetails.discarded == true ||
                    createDetails.openInReaderMode == true ||
                    createDetails.cookieStoreId != null ||
                    createDetails.index != null
                ) {
                    statusMessage = "Yêu cầu tạo tab này dùng tuỳ chọn mà Cat Browser chưa hỗ trợ."
                    return null
                }
                val session = onCreateTabFromExtension(createDetails.active != false) ?: return null
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

    private fun detachExtensionFromSessions(extension: WebExtension) {
        sessionProvider().forEach { session ->
            session.getWebExtensionController().setActionDelegate(extension, null)
            session.getWebExtensionController().setTabDelegate(extension, null)
        }
    }

    private fun detachExtension(extension: WebExtension) {
        detachExtensionFromSessions(extension)
        extension.setActionDelegate(null)
        extension.setTabDelegate(null)
        extension.setDownloadDelegate(null)
    }

    fun detachSession(session: GeckoSession) {
        if (session.isOpen) {
            installed.forEach { extension ->
                val extensionController = session.getWebExtensionController()
                extensionController.setActionDelegate(extension, null)
                extensionController.setTabDelegate(extension, null)
            }
        }

        // Session-specific actions can contain decoded icon bitmaps. Release them
        // as soon as the corresponding GeckoSession is no longer usable.
        val sessionIdentity = System.identityHashCode(session)
        toolbarActions.removeAll { it.sessionIdentity == sessionIdentity }
    }

    fun attachSession(session: GeckoSession) {
        installed.filter { it.metaData.enabled }.forEach { extension ->
            session.getWebExtensionController().setActionDelegate(
                extension,
                createActionDelegate(extension, session)
            )
            session.getWebExtensionController().setTabDelegate(
                extension,
                object : WebExtension.SessionTabDelegate {
                    override fun onUpdateTab(
                        extension: WebExtension,
                        session: GeckoSession,
                        details: WebExtension.UpdateTabDetails
                    ): GeckoResult<AllowOrDeny> {
                        if (details.pinned != null ||
                            details.muted != null ||
                            details.autoDiscardable != null
                        ) {
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        if (details.active == true || details.highlighted == true) {
                            onActivateSession(session)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }

                    override fun onCloseTab(
                        source: WebExtension?,
                        session: GeckoSession
                    ): GeckoResult<AllowOrDeny> {
                        val closed = onCloseSession(session)
                        return GeckoResult.fromValue(
                            if (closed) AllowOrDeny.ALLOW else AllowOrDeny.DENY
                        )
                    }
                }
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
        sessionProvider().filter { it.isOpen }.forEach { it.setFocused(false) }
        val session = GeckoSession()
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                closePopup(session)
            }
        }
        session.open(runtime)
        session.setActive(isAppForegrounded)
        session.setFocused(isAppForegrounded)
        session.setPriorityHint(
            if (isAppForegrounded) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT
        )
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
        current.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        if (current.session.isOpen) current.session.close()
        if (isAppForegrounded) onRestoreBrowserFocus()
    }

    fun onAppBackgrounded() {
        isAppForegrounded = false
        popup?.session?.takeIf { it.isOpen }?.let { session ->
            session.setFocused(false)
            session.setActive(false)
            session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        }
    }

    fun onAppForegrounded() {
        isAppForegrounded = true
        val popupSession = popup?.session ?: return
        if (!popupSession.isOpen) {
            closePopup(popupSession)
            return
        }
        sessionProvider().filter { it.isOpen }.forEach { it.setFocused(false) }
        popupSession.setActive(true)
        popupSession.setFocused(true)
        popupSession.setPriorityHint(GeckoSession.PRIORITY_HIGH)
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
        if (isLoadingInstalled) {
            refreshInstalledAgain = true
            return
        }
        isLoadingInstalled = true
        controller.list().accept(
            { extensions ->
                val available = extensions.orEmpty()
                installed.clear()
                installed.addAll(
                    available.sortedBy {
                        it.metaData.name?.lowercase().orEmpty().ifBlank { it.id.lowercase() }
                    },
                )
                val enabledIds = available.filter { it.metaData.enabled }.mapTo(mutableSetOf()) { it.id }
                toolbarActions.removeAll { it.extensionId !in enabledIds }
                if (popup?.extensionId?.let { it !in enabledIds } == true) closePopup()
                available.filter { it.metaData.enabled }.forEach(::attachExtension)
                sessionProvider().forEach(::attachSession)
                finishInstalledRefresh()
            },
            { error ->
                statusMessage = "Không thể đọc danh sách tiện ích: ${error?.message ?: "Lỗi GeckoView"}"
                finishInstalledRefresh()
            },
        )
    }

    private fun finishInstalledRefresh() {
        isLoadingInstalled = false
        if (refreshInstalledAgain) {
            refreshInstalledAgain = false
            refreshInstalled()
        }
    }

    fun retryExtensionProcessSpawning() {
        controller.enableExtensionProcessSpawning()
        isExtensionProcessSpawningDisabled = false
        statusMessage = "Đã yêu cầu GeckoView khởi động lại tiến trình tiện ích."
        refreshInstalled()
    }

    fun searchCatalog(query: String) {
        val generation = catalogSearchGeneration.incrementAndGet()
        activeCatalogConnection?.disconnect()
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
                    .appendQueryParameter("lang", "vi")
                query.trim().take(100).takeIf(String::isNotBlank)?.let {
                    builder.appendQueryParameter("q", it)
                }

                connection = URL(builder.build().toString())
                    .openConnection() as HttpURLConnection
                activeCatalogConnection = connection
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
                val payload = responseStream?.use(::readAmoResponse).orEmpty()
                if (responseCode !in 200..299) {
                    val detail = runCatching {
                        JSONObject(payload).optString("detail")
                    }.getOrNull().orEmpty()
                    throw IllegalStateException(
                        detail.ifBlank { "Mozilla Add-ons trả về HTTP $responseCode." }
                    )
                }

                val entries = ExtensionCatalogParser.parse(payload)
                mainHandler.post {
                    if (catalogSearchGeneration.get() != generation) return@post
                    catalog.clear()
                    catalog.addAll(entries)
                    isSearching = false
                    statusMessage = if (entries.isEmpty()) {
                        "Mozilla Add-ons không có kết quả phù hợp. Bạn vẫn có thể mở kho Mozilla để duyệt trực tiếp."
                    } else {
                        null
                    }
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf(String::isNotBlank) ?: "Lỗi kết nối"
                mainHandler.post {
                    if (catalogSearchGeneration.get() != generation) return@post
                    isSearching = false
                    statusMessage = "Không thể tải kho Mozilla Add-ons: $message"
                }
            } finally {
                connection?.disconnect()
                if (activeCatalogConnection === connection) activeCatalogConnection = null
            }
        }
    }

    private fun nextDownloadId(): Int = synchronized(downloadIdLock) {
        val previous = downloadIdPreferences.getInt(KEY_NEXT_DOWNLOAD_ID, 0).coerceAtLeast(0)
        val next = if (previous >= Int.MAX_VALUE - 1) 1 else previous + 1
        downloadIdPreferences.edit().putInt(KEY_NEXT_DOWNLOAD_ID, next).apply()
        next
    }

    private fun createDownloadDelegate(): WebExtension.DownloadDelegate =
        object : WebExtension.DownloadDelegate {
            override fun onDownload(
                source: WebExtension,
                request: WebExtension.DownloadRequest
            ): GeckoResult<WebExtension.DownloadInitData>? {
                val result = GeckoResult<WebExtension.DownloadInitData>()
                mainHandler.post {
                    if (
                        request.saveAs ||
                        request.conflictActionFlag == WebExtension.DownloadRequest.CONFLICT_ACTION_PROMPT
                    ) {
                        if (pendingExtensionDownload != null) {
                            result.completeExceptionally(
                                IllegalStateException("Một yêu cầu chọn vị trí lưu khác đang chờ xử lý.")
                            )
                            return@post
                        }
                        val suggestedName = sanitizeDownloadFileName(
                            request.filename?.substringAfterLast('/')?.takeIf(String::isNotBlank)
                                ?: "download"
                        )
                        pendingExtensionDownload = PendingExtensionDownload(source, request, result)
                        saveAsRequest = ExtensionSaveAsRequest(
                            id = UUID.randomUUID().toString(),
                            suggestedFileName = suggestedName
                        )
                        statusMessage = if (request.saveAs) {
                            "Chọn vị trí lưu cho " + (source.metaData.name ?: source.id) + "."
                        } else {
                            "Chọn vị trí lưu để xử lý tên tệp có thể bị trùng."
                        }
                    } else {
                        startExtensionDownload(source, request, result, destinationUri = null)
                    }
                }
                return result
            }
        }

    fun resolveSaveAsRequest(uri: Uri?) {
        val pending = pendingExtensionDownload ?: return
        pendingExtensionDownload = null
        saveAsRequest = null
        if (uri == null) {
            pending.result.completeExceptionally(
                IllegalStateException("Người dùng đã hủy chọn nơi lưu.")
            )
            statusMessage = "Đã hủy tải xuống theo yêu cầu của tiện ích."
            return
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        startExtensionDownload(pending.extension, pending.request, pending.result, uri)
    }

    private fun startExtensionDownload(
        source: WebExtension,
        request: WebExtension.DownloadRequest,
        result: GeckoResult<WebExtension.DownloadInitData>,
        destinationUri: Uri?
    ) {
        val download = runCatching {
            controller.createDownload(nextDownloadId())
        }.getOrElse { error ->
            result.completeExceptionally(error)
            return
        }
        if (download == null) {
            result.completeExceptionally(
                IllegalStateException("GeckoView không thể tạo tác vụ tải xuống.")
            )
            return
        }

        webExecutor.fetch(request.request, request.downloadFlags).accept(
            { response ->
                mainHandler.post {
                    if (response == null) {
                        result.completeExceptionally(
                            IllegalStateException("GeckoView không trả về phản hồi tải xuống.")
                        )
                        return@post
                    }
                    if (!request.allowHttpErrors && response.statusCode >= 400) {
                        runCatching { response.body?.close() }
                        result.completeExceptionally(
                            IllegalStateException("Máy chủ trả về HTTP " + response.statusCode + ".")
                        )
                        return@post
                    }

                    val privateMode =
                        (request.downloadFlags and GeckoWebExecutor.FETCH_FLAGS_PRIVATE) != 0
                    val item = downloadManager.enqueue(
                        response = response,
                        privateMode = privateMode,
                        requestedFileName = request.filename,
                        destinationUri = destinationUri,
                        conflictActionFlag = request.conflictActionFlag,
                        extensionId = source.id,
                        extensionDownloadId = download.id,
                        referrer = request.request.referrer
                    ) { changed ->
                        download.update(
                            extensionDownloadInfo(changed, request.request.referrer)
                        )
                    }

                    if (item == null) {
                        runCatching { response.body?.close() }
                        result.completeExceptionally(
                            IllegalStateException("Không nhận được dữ liệu tải xuống.")
                        )
                        return@post
                    }

                    result.complete(
                        WebExtension.DownloadInitData(
                            download,
                            extensionDownloadInfo(item, request.request.referrer)
                        )
                    )
                }
            },
            { error ->
                mainHandler.post {
                    result.completeExceptionally(
                        error ?: IllegalStateException("Không thể lấy dữ liệu tải xuống.")
                    )
                }
            }
        )
    }

    private fun extensionDownloadInfo(
        item: BrowserDownloadItem,
        referrer: String?
    ): WebExtension.Download.Info {
        val isTerminal = item.status == BrowserDownloadStatus.COMPLETE ||
            item.status == BrowserDownloadStatus.FAILED ||
            item.status == BrowserDownloadStatus.CANCELLED

        return object : WebExtension.Download.Info {
            override fun bytesReceived(): Long = item.receivedBytes
            override fun canResume(): Boolean = false
            override fun endTime(): Long? = if (isTerminal) System.currentTimeMillis() else null
            override fun error(): Int? = when (item.status) {
                BrowserDownloadStatus.FAILED -> WebExtension.Download.INTERRUPT_REASON_NETWORK_FAILED
                BrowserDownloadStatus.CANCELLED -> WebExtension.Download.INTERRUPT_REASON_USER_CANCELED
                else -> null
            }
            override fun fileExists(): Boolean =
                item.status == BrowserDownloadStatus.COMPLETE &&
                    (item.outputFile?.isFile == true || item.outputUri != null)
            override fun filename(): String = item.fileName
            override fun fileSize(): Long =
                if (item.status == BrowserDownloadStatus.COMPLETE) item.receivedBytes else -1L
            override fun mime(): String = item.mimeType
            override fun paused(): Boolean = false
            override fun referrer(): String = referrer.orEmpty()
            override fun startTime(): Long = item.createdAt
            override fun state(): Int = when (item.status) {
                BrowserDownloadStatus.COMPLETE -> WebExtension.Download.STATE_COMPLETE
                BrowserDownloadStatus.FAILED, BrowserDownloadStatus.CANCELLED ->
                    WebExtension.Download.STATE_INTERRUPTED
                else -> WebExtension.Download.STATE_IN_PROGRESS
            }
            override fun totalBytes(): Long = item.totalBytes
        }
    }


    fun isInstalled(entry: ExtensionCatalogEntry): Boolean =
        installed.any {
            it.metaData.amoListingUrl == entry.listingUrl ||
                (entry.downloadUrl != null && it.metaData.downloadUrl == entry.downloadUrl)
        }

    fun install(entry: ExtensionCatalogEntry) {
        val downloadUrl = entry.downloadUrl
        if (downloadUrl == null || !ExtensionSourcePolicy.isTrustedAmoDownloadUrl(downloadUrl)) {
            statusMessage = "Tiện ích này chưa có gói XPI Mozilla đã xác minh để cài đặt."
            return
        }
        if (isWorking) return

        isWorking = true
        statusMessage = "Đang xác minh và cài ${entry.name}…"
        controller.install(
            downloadUrl,
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
                    "Đã cập nhật ${updated.metaData.name ?: updated.id}."
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
        const val KEY_NEXT_DOWNLOAD_ID = "next_download_id"
    }
}

private const val MAX_AMO_RESPONSE_BYTES = 2 * 1024 * 1024

private fun readAmoResponse(input: java.io.InputStream): String =
    io.github.snowgemm.catbrowser.util
        .readUtf8Bounded(input, MAX_AMO_RESPONSE_BYTES)
