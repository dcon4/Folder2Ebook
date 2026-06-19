package com.folderpub.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.folderpub.debug.DebugLogger
import com.folderpub.ebook.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterContent>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgress by remember { mutableStateOf("") }
    var bookTitle by remember { mutableStateOf("My Ebook") }
    var selectedFormat by remember { mutableStateOf("EPUB") }
    var maxPages by remember { mutableStateOf("500") }
    var pdfWarnings by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("Select a folder to begin") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFolderUri = uri
            scanFolder(context, uri, onResult = { result ->
                scanResult = result
                statusMessage = "Found ${result.chapters.size} files" +
                        if (result.skipped.isNotEmpty()) " (${result.skipped.size} skipped)" else ""
                readChapters(context, result.chapters, onChapters = { contentList, warnings ->
                    chapters = contentList
                    pdfWarnings = warnings
                    statusMessage = "Loaded ${contentList.size} chapters" +
                            if (warnings > 0) " ($warnings PDFs with low-quality extraction)" else ""
                })
            }, onScanning = { isScanning = it })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FolderPub") },
                actions = {
                    IconButton(
                        onClick = { DebugLogger.shareLog(context) }
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "Share debug log")
                    }
                    IconButton(
                        onClick = onNavigateToSettings
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = bookTitle,
                onValueChange = { bookTitle = it },
                label = { Text("Book Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFormat == "EPUB",
                    onClick = { selectedFormat = "EPUB" },
                    label = { Text("EPUB") }
                )
                FilterChip(
                    selected = selectedFormat == "PDF",
                    onClick = { selectedFormat = "PDF" },
                    label = { Text("PDF") }
                )
            }

            OutlinedTextField(
                value = maxPages,
                onValueChange = { maxPages = it },
                label = { Text("Max pages per volume") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning && !isBuilding
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Text(if (selectedFolderUri == null) "Choose Folder" else "Choose Different Folder")
                }
            }

            if (scanResult != null) {
                val result = scanResult!!
                Text(
                    text = "${result.chapters.size} files found, ${result.skipped.size} skipped",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Total size: ${formatSize(result.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (pdfWarnings > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "$pdfWarnings PDF file(s) had low-quality text extraction. " +
                                    "The text may be garbled or empty.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Button(
                    onClick = {
                        buildEbook(
                            context = context,
                            chapters = chapters,
                            bookTitle = bookTitle,
                            format = selectedFormat,
                            maxPagesPerVolume = maxPages.toIntOrNull() ?: 500,
                            onProgress = { buildProgress = it },
                            onStatus = { statusMessage = it },
                            onBuilding = { isBuilding = it },
                            scope = scope
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = chapters.isNotEmpty() && !isBuilding
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Building...")
                    } else {
                        Text("Build ${selectedFormat}")
                    }
                }

                if (buildProgress.isNotEmpty()) {
                    Text(
                        text = buildProgress,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (scanResult?.skipped?.isNotEmpty() == true) {
                Text(
                    text = "Skipped files (unsupported format):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = scanResult!!.skipped.take(20).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scanResult!!.skipped.size > 20) {
                    Text(
                        text = "... and ${scanResult!!.skipped.size - 20} more",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun scanFolder(
    context: android.content.Context,
    uri: Uri,
    onResult: (ScanResult) -> Unit,
    onScanning: (Boolean) -> Unit
) {
    onScanning(true)
    Thread {
        try {
            val result = ChapterScanner.scanFolder(context, uri)
            (context as? android.app.Activity)?.runOnUiThread {
                onResult(result)
                onScanning(false)
            }
        } catch (e: Exception) {
            DebugLogger.log("MainScreen", "Scan error: ${e.message}")
            (context as? android.app.Activity)?.runOnUiThread {
                onScanning(false)
            }
        }
    }.start()
}

private fun readChapters(
    context: android.content.Context,
    files: List<ChapterFile>,
    onChapters: (List<ChapterContent>, Int) -> Unit
) {
    Thread {
        try {
            val chapters = mutableListOf<ChapterContent>()
            var warnings = 0
            for (file in files) {
                val content = ContentReader.readChapter(context, file)
                chapters.add(content)
                if (content.pdfExtractionWarning) warnings++
            }
            (context as? android.app.Activity)?.runOnUiThread {
                onChapters(chapters, warnings)
            }
        } catch (e: Exception) {
            DebugLogger.log("MainScreen", "Read error: ${e.message}")
        }
    }.start()
}

private fun buildEbook(
    context: android.content.Context,
    chapters: List<ChapterContent>,
    bookTitle: String,
    format: String,
    maxPagesPerVolume: Int,
    onProgress: (String) -> Unit,
    onStatus: (String) -> Unit,
    onBuilding: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    onBuilding(true)
    onProgress("Splitting into volumes...")
    scope.launch(Dispatchers.IO) {
        try {
            val volumes = VolumeSplitter.splitIntoVolumes(chapters, maxPagesPerVolume)
            val inputPdfUris = emptyList<Uri>()

            for (volume in volumes) {
                val volumeTitle = if (volumes.size > 1) {
                    "${bookTitle}_vol${volume.index}"
                } else {
                    bookTitle
                }

                onProgress("Building volume ${volume.index} of ${volumes.size}...")

                if (format == "EPUB") {
                    val uri = saveToMediaStore(context, "$volumeTitle.epub", "application/epub+zip")
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            EpubBuilder.buildEpub(
                                context = context,
                                chapters = volume.chapters,
                                outputStream = os,
                                bookTitle = volumeTitle
                            )
                        }
                    }
                } else {
                    val uri = saveToMediaStore(context, "$volumeTitle.pdf", "application/pdf")
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            PdfBuilder.buildPdf(
                                context = context,
                                chapters = volume.chapters,
                                inputPdfUris = emptyList(),
                                outputStream = os,
                                bookTitle = volumeTitle
                            )
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val volumeWord = if (volumes.size == 1) "" else " (${volumes.size} volumes)"
                onStatus("Done! $bookTitle saved as $format$volumeWord")
                onProgress("")
                onBuilding(false)
                Toast.makeText(context, "Ebook saved successfully!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            DebugLogger.log("MainScreen", "Build error: ${e.message}")
            withContext(Dispatchers.Main) {
                onStatus("Error: ${e.message}")
                onProgress("")
                onBuilding(false)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun saveToMediaStore(
    context: android.content.Context,
    fileName: String,
    mimeType: String
): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val uri = context.contentResolver.insert(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        },
        contentValues
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, contentValues, null, null)
    }

    return uri
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
