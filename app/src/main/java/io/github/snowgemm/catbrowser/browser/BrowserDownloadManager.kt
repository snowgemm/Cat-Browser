package io.github.snowgemm.catbrowser.browser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

enum class BrowserDownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETE,
    FAILED,
    CANCELLED
}

class BrowserDownloadItem internal constructor(
    val id: String,
    val fileName: String,
    val sourceUrl: String,
    val mimeType: String,
    val totalBytes: Long,
    val isPrivate: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val extensionId: String? = null,
    val extensionDownloadId: Int? = null,
    val referrer: String? = null
) {
    var status by mutableStateOf(BrowserDownloadStatus.QUEUED)
        internal set
    var receivedBytes by mutableStateOf(0L)
        internal set
    var errorMessage by mutableStateOf<String?>(null)
        internal set
    var localUri by mutableStateOf<String?>(null)
        internal set
    @Volatile
    var isCancelled = false
        internal set

    @Volatile
    internal var inputStream: InputStream? = null

    @Volatile
    internal var outputUri: Uri? = null

    @Volatile
    internal var outputFile: File? = null
}

/**
 * Receives GeckoView's original response stream so cookies, authorization headers,
 * and non-GET response bodies are not lost by re-fetching a download URL.
 *
 * The task list is intentionally in-memory. A download made from a private tab is
 * still an explicit user-saved file, but its source URL is not persisted as history.
 */
class BrowserDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val executor = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "CatBrowserDownload").apply { isDaemon = true }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences("cat_download_history", Context.MODE_PRIVATE)
    private var lastPersistedAt = 0L

    val downloads = mutableStateListOf<BrowserDownloadItem>()

    init {
        restorePersistedDownloads()
    }

    fun enqueue(
        response: WebResponse,
        privateMode: Boolean,
        requestedFileName: String? = null,
        destinationUri: Uri? = null,
        extensionId: String? = null,
        extensionDownloadId: Int? = null,
        referrer: String? = null,
        onChanged: ((BrowserDownloadItem) -> Unit)? = null
    ): BrowserDownloadItem? {
        val body = response.body ?: return null

        val mimeType = response.headers.entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"

        val contentLength = response.headers.entries
            .firstOrNull { it.key.equals("content-length", ignoreCase = true) }
            ?.value
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: -1L

        val fileName = requestedFileName
            ?.takeIf(String::isNotBlank)
            ?.let(::sanitizeDownloadFileName)
            ?: resolveFileName(response)
        val item = BrowserDownloadItem(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            sourceUrl = response.uri,
            mimeType = mimeType,
            totalBytes = contentLength,
            isPrivate = privateMode,
            extensionId = extensionId,
            extensionDownloadId = extensionDownloadId,
            referrer = referrer
        )
        item.outputUri = destinationUri
        downloads.add(0, item)
        persistDownloads(force = true)

        executor.execute {
            copyResponse(item, body, onChanged)
        }
        return item
    }

    fun cancel(item: BrowserDownloadItem) {
        if (item.status == BrowserDownloadStatus.COMPLETE ||
            item.status == BrowserDownloadStatus.FAILED ||
            item.status == BrowserDownloadStatus.CANCELLED
        ) return

        item.isCancelled = true
        runCatching { item.inputStream?.close() }
        mainHandler.post {
            item.status = BrowserDownloadStatus.CANCELLED
            item.errorMessage = "Đã hủy tải xuống."
            persistDownloads(force = true)
        }
    }

    fun remove(item: BrowserDownloadItem, deleteFile: Boolean = false) {
        if (item.status == BrowserDownloadStatus.DOWNLOADING ||
            item.status == BrowserDownloadStatus.QUEUED
        ) {
            cancel(item)
        }
        if (deleteFile) deleteOutput(item)
        downloads.removeAll { it.id == item.id }
        persistDownloads(force = true)
    }

    fun open(item: BrowserDownloadItem): Boolean {
        val uri = outputContentUri(item) ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun share(item: BrowserDownloadItem): Boolean {
        val uri = outputContentUri(item) ?: return false
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(resolver, item.fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(
                Intent.createChooser(sendIntent, "Chia sẻ tệp")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun clearCompleted(deleteFiles: Boolean) {
        val completed = downloads.filter {
            it.status == BrowserDownloadStatus.COMPLETE ||
                it.status == BrowserDownloadStatus.FAILED ||
                it.status == BrowserDownloadStatus.CANCELLED
        }
        completed.forEach { remove(it, deleteFile = deleteFiles) }
    }

    private fun copyResponse(
        item: BrowserDownloadItem,
        input: InputStream,
        onChanged: ((BrowserDownloadItem) -> Unit)?
    ) {
        item.inputStream = input
        var output: java.io.OutputStream? = null
        var pendingMediaStoreUri: Uri? = null
        var temporaryFile: File? = null

        try {
            if (item.isCancelled) throw InterruptedException("Download cancelled")
            if (item.outputUri != null) {
                output = resolver.openOutputStream(item.outputUri!!, "wt")
                    ?: throw IllegalStateException("Không thể ghi vào vị trí đã chọn.")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, item.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, item.mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Cat Browser"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                pendingMediaStoreUri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("Không thể tạo tệp trong thư mục Download.")
                item.outputUri = pendingMediaStoreUri
                output = resolver.openOutputStream(pendingMediaStoreUri, "w")
                    ?: throw IllegalStateException("Không thể ghi tệp đã tải.")
            } else {
                val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Không thể tạo thư mục tải xuống.")
                }
                val target = uniqueFile(directory, item.fileName)
                temporaryFile = File(directory, target.name + ".part")
                item.outputFile = target
                output = temporaryFile.outputStream().buffered()
            }

            updateOnMain(item, onChanged) { it.status = BrowserDownloadStatus.DOWNLOADING }

            val buffer = ByteArray(64 * 1024)
            var received = 0L
            var lastPercent = -1
            input.buffered().use { source ->
                output.use { destination ->
                    while (true) {
                        if (item.isCancelled || Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Download cancelled")
                        }
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                        received += count
                        val percent = if (item.totalBytes > 0) {
                            ((received * 100) / item.totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        if (percent != lastPercent || received - item.receivedBytes >= 512 * 1024) {
                            lastPercent = percent
                            mainHandler.post {
                                item.receivedBytes = received
                                onChanged?.invoke(item)
                            }
                        }
                    }
                    destination.flush()
                }
            }
            output = null

            if (item.isCancelled) throw InterruptedException("Download cancelled")

            if (item.outputUri != null) {
                // SAF already created the destination document selected by the user.
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                pendingMediaStoreUri?.let { resolver.update(it, values, null, null) }
            } else {
                val part = temporaryFile ?: throw IllegalStateException("Tệp tạm không tồn tại.")
                val target = item.outputFile ?: throw IllegalStateException("Tệp đích không tồn tại.")
                if (!part.renameTo(target)) {
                    throw IllegalStateException("Không thể hoàn tất tệp tải xuống.")
                }
            }

            updateOnMain(item, onChanged) {
                it.receivedBytes = received
                it.status = BrowserDownloadStatus.COMPLETE
                it.errorMessage = null
            }
        } catch (error: InterruptedException) {
            pendingMediaStoreUri?.let { runCatching { resolver.delete(it, null, null) } }
            if (item.outputUri != null) runCatching { resolver.delete(item.outputUri!!, null, null) }
            temporaryFile?.delete()
            updateOnMain(item, onChanged) {
                it.status = BrowserDownloadStatus.CANCELLED
                it.errorMessage = "Đã hủy tải xuống."
            }
        } catch (error: Exception) {
            pendingMediaStoreUri?.let { runCatching { resolver.delete(it, null, null) } }
            if (item.outputUri != null) runCatching { resolver.delete(item.outputUri!!, null, null) }
            temporaryFile?.delete()
            updateOnMain(item, onChanged) {
                if (item.isCancelled) {
                    it.status = BrowserDownloadStatus.CANCELLED
                    it.errorMessage = "Đã hủy tải xuống."
                } else {
                    it.status = BrowserDownloadStatus.FAILED
                    it.errorMessage = error.message?.take(240) ?: "Không thể tải tệp."
                }
            }
        } finally {
            runCatching { output?.close() }
            runCatching { input.close() }
            item.inputStream = null
        }
    }

    private fun updateOnMain(
        item: BrowserDownloadItem,
        onChanged: ((BrowserDownloadItem) -> Unit)? = null,
        update: (BrowserDownloadItem) -> Unit
    ) {
        mainHandler.post {
            update(item)
            onChanged?.invoke(item)
            val terminal = item.status == BrowserDownloadStatus.COMPLETE ||
                item.status == BrowserDownloadStatus.FAILED ||
                item.status == BrowserDownloadStatus.CANCELLED
            persistDownloads(force = terminal)
        }
    }

    private fun restorePersistedDownloads() {
        val saved = runCatching {
            JSONArray(preferences.getString(KEY_DOWNLOADS, "[]"))
        }.getOrDefault(JSONArray())

        for (index in 0 until saved.length()) {
            val record = saved.optJSONObject(index) ?: continue
            if (record.optBoolean("private", false)) continue

            val savedStatus = runCatching {
                BrowserDownloadStatus.valueOf(record.optString("status"))
            }.getOrDefault(BrowserDownloadStatus.FAILED)
            val interrupted = savedStatus == BrowserDownloadStatus.QUEUED ||
                savedStatus == BrowserDownloadStatus.DOWNLOADING
            var outputUri = record.optString("outputUri").takeIf(String::isNotBlank)
                ?.let(Uri::parse)
            var outputPath = record.optString("outputFile").takeIf(String::isNotBlank)
                ?.let(::File)
            if (interrupted) {
                outputUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
                outputUri = null
                outputPath?.let { target ->
                    File(target.parentFile, target.name + ".part").delete()
                    if (!target.isFile) outputPath = null
                }
            }
            val item = BrowserDownloadItem(
                id = record.optString("id").ifBlank { UUID.randomUUID().toString() },
                fileName = record.optString("fileName").ifBlank { "download" },
                sourceUrl = record.optString("sourceUrl"),
                mimeType = record.optString("mimeType", "application/octet-stream"),
                totalBytes = record.optLong("totalBytes", -1L),
                isPrivate = false,
                createdAt = record.optLong("createdAt", System.currentTimeMillis()),
                extensionId = record.optString("extensionId").takeIf(String::isNotBlank),
                extensionDownloadId = record.optInt("extensionDownloadId", -1).takeIf { it >= 0 },
                referrer = record.optString("referrer").takeIf(String::isNotBlank)
            )
            item.outputUri = outputUri
            item.outputFile = outputPath
            item.receivedBytes = record.optLong("receivedBytes", 0L).coerceAtLeast(0L)
            item.status = when {
                interrupted -> BrowserDownloadStatus.FAILED
                savedStatus == BrowserDownloadStatus.COMPLETE &&
                    outputUri == null && outputPath?.isFile != true -> BrowserDownloadStatus.FAILED
                else -> savedStatus
            }
            item.errorMessage = when {
                interrupted -> "Tác vụ bị gián đoạn khi ứng dụng đóng. Tệp tải dở không thể tự tiếp tục."
                item.status == BrowserDownloadStatus.FAILED ->
                    record.optString("errorMessage").ifBlank { "Không thể khôi phục tệp đã tải." }
                else -> record.optString("errorMessage").takeIf(String::isNotBlank)
            }
            downloads.add(item)
        }
        downloads.sortByDescending { it.createdAt }
    }

    private fun persistDownloads(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedAt < 2_000L) return
        val records = JSONArray()
        downloads.asSequence().filterNot { it.isPrivate }.take(MAX_PERSISTED_DOWNLOADS).forEach { item ->
            val record = JSONObject()
                .put("id", item.id)
                .put("fileName", item.fileName)
                .put("sourceUrl", item.sourceUrl)
                .put("mimeType", item.mimeType)
                .put("totalBytes", item.totalBytes)
                .put("createdAt", item.createdAt)
                .put("receivedBytes", item.receivedBytes)
                .put("status", item.status.name)
                .put("errorMessage", item.errorMessage)
                .put("outputUri", item.outputUri?.toString())
                .put("outputFile", item.outputFile?.absolutePath)
                .put("extensionId", item.extensionId)
                .put("extensionDownloadId", item.extensionDownloadId)
                .put("referrer", item.referrer)
                .put("private", false)
            records.put(record)
        }
        preferences.edit().putString(KEY_DOWNLOADS, records.toString()).apply()
        lastPersistedAt = now
    }

    private fun outputContentUri(item: BrowserDownloadItem): Uri? {
        item.outputUri?.let { return it }
        val file = item.outputFile?.takeIf { it.isFile } ?: return null
        return runCatching {
            FileProvider.getUriForFile(
                appContext,
                appContext.packageName + ".fileprovider",
                file
            )
        }.getOrNull()
    }

    private fun deleteOutput(item: BrowserDownloadItem) {
        item.outputUri?.let { uri ->
            runCatching { resolver.delete(uri, null, null) }
        }
        item.outputFile?.let { file ->
            runCatching { file.delete() }
        }
    }

    private fun resolveFileName(response: WebResponse): String {
        val disposition = response.headers.entries
            .firstOrNull { it.key.equals("content-disposition", ignoreCase = true) }
            ?.value
            .orEmpty()

        val encodedName = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        val plainName = Regex("filename\\s*=\\s*\"?([^;\"]+)\"?", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        val pathName = runCatching { Uri.parse(response.uri).lastPathSegment }.getOrNull()
        val rawName = encodedName?.let {
            runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull()
        } ?: plainName ?: pathName ?: "download"

        return sanitizeDownloadFileName(rawName)
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        var index = 0
        while (true) {
            val candidateName = if (index == 0) {
                fileName
            } else {
                base + " (" + index + ")" + (extension?.let { ".$it" } ?: "")
            }
            val candidate = File(directory, candidateName)
            if (!candidate.exists() && !File(directory, candidateName + ".part").exists()) {
                return candidate
            }
            index++
        }
    }
    private companion object {
        const val KEY_DOWNLOADS = "items_v1"
        const val MAX_PERSISTED_DOWNLOADS = 100
    }
}


internal fun sanitizeDownloadFileName(rawName: String): String =
    rawName.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]"), "_")
        .trim()
        .trim('.')
        .take(120)
        .ifBlank { "download" }
