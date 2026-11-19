package io.github.snowgemm.catbrowser.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

internal object BrowserUpdateArtifacts {
    private val completedNames = listOf(
        "cat-browser-stable-update.apk",
        "cat-browser-beta-update.apk"
    )
    private val partialNames = completedNames.map { "$it.part" }

    fun removePartialDownloads(cacheDir: File): Int =
        deleteKnownFiles(cacheDir, partialNames)

    fun removeInstalledApks(cacheDir: File): Int =
        deleteKnownFiles(cacheDir, completedNames + partialNames)

    private fun deleteKnownFiles(cacheDir: File, names: List<String>): Int =
        names.count { name ->
            val file = File(cacheDir, name)
            file.isFile && file.delete()
        }
}

/**
 * The Package Installer may still need the APK after its launch Intent returns.
 * MY_PACKAGE_REPLACED is sent only after this app has been successfully replaced.
 */
class UpdateApkCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        BrowserUpdateArtifacts.removeInstalledApks(context.cacheDir)
    }
}
