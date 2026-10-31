package com.folderpub.ebook

import android.content.Context
import android.net.Uri
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
    val skippedCount: Int,
    val skippedByExt: Map<String, Int>,
    val skippedExamples: List<String>,
    val totalSizeBytes: Long
)

object ChapterScanner {

    private const val TAG = "ChapterScanner"
    private const val MAX_SKIPPED_EXAMPLES = 5

    private val SUPPORTED_EXTENSIONS = setOf("html", "htm", "txt", "pdf")

    fun scanFolder(context: Context, treeUri: Uri): ScanResult {
        val chapters = mutableListOf<ChapterFile>()
        val skippedByExt = mutableMapOf<String, Int>()
        val skippedExamples = mutableListOf<String>()

        val rootDir = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDir == null || !rootDir.exists()) {
            DebugLogger.log(TAG, "Root directory not found: $treeUri")
            return ScanResult(emptyList(), 1, mapOf("error" to 1), listOf("Root directory not found"), 0)
        }

        scanRecursive(context, rootDir, chapters, skippedByExt, skippedExamples)

        val totalSize = chapters.sumOf { it.sizeBytes }
        val skippedCount = skippedByExt.values.sum()

        val extSummary = skippedByExt.entries
            .sortedByDescending { it.value }
            .take(8)
            .joinToString(", ") { ".${it.key} x${it.value}" }
        DebugLogger.log(
            TAG,
            "Scan complete: ${chapters.size} files, $skippedCount skipped ($extSummary)"
        )

        return ScanResult(chapters, skippedCount, skippedByExt, skippedExamples, totalSize)
    }

    private fun scanRecursive(
        context: Context,
        dir: DocumentFile,
        chapters: MutableList<ChapterFile>,
        skippedByExt: MutableMap<String, Int>,
        skippedExamples: MutableList<String>
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanRecursive(context, file, chapters, skippedByExt, skippedExamples)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { "(none)" }
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
                    skippedByExt[ext] = (skippedByExt[ext] ?: 0) + 1
                    if (skippedExamples.size < MAX_SKIPPED_EXAMPLES) {
                        skippedExamples.add(name)
                    }
                }
            }
        }
    }
}