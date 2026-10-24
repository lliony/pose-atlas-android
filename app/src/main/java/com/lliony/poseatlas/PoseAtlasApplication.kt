package com.lliony.poseatlas

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp class PoseAtlasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Record only a marker, never exception messages, model payloads, or private content.
            runCatching { getSharedPreferences("health", MODE_PRIVATE).edit().putBoolean("previous_crash", true).commit() }
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
