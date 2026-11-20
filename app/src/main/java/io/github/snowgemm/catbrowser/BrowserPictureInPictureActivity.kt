package io.github.snowgemm.catbrowser

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserPictureInPictureActivity : ComponentActivity() {
    private var pictureInPictureSession: GeckoSession? = null
    private var pictureInPictureView: GeckoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = (application as CatBrowserApplication).existingBrowserController()
        val session = controller?.pictureInPictureSession
        if (controller == null || session == null || !session.isOpen) {
            finish()
            return
        }

        pictureInPictureSession = session
        controller.setPictureInPictureCloseHost { finish() }

        val view = GeckoView(this).apply {
            setActivityContextDelegate { this@BrowserPictureInPictureActivity }
            setSession(session)
        }
        pictureInPictureView = view
        controller.onPictureInPictureViewAttached(session)
        setContentView(view)

        view.post {
            if (isFinishing || isDestroyed || pictureInPictureSession !== session) {
                return@post
            }
            val entered = runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }.getOrDefault(false)
            if (!entered) {
                controller.onPictureInPictureWindowDestroyed(session)
                finish()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val controller = (application as CatBrowserApplication).existingBrowserController()
        controller?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode && !isFinishing) {
            finish()
        }
    }

    override fun onDestroy() {
        val session = pictureInPictureSession
        val controller = (application as CatBrowserApplication).existingBrowserController()
        if (session != null) {
            controller?.onPictureInPictureViewDetached(session)
            pictureInPictureView?.releaseSession()
            if (controller?.pictureInPictureSession === session) {
                controller.onPictureInPictureWindowDestroyed(session)
            }
        }
        controller?.clearPictureInPictureCloseHost()
        pictureInPictureView = null
        pictureInPictureSession = null
        super.onDestroy()
    }
}
