package io.github.snowgemm.catbrowser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import androidx.annotation.MainThread
import android.util.Log
import io.github.snowgemm.catbrowser.browser.BrowserController
import org.mozilla.geckoview.GeckoRuntime
import java.io.File

class CatBrowserApplication : Application() {
    lateinit var geckoRuntime: GeckoRuntime
        private set

    lateinit var browserController: BrowserController
        private set

    private var isMainProcess = false

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName()
        isMainProcess = processName == packageName
        if (!isMainProcess) {
            Log.i(TAG, "Skipping browser initialization in non-main process: $processName")
        }
    }

    /**
     * GeckoRuntime creation is UI-thread-only. Call this after the launch screen
     * has drawn so engine and browser-state initialization do not delay the first frame.
     */
    @MainThread
    fun initializeBrowserController(): BrowserController {
        check(isMainProcess) { "The browser can only be initialized in the main app process." }

        if (!::geckoRuntime.isInitialized) {
            logPreviousMainProcessExit()
            geckoRuntime = GeckoRuntime.create(this)
        }
        if (!::browserController.isInitialized) {
            browserController = BrowserController(this, geckoRuntime)
        }
        return browserController
    }

    fun existingBrowserController(): BrowserController? =
        if (::browserController.isInitialized) browserController else null

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        val procName = runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        }.getOrNull()

        if (!procName.isNullOrBlank()) return procName

        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return manager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
    }

    private fun logPreviousMainProcessExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val manager = getSystemService(ActivityManager::class.java) ?: return
        runCatching {
            manager.getHistoricalProcessExitReasons(packageName, 0, 5)
                .filter { it.processName == packageName }
                .forEach { exit ->
                    Log.w(
                        TAG,
                        "Previous main-process exit: reason=${exit.reason}, " +
                            "status=${exit.status}, description=${exit.description}"
                    )
                }
        }.onFailure { error ->
            Log.w(TAG, "Could not read previous process exit reasons", error)
        }
    }

    private companion object {
        const val TAG = "CatBrowserApplication"
    }
}
