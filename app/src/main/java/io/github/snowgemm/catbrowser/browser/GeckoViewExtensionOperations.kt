package io.github.snowgemm.catbrowser.browser

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Dispatches GeckoView's @HandlerThread extension operations away from the UI thread.
 *
 * Delegate registration and createDownload remain on the UI thread according to
 * GeckoView's API annotations.
 */
internal class GeckoViewExtensionOperations(
    private val delegate: WebExtensionController,
) {
    private val operationThread = HandlerThread("CatBrowserExtensionOperations").apply { start() }
    private val operationHandler = Handler(operationThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setAddonManagerDelegate(value: WebExtensionController.AddonManagerDelegate) {
        delegate.setAddonManagerDelegate(value)
    }

    fun setPromptDelegate(value: WebExtensionController.PromptDelegate) {
        delegate.setPromptDelegate(value)
    }

    fun setDebuggerDelegate(value: WebExtensionController.DebuggerDelegate) {
        delegate.setDebuggerDelegate(value)
    }

    fun setExtensionProcessDelegate(value: WebExtensionController.ExtensionProcessDelegate) {
        delegate.setExtensionProcessDelegate(value)
    }

    fun createDownload(id: Int): WebExtension.Download? = delegate.createDownload(id)

    fun setAllowedInPrivateBrowsing(
        extension: WebExtension,
        allowed: Boolean,
    ): GeckoResult<WebExtension> =
        dispatch {
            delegate.setAllowedInPrivateBrowsing(extension, allowed)
        }

    fun list(): GeckoResult<List<WebExtension>> =
        dispatch {
            delegate.list()
        }

    fun install(
        uri: String,
        installationMethod: String,
    ): GeckoResult<WebExtension> =
        dispatch {
            delegate.install(uri, installationMethod)
        }

    fun enable(
        extension: WebExtension,
        source: Int,
    ): GeckoResult<WebExtension> =
        dispatch {
            delegate.enable(extension, source)
        }

    fun disable(
        extension: WebExtension,
        source: Int,
    ): GeckoResult<WebExtension> =
        dispatch {
            delegate.disable(extension, source)
        }

    fun update(extension: WebExtension): GeckoResult<WebExtension> =
        dispatch {
            delegate.update(extension)
        }

    fun uninstall(extension: WebExtension): GeckoResult<Void> =
        dispatch {
            delegate.uninstall(extension)
        }

    private fun <T> dispatch(operation: () -> GeckoResult<T>): GeckoResult<T> {
        val result = GeckoResult<T>()
        operationHandler.post {
            val pending =
                try {
                    operation()
                } catch (error: Exception) {
                    mainHandler.post { result.completeExceptionally(error) }
                    return@post
                }
            pending.accept(
                { value ->
                    mainHandler.post {
                        result.complete(value)
                    }
                },
                { error ->
                    mainHandler.post {
                        result.completeExceptionally(
                            error ?: IllegalStateException("GeckoView extension operation failed."),
                        )
                    }
                },
            )
        }
        return result
    }
}
