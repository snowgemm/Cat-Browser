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
    val isPrivate: Boolean
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

    val downloads = mutableStateListOf<BrowserDownloadItem>()

    fun enqueue(response: WebResponse, privateMode: Boolean) {
        val body = response.body
        if (body == null) return

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

        val fileName = resolveFileName(response)
        val item = BrowserDownloadItem(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            sourceUrl = response.uri,
            mimeType = mimeType,
            totalBytes = contentLength,
            isPrivate = privateMode
        )
        downloads.add(0, item)

        executor.execute {
            copyResponse(item, body)
        }
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

    private fun copyResponse(item: BrowserDownloadItem, input: InputStream) {
        item.inputStream = input
        var output: java.io.OutputStream? = null
        var pendingMediaStoreUri: Uri? = null
        var temporaryFile: File? = null

        try {
            if (item.isCancelled) throw InterruptedException("Download cancelled")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

            updateOnMain(item) { it.status = BrowserDownloadStatus.DOWNLOADING }

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
                            mainHandler.post { item.receivedBytes = received }
                        }
                    }
                    destination.flush()
                }
            }
            output = null

            if (item.isCancelled) throw InterruptedException("Download cancelled")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

            updateOnMain(item) {
                it.receivedBytes = received
                it.status = BrowserDownloadStatus.COMPLETE
                it.errorMessage = null
            }
        } catch (error: InterruptedException) {
            pendingMediaStoreUri?.let { runCatching { resolver.delete(it, null, null) } }
            temporaryFile?.delete()
            updateOnMain(item) {
                it.status = BrowserDownloadStatus.CANCELLED
                it.errorMessage = "Đã hủy tải xuống."
            }
        } catch (error: Exception) {
            pendingMediaStoreUri?.let { runCatching { resolver.delete(it, null, null) } }
            temporaryFile?.delete()
            updateOnMain(item) {
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
        update: (BrowserDownloadItem) -> Unit
    ) {
        mainHandler.post { update(item) }
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
}


internal fun sanitizeDownloadFileName(rawName: String): String =
    rawName.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]"), "_")
        .trim()
        .trim('.')
        .take(120)
        .ifBlank { "download" }
