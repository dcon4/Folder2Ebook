package com.folderpub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import com.folderpub.debug.DebugLogger
import com.folderpub.ebook.ChapterContent
import com.folderpub.ebook.ChapterFile
import com.folderpub.ebook.ChapterScanner
import com.folderpub.ebook.ContentReader
import com.folderpub.ebook.EpubBuilder
import com.folderpub.ebook.EpubState
import com.folderpub.ebook.PdfBuilder
import com.folderpub.ebook.VolumeSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class EpubService : Service() {

    companion object {
        const val ACTION_SCAN = "com.folderpub.action.SCAN"
        const val ACTION_BUILD = "com.folderpub.action.BUILD"
        const val ACTION_STOP = "com.folderpub.action.STOP"
        const val EXTRA_FOLDER_URI = "folder_uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_MAX_MB = "max_mb"
        private const val TAG = "EpubService"
        private const val CHANNEL_ID = "ebook_progress"
        private const val NOTIF_ID = 101

        @Volatile var heldChapters: List<ChapterContent> = emptyList()
        @Volatile private var scanActive = false
        @Volatile private var buildActive = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Ebook progress",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        DebugLogger.log(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCAN -> {
                startForeground(NOTIF_ID, buildNotification("Scanning folder..."))
                if (!scanActive) {
                    startScan(intent.getStringExtra(EXTRA_FOLDER_URI))
                } else {
                    DebugLogger.log(TAG, "Scan already running, ignoring request")
                }
            }
            ACTION_BUILD -> {
                startForeground(NOTIF_ID, buildNotification("Building ebook..."))
                if (!buildActive && heldChapters.isNotEmpty()) {
                    startBuild(
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "My Ebook",
                        format = intent.getStringExtra(EXTRA_FORMAT) ?: "EPUB",
                        maxMb = intent.getIntExtra(EXTRA_MAX_MB, 30)
                    )
                } else {
                    DebugLogger.log(TAG, "Build ignored (active=$buildActive, chapters=${heldChapters.size})")
                }
            }
            ACTION_STOP -> {
                DebugLogger.log(TAG, "Stop requested")
                EpubState.statusMessage.value = "Stopped"
                finishService("Stopped")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DebugLogger.log(TAG, "Service destroyed")
        scanActive = false
        buildActive = false
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScan(folderUri: String?) {
        if (folderUri == null) {
            EpubState.statusMessage.value = "No folder provided"
            finishService("No folder")
            return
        }
        scanActive = true
        EpubState.reset()
        EpubState.isScanning.value = true
        EpubState.statusMessage.value = "Scanning folder..."
        updateNotification("Scanning folder...")
        acquireWakeLock()
        DebugLogger.log(TAG, "Scanning $folderUri")

        scope.launch {
            try {
                val uri = Uri.parse(folderUri)
                val result = ChapterScanner.scanFolder(this@EpubService, uri)
                EpubState.scanResult.value = result
                EpubState.statusMessage.value = "Found ${result.chapters.size} files" +
                        if (result.skippedCount > 0) " (${result.skippedCount} skipped)" else ""
                readChapters(result.chapters)
            } catch (e: Throwable) {
                DebugLogger.log(TAG, "Scan error: ${e.javaClass.simpleName} - ${e.message}")
                EpubState.statusMessage.value = "Scan error: ${e.javaClass.simpleName}"
                EpubState.isScanning.value = false
                scanActive = false
                finishService("Scan failed")
            }
        }
    }

    private suspend fun readChapters(files: List<ChapterFile>) {
        EpubState.isReading.value = true
        EpubState.readTotal.value = files.size
        EpubState.readDone.value = 0
        DebugLogger.log(TAG, "Reading ${files.size} chapters")
        updateNotification("Reading 0 of ${files.size} chapters")

        val chapters = mutableListOf<ChapterContent>()
        var warnings = 0
        try {
            for ((index, file) in files.withIndex()) {
                val content = ContentReader.readChapter(this@EpubService, file)
                chapters.add(content)
                if (content.pdfExtractionWarning) warnings++
                if ((index + 1) % 100 == 0 || index + 1 == files.size) {
                    val done = index + 1
                    DebugLogger.log(TAG, "Read $done/${files.size} chapters")
                    EpubState.readDone.value = done
                    updateNotification("Reading $done of ${files.size} chapters")
                    EpubState.statusMessage.value = "Reading chapters $done of ${files.size}..."
                }
            }
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "Read error: ${e.javaClass.simpleName} - ${e.message}")
            EpubState.statusMessage.value = "Read error: ${e.javaClass.simpleName} - ${e.message}"
        } finally {
            heldChapters = chapters
            EpubState.isReading.value = false
            EpubState.isScanning.value = false
            scanActive = false
            EpubState.chaptersCount.value = chapters.size
            EpubState.pdfWarnings.value = warnings
            EpubState.statusMessage.value = "Loaded ${chapters.size} chapters" +
                    if (warnings > 0) " ($warnings PDFs with low-quality extraction)" else ""
            DebugLogger.log(TAG, "Read complete: ${chapters.size} chapters loaded")
            updateNotification("${chapters.size} chapters ready to build")
            releaseWakeLock()
        }
    }

    private fun startBuild(title: String, format: String, maxMb: Int) {
        buildActive = true
        EpubState.isBuilding.value = true
        EpubState.statusMessage.value = "Building $format..."
        EpubState.buildProgress.value = "Splitting into volumes..."
        updateNotification("Building $format...")
        acquireWakeLock()

        scope.launch {
            try {
                val chapters = heldChapters
                val volumes = VolumeSplitter.splitIntoVolumes(chapters, maxMb)

                for (volume in volumes) {
                    val volumeTitle = if (volumes.size > 1) {
                        "${title}_vol${volume.index}"
                    } else {
                        title
                    }
                    val progress = "Building volume ${volume.index} of ${volumes.size}..."
                    EpubState.buildProgress.value = progress
                    updateNotification(progress)

                    val tempFile = File(cacheDir, "ebooks/${volumeTitle}.${format.lowercase()}")
                    tempFile.parentFile?.mkdirs()

                    if (format == "EPUB") {
                        tempFile.outputStream().use { os ->
                            EpubBuilder.buildEpub(
                                context = this@EpubService,
                                chapters = volume.chapters,
                                outputStream = os,
                                bookTitle = volumeTitle
                            )
                        }
                    } else {
                        tempFile.outputStream().use { os ->
                            PdfBuilder.buildPdf(
                                context = this@EpubService,
                                chapters = volume.chapters,
                                inputPdfUris = emptyList(),
                                outputStream = os,
                                bookTitle = volumeTitle
                            )
                        }
                    }

                    val outUri = copyToDownloads(this@EpubService, tempFile, volumeTitle, format.lowercase())
                    if (outUri == null) {
                        throw java.io.IOException("Failed to save ebook to Downloads")
                    }
                }

                val volumeWord = if (volumes.size == 1) "" else " (${volumes.size} volumes)"
                EpubState.statusMessage.value = "Done! $title saved as $format$volumeWord"
                EpubState.buildProgress.value = ""
                DebugLogger.log(TAG, "Build done: $title saved as $format$volumeWord")
                heldChapters = emptyList()
                buildActive = false
                EpubState.isBuilding.value = false
                finishService("Ebook saved")
            } catch (e: Throwable) {
                DebugLogger.log(TAG, "Build error: ${e.javaClass.simpleName} - ${e.message}")
                EpubState.statusMessage.value = "Error: ${e.javaClass.simpleName} - ${e.message}"
                EpubState.buildProgress.value = ""
                buildActive = false
                EpubState.isBuilding.value = false
                finishService("Build failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun finishService(lastNotification: String) {
        releaseWakeLock()
        updateNotification(lastNotification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FolderPub:ebook").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
        DebugLogger.verbose(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                DebugLogger.verbose(TAG, "Wake lock released")
            }
        } catch (e: Throwable) {
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopAction = PendingIntent.getService(
            this,
            1,
            Intent(this, EpubService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("FolderPub")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(Notification.Action(R.mipmap.ic_launcher, "Stop", stopAction))
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "Notification update failed: ${e.message}")
        }
    }

    private fun copyToDownloads(
        context: android.content.Context,
        sourceFile: File,
        displayName: String,
        extension: String
    ): Uri? {
        return try {
            val mimeType = if (extension == "epub") "application/epub+zip" else "application/pdf"
            val fileName = "$displayName.$extension"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = context.contentResolver.insert(collectionUri, contentValues) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { os ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(os)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "Copy to Downloads error: ${e.message}")
            null
        }
    }
}