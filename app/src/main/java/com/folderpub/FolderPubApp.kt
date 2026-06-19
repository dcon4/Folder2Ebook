package com.folderpub

import android.app.Application
import com.folderpub.debug.DebugLogger
import com.folderpub.ebook.ContentReader
import java.io.File

class FolderPubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        ContentReader.ensurePdfBoxInitialized(this)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.log("Crash", "${throwable.javaClass.simpleName}: ${throwable.message}")
            val crashFile = File(cacheDir, "crash_log.txt")
            crashFile.writeText(
                "Thread: ${thread.name}\n" +
                "Exception: ${throwable.javaClass.name}\n" +
                "Message: ${throwable.message}\n" +
                java.util.Arrays.toString(throwable.stackTrace).replace(", ", "\n  at ")
            )
        }
    }
}
