package com.toimg.app

import android.app.Application
import com.toimg.app.core.util.NotificationHelper

class ToImgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        NotificationHelper.ensureChannels(this)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ToImgCrash", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
