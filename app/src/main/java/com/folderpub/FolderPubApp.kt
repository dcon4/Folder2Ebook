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

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.log("CRASH", "${thread.name}: ${throwable.javaClass.name}: ${throwable.message}")
            throwable.stackTrace.take(15).forEach { element ->
                DebugLogger.log("CRASH", "  at $element")
            }
            try {
                val crashFile = File(cacheDir, "crash_log.txt")
                crashFile.writeText(
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}\n" +
                    "Message: ${throwable.message}\n" +
                    java.util.Arrays.toString(throwable.stackTrace).replace(", ", "\n  at ")
                )
            } catch (_: Throwable) {}
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}