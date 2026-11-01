package com.folderpub.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {

    private const val TAG = "FolderPub"
    private const val APP_NAME = "folderpub"
    private const val PREFS_NAME = "debug_prefs"
    private const val KEY_VERBOSE = "verbose_logging"
    private val lineFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)

    var verboseEnabled = true
    private var prefs: android.content.SharedPreferences? = null
    private var logFile: File? = null

    fun log(tag: String, message: String) {
        val line = "${lineFormat.format(Date())} [${tag}] $message"
        Log.d(tag, message)
        writeToFile(line)
    }

    fun verbose(tag: String, message: String) {
        if (!verboseEnabled) return
        val line = "${lineFormat.format(Date())} [${tag}] [VERBOSE] $message"
        Log.v(tag, message)
        writeToFile(line)
    }

    private fun writeToFile(line: String) {
        try {
            val file = logFile ?: return
            file.appendText("$line\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        verboseEnabled = prefs!!.getBoolean(KEY_VERBOSE, true)

        val logDir = File(context.cacheDir, "logs")
        logDir.mkdirs()
        val now = fileNameFormat.format(Date())
        logFile = File(logDir, "${APP_NAME}-debug.log.$now.txt")

        val buildNumber = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } catch (e: Exception) {
            0L
        }
        val header = "${lineFormat.format(Date())} [$APP_NAME] [VERBOSE] Logger initialized (build $buildNumber)"
        logFile?.writeText("$header\n")

        log(TAG, "DebugLogger initialized (verbose=$verboseEnabled)")
    }

    fun getLogFile(context: Context): File? {
        return logFile?.takeIf { it.exists() }
    }

    fun shareLog(context: Context) {
        val file = getLogFile(context) ?: return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share debug log"))
    }

    fun isVerboseEnabled(): Boolean = verboseEnabled

    fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_VERBOSE, enabled)?.apply()
    }
}