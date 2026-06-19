package com.folderpub

import android.app.Application
import com.folderpub.debug.DebugLogger
import com.folderpub.ebook.ContentReader

class FolderPubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        ContentReader.ensurePdfBoxInitialized(this)
    }
}
