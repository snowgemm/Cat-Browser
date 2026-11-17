package io.github.snowgemm.catbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.snowgemm.catbrowser.ui.BrowserScreen
import io.github.snowgemm.catbrowser.ui.CatBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val runtime = (application as CatBrowserApplication).geckoRuntime
        setContent {
            CatBrowserTheme {
                BrowserScreen(runtime = runtime)
            }
        }
    }
}
