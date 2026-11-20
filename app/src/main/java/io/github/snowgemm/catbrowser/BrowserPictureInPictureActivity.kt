package io.github.snowgemm.catbrowser

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserPictureInPictureActivity : ComponentActivity() {
    private var pictureInPictureSession: GeckoSession? = null
    private var pictureInPictureView: GeckoView? = null
    private var hasEnteredPictureInPicture = false
    private var returnToBrowserOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = (application as CatBrowserApplication).existingBrowserController()
        val session = controller?.pictureInPictureSession
        if (controller == null || session == null) {
            finish()
            return
        }
        if (!session.isOpen) {
            controller.onPictureInPictureWindowDestroyed(session)
            finish()
            return
        }

        pictureInPictureSession = session
        controller.setPictureInPictureCloseHost { finish() }

        val view = GeckoView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setActivityContextDelegate { this@BrowserPictureInPictureActivity }
            setSession(session)
        }
        pictureInPictureView = view
        controller.onPictureInPictureViewAttached(session)
        session.loadUri("about:blank")
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        if (returnToBrowserOnResume) {
            returnToBrowserOnResume = false
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
            )
            finish()
            return
        }
        if (hasEnteredPictureInPicture) return
        hasEnteredPictureInPicture = true

        val entered = runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }.getOrDefault(false)
        if (!entered) finish()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val controller = (application as CatBrowserApplication).existingBrowserController()
        controller?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode || isFinishing) return

        // Android reports CREATED after user dismissal, but STARTED when expanding PiP.
        if (lifecycle.currentState == Lifecycle.State.CREATED) {
            finish()
        } else {
            returnToBrowserOnResume = true
        }
    }

    override fun onDestroy() {
        val session = pictureInPictureSession
        val controller = (application as CatBrowserApplication).existingBrowserController()
        if (session != null) {
            controller?.onPictureInPictureViewDetached(session)
            pictureInPictureView?.releaseSession()
            controller?.let { browserController ->
                if (browserController.pictureInPictureSession === session) {
                    browserController.onPictureInPictureWindowDestroyed(session)
                }
            }
        }
        controller?.clearPictureInPictureCloseHost()
        pictureInPictureView = null
        pictureInPictureSession = null
        super.onDestroy()
    }
}
