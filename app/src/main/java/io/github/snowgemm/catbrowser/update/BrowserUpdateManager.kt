package io.github.snowgemm.catbrowser.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val updateAvailable: Boolean = false,
    val version: String = "",
    val releaseNotes: String = "",
    val downloaded: Boolean = false,
    val message: String = "Chưa kiểm tra cập nhật.",
    val error: Boolean = false
)

class BrowserUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var downloadUrl: String? = null
    private var expectedSha256: String? = null
    private var apkFile: File? = null

    var state by mutableStateOf(UpdateUiState())
        private set

    fun checkForUpdates() {
        if (state.isChecking || state.isDownloading) return
        state = state.copy(isChecking = true, error = false, message = "Đang kiểm tra phiên bản mới…")
        executor.execute {
            try {
                val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "Cat-Browser-Android")
                }
                val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                connection.disconnect()

                val tag = release.optString("tag_name").removePrefix("v")
                val current = appContext.packageManager
                    .getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
                val available = compareVersions(tag, current) > 0
                if (!available) {
                    post { state = UpdateUiState(message = "Cat Browser đã ở phiên bản mới nhất ($current).") }
                    return@execute
                }

                val abi = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }
                    ?: throw IllegalStateException("Thiết bị chưa có APK phù hợp.")
                val suffix = when (abi) {
                    "arm64-v8a" -> "arm64-v8a"
                    "armeabi-v7a" -> "armeabi-v7a"
                    "x86_64" -> "x86_64"
                    else -> throw IllegalStateException("Kiến trúc thiết bị không được hỗ trợ.")
                }
                val expectedName = "cat-browser-$suffix-release.apk"
                val assets = release.optJSONArray("assets")
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
                if (uri.scheme != "https" || uri.host != "github.com" ||
                    !uri.path.orEmpty().startsWith("/snowgemm/Cat-Browser/releases/download/")) {
                    throw IllegalStateException("Đường dẫn cập nhật không hợp lệ.")
                }
                if (!digest.startsWith("sha256:") || digest.length != 71) {
                    throw IllegalStateException("Bản phát hành chưa có SHA-256 hợp lệ.")
                }

                downloadUrl = assetUrl
                expectedSha256 = digest.removePrefix("sha256:").lowercase()
                val notes = release.optString("body").take(1200)
                post {
                    state = UpdateUiState(
                        updateAvailable = true,
                        version = tag,
                        releaseNotes = notes,
                        message = "Có phiên bản mới: $tag"
                    )
                }
            } catch (error: Exception) {
                post {
                    state = UpdateUiState(
                        message = error.message ?: "Không thể kiểm tra cập nhật.",
                        error = true
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val url = downloadUrl ?: return
        val digest = expectedSha256 ?: return
        if (state.isDownloading) return
        state = state.copy(isDownloading = true, progress = 0, error = false, message = "Đang tải bản cập nhật…")
        executor.execute {
            val temporary = File(appContext.cacheDir, "cat-browser-update.apk.part")
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Cat-Browser-Android")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Máy chủ tải xuống trả về lỗi ${connection.responseCode}.")
                }
                val total = connection.contentLengthLong
                val sha = MessageDigest.getInstance("SHA-256")
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
                                post { state = state.copy(progress = percent) }
                            }
                        }
                    }
                }
                connection.disconnect()
                val actual = sha.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(digest, ignoreCase = true)) {
                    throw SecurityException("SHA-256 không khớp. Tệp tải xuống đã bị từ chối.")
                }
                val destination = File(appContext.cacheDir, "cat-browser-update.apk")
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Không thể lưu APK đã tải.")
                }
                apkFile = destination
                post {
                    state = state.copy(
                        isDownloading = false,
                        progress = 100,
                        downloaded = true,
                        message = "Đã tải và xác minh APK. Sẵn sàng cài đặt."
                    )
                }
            } catch (error: Exception) {
                temporary.delete()
                post {
                    state = state.copy(
                        isDownloading = false,
                        error = true,
                        message = error.message ?: "Tải cập nhật thất bại."
                    )
                }
            }
        }
    }

    fun installDownloadedApk(): Boolean {
        val file = apkFile?.takeIf { it.isFile } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(settingsIntent)
            state = state.copy(message = "Hãy cho phép Cat Browser cài ứng dụng, sau đó quay lại và nhấn Cài đặt.")
            return false
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(installIntent)
            true
        } catch (error: Exception) {
            state = state.copy(error = true, message = error.message ?: "Không thể mở trình cài đặt Android.")
            false
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        val left = first.split(".").map { it.toIntOrNull() ?: 0 }
        val right = second.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun post(action: () -> Unit) = mainHandler.post { action() }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/snowgemm/Cat-Browser/releases/latest"
        val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }
}
