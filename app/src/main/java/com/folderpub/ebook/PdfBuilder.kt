package com.folderpub.ebook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.folderpub.debug.DebugLogger
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PdfBuilder {

    private const val TAG = "PdfBuilder"

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 56f
    private const val LINE_HEIGHT = 16f
    private const val TITLE_SIZE = 24f
    private const val BODY_SIZE = 11f
    private const val INDENT = 20f

    fun buildPdf(
        context: Context,
        chapters: List<ChapterContent>,
        inputPdfUris: List<Uri>,
        outputStream: OutputStream,
        bookTitle: String
    ) {
        val hasInputPdfs = inputPdfUris.isNotEmpty()

        if (hasInputPdfs) {
            mergeWithGenerated(
                context = context,
                generatedChapters = chapters,
                inputPdfUris = inputPdfUris,
                outputStream = outputStream,
                bookTitle = bookTitle
            )
        } else {
            buildFromChapters(
                chapters = chapters,
                outputStream = outputStream,
                bookTitle = bookTitle
            )
        }
    }

    private fun buildFromChapters(
        chapters: List<ChapterContent>,
        outputStream: OutputStream,
        bookTitle: String
    ) {
        val document = PdfDocument()

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = TITLE_SIZE
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = BODY_SIZE
            isAntiAlias = true
        }

        for (chapter in chapters) {
            val text = stripHtml(chapter.bodyHtml)
            val lines = wrapText(text, PAGE_WIDTH - MARGIN * 2, bodyPaint)

            val titleLines = wrapText(chapter.title, PAGE_WIDTH - MARGIN * 2, titlePaint)
            val totalLines = titleLines.size + 1 + lines.size
            val linesPerPage = ((PAGE_HEIGHT - MARGIN * 2 - TITLE_SIZE - 20) / LINE_HEIGHT).toInt()
            val pageCount = (totalLines / linesPerPage) + 1

            var lineIndex = 0
            var titleDrawn = false

            for (pageNum in 0 until pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                var y = MARGIN + TITLE_SIZE

                if (!titleDrawn) {
                    for (tl in titleLines) {
                        canvas.drawText(tl, MARGIN, y, titlePaint)
                        y += (TITLE_SIZE * 1.3f)
                    }
                    y += 10f
                    titleDrawn = true
                }

                val availableLines = ((PAGE_HEIGHT - y - MARGIN) / LINE_HEIGHT).toInt()
                var drawn = 0
                while (lineIndex < lines.size && drawn < availableLines) {
                    canvas.drawText(lines[lineIndex], MARGIN + INDENT, y, bodyPaint)
                    y += LINE_HEIGHT
                    lineIndex++
                    drawn++
                }

                document.finishPage(page)
            }
        }

        try {
            document.writeTo(outputStream)
            DebugLogger.log(TAG, "PDF built: ${chapters.size} chapters")
        } catch (e: Exception) {
            DebugLogger.log(TAG, "PDF write error: ${e.message}")
            throw e
        } finally {
            document.close()
        }
    }

    private fun mergeWithGenerated(
        context: Context,
        generatedChapters: List<ChapterContent>,
        inputPdfUris: List<Uri>,
        outputStream: OutputStream,
        bookTitle: String
    ) {
        val tempDir = File(context.cacheDir, "ebooks")
        tempDir.mkdirs()

        val generatedPdf = File(tempDir, "generated_temp.pdf")
        FileOutputStream(generatedPdf).use { fos ->
            buildFromChapters(generatedChapters, fos, bookTitle)
        }

        try {
            val merger = PDFMergerUtility()
            merger.destinationStream = outputStream

            merger.addSource(generatedPdf)

            for (uri in inputPdfUris) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        merger.addSource(inputStream)
                    }
                } catch (e: Exception) {
                    DebugLogger.log(TAG, "Failed to add PDF source $uri: ${e.message}")
                }
            }

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            DebugLogger.log(
                TAG,
                "PDF merged: ${generatedChapters.size} generated chapters + ${inputPdfUris.size} input PDFs"
            )
        } catch (e: Exception) {
            DebugLogger.log(TAG, "PDF merge error: ${e.message}")
            throw e
        } finally {
            generatedPdf.delete()
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
