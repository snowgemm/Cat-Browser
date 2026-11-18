package io.github.snowgemm.catbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.snowgemm.catbrowser.ui.BrowserScreen
import io.github.snowgemm.catbrowser.ui.CatBrowserTheme

class MainActivity : ComponentActivity() {
    private val browserController
        get() = (application as CatBrowserApplication).browserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.dataString?.let(browserController::openExternalUrl)

        setContent {
            CatBrowserTheme(themeMode = browserController.themeMode) {
                BrowserScreen(controller = browserController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(browserController::openExternalUrl)
    }
}
