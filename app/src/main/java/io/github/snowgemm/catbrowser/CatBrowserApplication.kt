package io.github.snowgemm.catbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class CatBrowserApplication : Application() {
    lateinit var geckoRuntime: GeckoRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        geckoRuntime = GeckoRuntime.create(this)
    }
}
