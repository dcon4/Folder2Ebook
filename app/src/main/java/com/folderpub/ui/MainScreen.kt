package com.folderpub.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.folderpub.EpubService
import com.folderpub.debug.DebugLogger
import com.folderpub.ebook.EpubState
import com.folderpub.ebook.ScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current

    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var bookTitle by remember { mutableStateOf("My Ebook") }
    var selectedFormat by remember { mutableStateOf("EPUB") }
    var maxMb by remember { mutableStateOf("30") }

    val scanResult by EpubState.scanResult.collectAsState()
    val isScanning by EpubState.isScanning.collectAsState()
    val isReading by EpubState.isReading.collectAsState()
    val isBuilding by EpubState.isBuilding.collectAsState()
    val statusMessage by EpubState.statusMessage.collectAsState()
    val buildProgress by EpubState.buildProgress.collectAsState()
    val chaptersCount by EpubState.chaptersCount.collectAsState()
    val pdfWarnings by EpubState.pdfWarnings.collectAsState()

    val busy = isScanning || isReading || isBuilding

    LaunchedEffect(statusMessage) {
        when {
            statusMessage.startsWith("Done!") ->
                Toast.makeText(context, "Ebook saved successfully!", Toast.LENGTH_LONG).show()
            statusMessage.startsWith("Error") || statusMessage.startsWith("Scan error") ->
                Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFolderUri = uri
            val intent = Intent(context, EpubService::class.java).apply {
                action = EpubService.ACTION_SCAN
                putExtra(EpubService.EXTRA_FOLDER_URI, uri.toString())
            }
            context.startForegroundService(intent)
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
                value = maxMb,
                onValueChange = { maxMb = it },
                label = { Text("Max MB per volume") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (isScanning || isReading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isScanning) "Scanning..." else "Reading...")
                } else {
                    Text(if (selectedFolderUri == null) "Choose Folder" else "Choose Different Folder")
                }
            }

            if (scanResult != null) {
                val result = scanResult!!
                Text(
                    text = "${result.chapters.size} files found, ${result.skippedCount} skipped",
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
                        val intent = Intent(context, EpubService::class.java).apply {
                            action = EpubService.ACTION_BUILD
                            putExtra(EpubService.EXTRA_TITLE, bookTitle)
                            putExtra(EpubService.EXTRA_FORMAT, selectedFormat)
                            putExtra(EpubService.EXTRA_MAX_MB, maxMb.toIntOrNull() ?: 30)
                        }
                        context.startForegroundService(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = chaptersCount > 0 && !busy
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Building...")
                    } else {
                        Text("Build $selectedFormat")
                    }
                }

                if (buildProgress.isNotEmpty()) {
                    Text(
                        text = buildProgress,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if ((scanResult?.skippedCount ?: 0) > 0) {
                val skipped = scanResult!!
                Text(
                    text = "Skipped files (${skipped.skippedCount}):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = skipped.skippedByExt.entries
                        .sortedByDescending { it.value }
                        .take(8)
                        .joinToString(", ") { ".${it.key} x${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                skipped.skippedExamples.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}