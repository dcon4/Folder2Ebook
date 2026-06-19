package com.folderpub

import android.app.Application
import com.folderpub.debug.DebugLogger

class FolderPubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
    }
}
