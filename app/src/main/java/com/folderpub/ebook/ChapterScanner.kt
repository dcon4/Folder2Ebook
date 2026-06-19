package com.folderpub.ebook

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.folderpub.debug.DebugLogger

data class ChapterFile(
    val uri: Uri,
    val name: String,
    val extension: String,
    val sizeBytes: Long
)

data class ScanResult(
    val chapters: List<ChapterFile>,
    val skipped: List<String>,
    val totalSizeBytes: Long
)

object ChapterScanner {

    private const val TAG = "ChapterScanner"

    private val SUPPORTED_EXTENSIONS = setOf("html", "htm", "txt", "pdf")

    fun scanFolder(context: Context, treeUri: Uri): ScanResult {
        val chapters = mutableListOf<ChapterFile>()
        val skipped = mutableListOf<String>()
        var totalSize = 0L

        val rootDir = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDir == null || !rootDir.exists()) {
            DebugLogger.log(TAG, "Root directory not found: $treeUri")
            return ScanResult(emptyList(), listOf("Root directory not found"), 0)
        }

        scanRecursive(context, rootDir, chapters, skipped, totalSize)

        totalSize = chapters.sumOf { it.sizeBytes }
        DebugLogger.log(TAG, "Scan complete: ${chapters.size} files, ${skipped.size} skipped")
        return ScanResult(chapters, skipped, totalSize)
    }

    private fun scanRecursive(
        context: Context,
        dir: DocumentFile,
        chapters: MutableList<ChapterFile>,
        skipped: MutableList<String>,
        totalSize: Long
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanRecursive(context, file, chapters, skipped, totalSize)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val uri = file.uri
                    if (uri != null) {
                        chapters.add(
                            ChapterFile(
                                uri = uri,
                                name = name,
                                extension = ext,
                                sizeBytes = file.length()
                            )
                        )
                    }
                } else {
                    skipped.add(name)
                }
            }
        }
    }
}
