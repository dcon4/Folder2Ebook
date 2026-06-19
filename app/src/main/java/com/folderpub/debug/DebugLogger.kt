package com.folderpub.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "debug_prefs")

object DebugLogger {

    private const val TAG = "FolderPub"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    var verboseEnabled = false
    private var appContext: Context? = null

    fun log(tag: String, message: String) {
        val line = "${dateFormat.format(Date())} [${tag}] $message"
        Log.d(tag, message)
        writeToFile(line)
    }

    fun verbose(tag: String, message: String) {
        if (!verboseEnabled) return
        val line = "${dateFormat.format(Date())} [${tag}] [VERBOSE] $message"
        Log.v(tag, message)
        writeToFile(line)
    }

    private fun writeToFile(line: String) {
        try {
            val ctx = appContext ?: return
            val logDir = File(ctx.cacheDir, "logs")
            logDir.mkdirs()
            val logFile = File(logDir, "folderpub.log")
            logFile.appendText("$line\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        verboseEnabled = false
        log(TAG, "DebugLogger initialized")
    }

    fun getLogFile(context: Context): File {
        val logDir = File(context.cacheDir, "logs")
        logDir.mkdirs()
        return File(logDir, "folderpub.log")
    }

    fun shareLog(context: Context) {
        val logFile = getLogFile(context)
        if (!logFile.exists()) {
            log(TAG, "No log file to share")
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share debug log"))
    }

    fun verboseEnabledFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_VERBOSE] ?: false
        }

    suspend fun setVerboseEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERBOSE] = enabled
        }
        verboseEnabled = enabled
    }

    private val KEY_VERBOSE = booleanPreferencesKey("verbose_logging")
}
