package com.folderpub.ebook

import android.content.Context
import android.net.Uri
import com.folderpub.debug.DebugLogger
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.InputStream

data class ChapterContent(
    val title: String,
    val bodyHtml: String,
    val isPdf: Boolean,
    val pdfExtractionWarning: Boolean
)

object ContentReader {

    private const val TAG = "ContentReader"
    private const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024

    fun readChapter(context: Context, file: ChapterFile): ChapterContent? {
        return try {
            val contentResolver = context.contentResolver
            if (file.sizeBytes > MAX_FILE_SIZE_BYTES) {
                DebugLogger.log(TAG, "File too large: ${file.name} (${file.sizeBytes} bytes)")
                return ChapterContent(
                    title = file.name,
                    bodyHtml = "<p>[File too large: ${file.name}]</p>",
                    isPdf = false,
                    pdfExtractionWarning = false
                )
            }

            when (file.extension) {
                "html", "htm" -> readHtml(context, contentResolver, file)
                "txt" -> readText(context, contentResolver, file)
                "pdf" -> readPdf(context, contentResolver, file)
                else -> {
                    DebugLogger.log(TAG, "Unsupported extension: ${file.extension}")
                    null
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading ${file.name}: ${e.message}")
            ChapterContent(
                title = file.name,
                bodyHtml = "<p>[Error reading ${file.name}: ${e.message}]</p>",
                isPdf = false,
                pdfExtractionWarning = false
            )
        }
    }

    private fun readHtml(
        context: Context,
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent {
        val rawHtml = readStream(contentResolver, file.uri) ?: return null
        val doc = Jsoup.parse(rawHtml)
        val bodyHtml = doc.body().html()
        val title = TitleExtractor.extractTitle(file.name, rawHtml, isHtml = true)
        return ChapterContent(
            title = title,
            bodyHtml = bodyHtml,
            isPdf = false,
            pdfExtractionWarning = false
        )
    }

    private fun readText(
        context: Context,
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent {
        val text = readStream(contentResolver, file.uri) ?: return null
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val bodyHtml = "<pre>$escaped</pre>"
        val title = TitleExtractor.extractTitle(file.name, text, isHtml = false)
        return ChapterContent(
            title = title,
            bodyHtml = bodyHtml,
            isPdf = false,
            pdfExtractionWarning = false
        )
    }

    private fun readPdf(
        context: Context,
        contentResolver: android.content.ContentResolver,
        file: ChapterFile
    ): ChapterContent? {
        var document: PDDocument? = null
        return try {
            val inputStream = contentResolver.openInputStream(file.uri)
                ?: return null
            document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(document)

            val pageCount = document.numberOfPages
            val wordCount = text.split("\\s+".toRegex()).size
            val warning = wordCount < 10 && pageCount > 0

            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val bodyHtml = if (warning) {
                "<p><em>[PDF text extraction may be poor - " +
                        "extracted $wordCount words from $pageCount pages]</em></p>" +
                        "<pre>$escaped</pre>"
            } else {
                "<pre>$escaped</pre>"
            }

            val title = TitleExtractor.extractTitle(file.name, text, isHtml = false)
            ChapterContent(
                title = title,
                bodyHtml = bodyHtml,
                isPdf = true,
                pdfExtractionWarning = warning
            )
        } catch (e: Exception) {
            DebugLogger.log(TAG, "PDF read error: ${file.name} - ${e.message}")
            ChapterContent(
                title = file.name,
                bodyHtml = "<p>[Error reading PDF: ${file.name} - ${e.message}]</p>",
                isPdf = true,
                pdfExtractionWarning = true
            )
        } finally {
            try { document?.close() } catch (_: Exception) {}
        }
    }

    private fun readStream(
        contentResolver: android.content.ContentResolver,
        uri: Uri
    ): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Stream read error: ${e.message}")
            null
        }
    }
}
