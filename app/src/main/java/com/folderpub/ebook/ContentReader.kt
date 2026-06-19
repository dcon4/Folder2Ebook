package com.folderpub.ebook

import android.content.Context
import android.net.Uri
import com.folderpub.debug.DebugLogger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.io.InputStream

data class ChapterContent(
    val title: String,
    val bodyHtml: String,
    val isPdf: Boolean,
    val pdfExtractionWarning: Boolean
)

object ContentReader {

    private const val TAG = "ContentReader"
    private const val MAX_HTML_FILE_SIZE = 5 * 1024 * 1024
    private const val MAX_PDF_FILE_SIZE = 30 * 1024 * 1024
    private var pdfBoxInitialized = false

    fun ensurePdfBoxInitialized(context: Context) {
        if (!pdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context)
                pdfBoxInitialized = true
            } catch (e: Throwable) {
                DebugLogger.log(TAG, "PDFBox init failed: ${e.message}")
            }
        }
    }

    fun readChapter(context: Context, file: ChapterFile): ChapterContent {
        return try {
            val contentResolver = context.contentResolver

            when (file.extension) {
                "html", "htm" -> {
                    if (file.sizeBytes > MAX_HTML_FILE_SIZE) {
                        DebugLogger.log(TAG, "HTML too large: ${file.name}")
                        ChapterContent(
                            title = file.name,
                            bodyHtml = "<p>[File too large: ${file.name}]</p>",
                            isPdf = false, pdfExtractionWarning = false
                        )
                    } else {
                        readHtml(contentResolver, file)
                    }
                }
                "txt" -> {
                    if (file.sizeBytes > MAX_HTML_FILE_SIZE) {
                        DebugLogger.log(TAG, "Text too large: ${file.name}")
                        ChapterContent(
                            title = file.name,
                            bodyHtml = "<p>[File too large: ${file.name}]</p>",
                            isPdf = false, pdfExtractionWarning = false
                        )
                    } else {
                        readText(contentResolver, file)
                    }
                }
                "pdf" -> {
                    if (file.sizeBytes > MAX_PDF_FILE_SIZE) {
                        DebugLogger.log(TAG, "PDF too large: ${file.name}")
                        ChapterContent(
                            title = file.name,
                            bodyHtml = "<p>[File too large: ${file.name}]</p>",
                            isPdf = false, pdfExtractionWarning = false
                        )
                    } else {
                        readPdf(contentResolver, file)
                    }
                }
                else -> {
                    DebugLogger.log(TAG, "Unsupported extension: ${file.extension}")
                    ChapterContent(title = file.name, bodyHtml = "<p>[Unsupported file type]</p>", isPdf = false, pdfExtractionWarning = false)
                }
            }
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "Error reading ${file.name}: ${e.javaClass.simpleName} - ${e.message}")
            ChapterContent(
                title = file.name,
                bodyHtml = "<p>[Error reading ${file.name}]</p>",
                isPdf = false, pdfExtractionWarning = false
            )
        }
    }

    private fun readHtml(
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent {
        val rawHtml = readStream(contentResolver, file.uri)
            ?: return ChapterContent(title = file.name, bodyHtml = "<p>[Empty file]</p>", isPdf = false, pdfExtractionWarning = false)
        val doc = Jsoup.parse(rawHtml)
        val bodyHtml = doc.body().html()
        val title = TitleExtractor.extractTitle(file.name, rawHtml, isHtml = true)
        return ChapterContent(title = title, bodyHtml = bodyHtml, isPdf = false, pdfExtractionWarning = false)
    }

    private fun readText(
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent {
        val text = readStream(contentResolver, file.uri)
            ?: return ChapterContent(title = file.name, bodyHtml = "<p>[Empty file]</p>", isPdf = false, pdfExtractionWarning = false)
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val bodyHtml = "<pre>$escaped</pre>"
        val title = TitleExtractor.extractTitle(file.name, text, isHtml = false)
        return ChapterContent(title = title, bodyHtml = bodyHtml, isPdf = false, pdfExtractionWarning = false)
    }

    private fun readPdf(
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent {
        var document: PDDocument? = null
        try {
            val inputStream = contentResolver.openInputStream(file.uri)
                ?: return ChapterContent(title = file.name, bodyHtml = "<p>[Cannot open PDF]</p>", isPdf = true, pdfExtractionWarning = true)
            document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(document)

            val pageCount = document.numberOfPages
            val wordCount = text.split("\\s+".toRegex()).size
            val warning = wordCount < 10 && pageCount > 0

            val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val bodyHtml = if (warning) {
                "<p><em>[PDF text extraction may be poor - extracted $wordCount words from $pageCount pages]</em></p><pre>$escaped</pre>"
            } else {
                "<pre>$escaped</pre>"
            }
            val title = TitleExtractor.extractTitle(file.name, text, isHtml = false)
            ChapterContent(title = title, bodyHtml = bodyHtml, isPdf = true, pdfExtractionWarning = warning)
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "PDF read error: ${file.name} - ${e.javaClass.simpleName}: ${e.message}")
            ChapterContent(title = file.name, bodyHtml = "<p>[Error reading PDF: ${file.name}]</p>", isPdf = true, pdfExtractionWarning = true)
        } finally {
            try { document?.close() } catch (_: Throwable) {}
        }
    }

    private fun readStream(contentResolver: android.content.ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream -> stream.bufferedReader().readText() }
        } catch (e: Throwable) {
            DebugLogger.log(TAG, "Stream read error: ${e.message}")
            null
        }
    }
}
