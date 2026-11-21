package io.github.snowgemm.catbrowser.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

enum class UpdateChannel {
    STABLE,
    BETA,
}

data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val updateAvailable: Boolean = false,
    val version: String = "",
    val releaseNotes: String = "",
    val downloaded: Boolean = false,
    val message: String = "Chưa kiểm tra cập nhật.",
    val error: Boolean = false,
)

private data class PendingUpdate(
    val downloadUrl: String,
    val expectedSha256: String,
)

private const val MAX_RELEASE_NOTES_LENGTH = 16_000

internal fun limitReleaseNotesForDisplay(body: String): String {
    if (body.length <= MAX_RELEASE_NOTES_LENGTH) return body
    val prefix = body.take(MAX_RELEASE_NOTES_LENGTH)
    val end = prefix.lastIndexOf("\n").takeIf { it > 0 } ?: prefix.length
    return prefix.take(end).trimEnd() + "\n\n…"
}

class BrowserUpdateManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "CatBrowserUpdater").apply { isDaemon = true }
        }
    private val pendingUpdates = mutableMapOf<UpdateChannel, PendingUpdate>()
    private val checkGenerations = UpdateGenerationTracker()
    private val preferences = appContext.getSharedPreferences("cat_update_preferences", Context.MODE_PRIVATE)

    init {
        // A process death during download can leave a partial cache file behind.
        BrowserUpdateArtifacts.removePartialDownloads(appContext.cacheDir)
    }

    var betaEnabled by mutableStateOf(preferences.getBoolean(KEY_BETA_ENABLED, false))
        private set

    var stableState by mutableStateOf(UpdateUiState())
        private set
    var betaState by mutableStateOf(
        UpdateUiState(message = "Kiểm tra bản Beta (pre-release) khi cần thử tính năng mới."),
    )
        private set

    private fun state(channel: UpdateChannel): UpdateUiState = if (channel == UpdateChannel.STABLE) stableState else betaState

    private fun setState(
        channel: UpdateChannel,
        newState: UpdateUiState,
    ) {
        if (channel == UpdateChannel.STABLE) stableState = newState else betaState = newState
    }

    private fun updateState(
        channel: UpdateChannel,
        transform: (UpdateUiState) -> UpdateUiState,
    ) {
        setState(channel, transform(state(channel)))
    }

    fun checkForStartupUpdates() {
        checkForUpdates(UpdateChannel.STABLE)
        if (betaEnabled) checkForUpdates(UpdateChannel.BETA)
    }

    fun updateBetaEnabled(enabled: Boolean) {
        if (betaEnabled == enabled) return
        if (!enabled && betaState.isDownloading) return
        betaEnabled = enabled
        preferences.edit().putBoolean(KEY_BETA_ENABLED, enabled).apply()
        if (enabled) {
            checkForUpdates(UpdateChannel.BETA)
        } else {
            checkGenerations.invalidate(UpdateChannel.BETA)
            pendingUpdates.remove(UpdateChannel.BETA)
            File(appContext.cacheDir, fileName(UpdateChannel.BETA)).delete()
            File(appContext.cacheDir, fileName(UpdateChannel.BETA) + ".part").delete()
            betaState = UpdateUiState(message = "Beta đang tắt. Stable vẫn luôn được kiểm tra.")
        }
    }

    fun nextPromptChannel(): UpdateChannel? {
        val channels = if (betaEnabled) listOf(UpdateChannel.BETA, UpdateChannel.STABLE) else listOf(UpdateChannel.STABLE)
        return channels
            .filter { channel ->
                val current = state(channel)
                current.updateAvailable &&
                    current.version.isNotBlank() &&
                    preferences.getString(promptKey(channel), null) != current.version
            }.maxWithOrNull { first, second -> compareBrowserVersions(state(first).version, state(second).version) }
    }

    fun dismissUpdatePrompt(channel: UpdateChannel) {
        val version = state(channel).version
        if (version.isNotBlank()) preferences.edit().putString(promptKey(channel), version).apply()
    }

    private fun promptKey(channel: UpdateChannel): String = "dismissed_${channel.name.lowercase()}_version"

    fun checkForUpdates(channel: UpdateChannel) {
        val currentState = state(channel)
        if (currentState.isChecking || currentState.isDownloading) return
        val generation = checkGenerations.next(channel)

        updateState(channel) {
            it.copy(isChecking = true, error = false, message = "Đang kiểm tra " + channel.label + "…")
        }
        executor.execute {
            try {
                val release = fetchRelease(channel)
                if (release == null) {
                    postIfCurrent(channel, generation) {
                        pendingUpdates.remove(channel)
                        setState(
                            channel,
                            UpdateUiState(
                                message =
                                    if (channel == UpdateChannel.BETA) {
                                        "Chưa có bản Beta (pre-release) nào được phát hành."
                                    } else {
                                        "Chưa có bản Stable nào được phát hành."
                                    },
                            ),
                        )
                    }
                    return@execute
                }

                val tag = release.optString("tag_name").removePrefix("v")
                if (tag.isBlank()) throw IllegalStateException("Bản phát hành không có tag phiên bản.")

                val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                val currentVersion = packageInfo.versionName.orEmpty()
                val currentVersionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                val candidateVersionCode = browserVersionCodeFromTag(tag)
                val versionComparison = compareBrowserVersions(tag, currentVersion)
                val isNewer =
                    versionComparison > 0 ||
                        (
                            versionComparison == 0 &&
                                candidateVersionCode != null &&
                                candidateVersionCode > currentVersionCode
                        )
                if (!isNewer) {
                    postIfCurrent(channel, generation) {
                        pendingUpdates.remove(channel)
                        setState(
                            channel,
                            UpdateUiState(
                                message =
                                    when (channel) {
                                        UpdateChannel.STABLE -> "Đang dùng Stable mới nhất (" + currentVersion + ")."
                                        UpdateChannel.BETA -> "Không có bản Beta mới hơn phiên bản đang cài (" + currentVersion + ")."
                                    },
                            ),
                        )
                    }
                    return@execute
                }

                val abi =
                    Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }
                        ?: throw IllegalStateException("Thiết bị chưa có APK phù hợp.")
                val expectedName = "cat-browser-" + abi + "-release.apk"
                val assets =
                    release.optJSONArray("assets")
                        ?: throw IllegalStateException("Bản phát hành không có tệp APK.")
                var selected: JSONObject? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") == expectedName) {
                        selected = asset
                        break
                    }
                }

                val asset = selected ?: throw IllegalStateException("Không tìm thấy APK cho thiết bị này.")
                val assetUrl = asset.optString("browser_download_url")
                val digest = asset.optString("digest")
                val uri = Uri.parse(assetUrl)
                if (uri.scheme != "https" ||
                    uri.host != "github.com" ||
                    !uri.path.orEmpty().startsWith("/snowgemm/Cat-Browser/releases/download/")
                ) {
                    throw IllegalStateException("Đường dẫn cập nhật không hợp lệ.")
                }
                if (!digest.startsWith("sha256:") || digest.length != 71) {
                    throw IllegalStateException("Bản phát hành chưa có SHA-256 hợp lệ.")
                }

                val pendingUpdate =
                    PendingUpdate(
                        downloadUrl = assetUrl,
                        expectedSha256 = digest.removePrefix("sha256:").lowercase(),
                    )
                val notes = limitReleaseNotesForDisplay(release.optString("body"))
                postIfCurrent(channel, generation) {
                    pendingUpdates[channel] = pendingUpdate
                    setState(
                        channel,
                        UpdateUiState(
                            updateAvailable = true,
                            version = tag,
                            releaseNotes = notes,
                            message = "Có " + channel.label + " mới: " + tag,
                        ),
                    )
                }
            } catch (error: Exception) {
                postIfCurrent(channel, generation) {
                    pendingUpdates.remove(channel)
                    setState(
                        channel,
                        UpdateUiState(
                            message = error.message ?: "Không thể kiểm tra " + channel.label + ".",
                            error = true,
                        ),
                    )
                }
            }
        }
    }

    private fun fetchRelease(channel: UpdateChannel): JSONObject? {
        val url =
            when (channel) {
                UpdateChannel.STABLE -> LATEST_RELEASE_URL
                UpdateChannel.BETA -> RELEASES_URL
            }
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Cat-Browser-Android")
            }

        val body =
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("GitHub API trả về lỗi " + connection.responseCode + ".")
                }
                connection.inputStream.use(::readReleaseMetadata)
            } finally {
                connection.disconnect()
            }

        if (channel == UpdateChannel.STABLE) return JSONObject(body)

        val releases = JSONArray(body)
        var newest: JSONObject? = null
        for (index in 0 until releases.length()) {
            val candidate = releases.optJSONObject(index) ?: continue
            if (candidate.optBoolean("draft") || !candidate.optBoolean("prerelease")) continue
            if (newest == null ||
                compareBrowserVersions(
                    candidate.optString("tag_name"),
                    newest.optString("tag_name"),
                ) > 0
            ) {
                newest = candidate
            }
        }
        return newest
    }

    fun downloadUpdate(channel: UpdateChannel) {
        val update = pendingUpdates[channel] ?: return
        if (state(channel).isDownloading) return

        updateState(channel) {
            it.copy(
                isDownloading = true,
                progress = 0,
                error = false,
                message = "Đang tải " + channel.label + "…",
            )
        }
        executor.execute {
            val temporary = File(appContext.cacheDir, fileName(channel) + ".part")
            try {
                val connection =
                    (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Cat-Browser-Android")
                    }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Máy chủ tải xuống trả về lỗi " + connection.responseCode + ".")
                }

                val total = connection.contentLengthLong
                val sha = MessageDigest.getInstance("SHA-256")
                try {
                    connection.inputStream.buffered().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                sha.update(buffer, 0, count)
                                received += count
                                if (total > 0) {
                                    val percent = ((received * 100) / total).toInt().coerceIn(0, 99)
                                    post { updateState(channel) { it.copy(progress = percent) } }
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                val actual = sha.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(update.expectedSha256, ignoreCase = true)) {
                    throw SecurityException("SHA-256 không khớp. Tệp tải xuống đã bị từ chối.")
                }

                val destination = File(appContext.cacheDir, fileName(channel))
                if (destination.exists() && !destination.delete()) {
                    throw IllegalStateException("Không thể thay thế APK cũ trong bộ nhớ tạm.")
                }
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Không thể lưu APK đã tải.")
                }
                post {
                    updateState(channel) {
                        it.copy(
                            isDownloading = false,
                            progress = 100,
                            downloaded = true,
                            message = "Đã tải và xác minh APK " + state(channel).version + ". Sẵn sàng cài đặt.",
                        )
                    }
                }
            } catch (error: Exception) {
                temporary.delete()
                post {
                    updateState(channel) {
                        it.copy(
                            isDownloading = false,
                            error = true,
                            message = error.message ?: "Tải " + channel.label + " thất bại.",
                        )
                    }
                }
            }
        }
    }

    fun installDownloadedApk(channel: UpdateChannel): Boolean {
        val file = File(appContext.cacheDir, fileName(channel)).takeIf { it.isFile } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + appContext.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(settingsIntent)
            updateState(channel) {
                it.copy(message = "Hãy cho phép Cat Browser cài ứng dụng, sau đó quay lại và nhấn Cài đặt.")
            }
            return false
        }

        return try {
            val contentUri =
                FileProvider.getUriForFile(
                    appContext,
                    appContext.packageName + ".fileprovider",
                    file,
                )
            val installIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            appContext.startActivity(installIntent)
            true
        } catch (error: Exception) {
            updateState(channel) {
                it.copy(error = true, message = error.message ?: "Không thể mở trình cài đặt Android.")
            }
            false
        }
    }

    private fun fileName(channel: UpdateChannel): String = "cat-browser-" + channel.name.lowercase() + "-update.apk"

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun postIfCurrent(
        channel: UpdateChannel,
        generation: Long,
        action: () -> Unit,
    ) {
        post {
            if (checkGenerations.isCurrent(channel, generation)) action()
        }
    }

    private companion object {
        const val KEY_BETA_ENABLED = "beta_updates_enabled"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/snowgemm/Cat-Browser/releases/latest"
        const val RELEASES_URL =
            "https://api.github.com/repos/snowgemm/Cat-Browser/releases?per_page=100"
        val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }
}

private data class ParsedBrowserVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val prerelease: List<String>,
)

internal fun browserVersionCodeFromTag(value: String): Long? {
    val normalized = value.removePrefix("v")
    val match =
        Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$""")
            .matchEntire(normalized) ?: return null

    val major = match.groupValues[1].toLongOrNull() ?: return null
    val minor = match.groupValues[2].toLongOrNull() ?: return null
    val patch = match.groupValues[3].toLongOrNull() ?: return null
    if (major > 2099 || minor > 99 || patch > 99) return null

    val betaSequence = match.groupValues[4].takeIf { it.isNotBlank() }?.toLongOrNull()
    if (match.groupValues[4].isNotBlank() && (betaSequence == null || betaSequence !in 1L..98L)) {
        return null
    }

    val patchStage = betaSequence ?: 99L
    return major * 1_000_000L + minor * 10_000L + patch * 100L + patchStage
}

internal fun compareBrowserVersions(
    first: String,
    second: String,
): Int {
    val left = parseBrowserVersion(first) ?: return first.compareTo(second, ignoreCase = true)
    val right = parseBrowserVersion(second) ?: return first.compareTo(second, ignoreCase = true)

    compareValues(left.major, right.major).takeIf { it != 0 }?.let { return it }
    compareValues(left.minor, right.minor).takeIf { it != 0 }?.let { return it }
    compareValues(left.patch, right.patch).takeIf { it != 0 }?.let { return it }

    if (left.prerelease.isEmpty() && right.prerelease.isEmpty()) return 0
    if (left.prerelease.isEmpty()) return 1
    if (right.prerelease.isEmpty()) return -1

    for (index in 0 until maxOf(left.prerelease.size, right.prerelease.size)) {
        val a = left.prerelease.getOrNull(index) ?: return -1
        val b = right.prerelease.getOrNull(index) ?: return 1
        val aNumber = a.toLongOrNull()
        val bNumber = b.toLongOrNull()
        val comparison =
            when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
        if (comparison != 0) return comparison
    }
    return 0
}

private fun parseBrowserVersion(value: String): ParsedBrowserVersion? {
    val normalized = value.removePrefix("v")
    val match =
        Regex(
            """^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""",
        ).matchEntire(normalized) ?: return null

    val major = match.groupValues[1].toLongOrNull() ?: return null
    val minor = match.groupValues[2].toLongOrNull() ?: return null
    val patch = match.groupValues[3].toLongOrNull() ?: return null
    val prerelease = match.groupValues[4].takeIf { it.isNotBlank() }?.split(".") ?: emptyList()
    return ParsedBrowserVersion(major, minor, patch, prerelease)
}

private val UpdateChannel.label: String
    get() = if (this == UpdateChannel.STABLE) "Stable" else "Beta"

private const val MAX_RELEASE_METADATA_BYTES = 4 * 1024 * 1024

private fun readReleaseMetadata(input: java.io.InputStream): String =
    io.github.snowgemm.catbrowser.util.readUtf8Bounded(input, MAX_RELEASE_METADATA_BYTES)
