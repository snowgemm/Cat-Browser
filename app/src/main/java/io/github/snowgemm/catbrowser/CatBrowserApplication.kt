package io.github.snowgemm.catbrowser

import android.app.Application
import io.github.snowgemm.catbrowser.browser.BrowserController
import org.mozilla.geckoview.GeckoRuntime

class CatBrowserApplication : Application() {
    lateinit var geckoRuntime: GeckoRuntime
        private set

    lateinit var browserController: BrowserController
        private set

    override fun onCreate() {
        super.onCreate()
        geckoRuntime = GeckoRuntime.create(this)
        browserController = BrowserController(this, geckoRuntime)
    }
}
