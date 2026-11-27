/*
 * file:    ResoundApp.kt
 * author:  Typezer∅
 * version: 0.1.0
 * desc:    Application entry; owns the manual DI container.
 */
package com.nexvora.echora

import android.app.Application
import com.nexvora.echora.BuildConfig
import com.nexvora.echora.di.AppContainer

class ResoundApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Startup contract: single process, nothing native has run yet. Must precede the container.
        com.nexvora.echora.core.io.ScratchSweeper.sweepAtStartup(cacheDir)
        if (BuildConfig.IS_DEVTEST) {
            com.nexvora.echora.core.DevTrace.sink = { android.util.Log.i("ResoundTrace", "${System.nanoTime() / 1_000_000} ${Thread.currentThread().name} $it") }
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
        }
        container = AppContainer(applicationContext)
    }
}
