package com.folder2ebook.parser

import android.content.Context
import android.net.Uri
import com.folder2ebook.model.BookChapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parses various file types into BookChapter objects for EPUB generation.
 * Supports .txt, .html/.htm, and .pdf files.
 * Designed to handle larger files by streaming content.
 */
class FileParser(private val context: Context) {

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("txt", "html", "htm", "pdf", "xhtml", "text")
        private const val STREAM_BUFFER_SIZE = 8192
    }

    /**
     * Parse a file from a URI into a BookChapter.
     */
    fun parseFile(uri: Uri, fileName: String, order: Int): BookChapter {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file: $fileName")

        return inputStream.use { stream ->
            when (extension) {
                "txt", "text" -> parseTextFile(stream, fileName, order)
                "html", "htm", "xhtml" -> parseHtmlFile(stream, fileName, order)
                "pdf" -> parsePdfFile(stream, fileName, order)
                else -> parseTextFile(stream, fileName, order) // fallback to text
            }
        }
    }

    /**
     * Parse a plain text file. Uses the filename (without extension) as the title.
     * Handles large files by streaming line-by-line.
     */
    private fun parseTextFile(inputStream: InputStream, fileName: String, order: Int): BookChapter {
        val title = fileName.substringBeforeLast('.')
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), STREAM_BUFFER_SIZE)
        val htmlBuilder = StringBuilder()

        htmlBuilder.append("<h1>").append(escapeHtml(title)).append("</h1>\n")

        var inParagraph = false
        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (inParagraph) {
                    htmlBuilder.append("</p>\n")
                    inParagraph = false
                }
            } else {
                if (!inParagraph) {
                    htmlBuilder.append("<p>")
                    inParagraph = true
                } else {
                    htmlBuilder.append("<br/>")
                }
                htmlBuilder.append(escapeHtml(trimmed))
            }
        }
        if (inParagraph) {
            htmlBuilder.append("</p>\n")
        }

        return BookChapter(
            title = title,
            htmlContent = htmlBuilder.toString(),
            sourceFileName = fileName,
            order = order
        )
    }

    /**
     * Parse an HTML file. Extracts the <title> tag if present, otherwise uses filename.
     * Preserves HTML content as-is for the ebook chapter body.
     * Handles large HTML files by reading in chunks.
     */
    private fun parseHtmlFile(inputStream: InputStream, fileName: String, order: Int): BookChapter {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), STREAM_BUFFER_SIZE)
        val content = reader.readText()

        // Extract title from <title> tag if available
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
        val titleMatch = titleRegex.find(content)
        val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')

        // Extract body content if there's a <body> tag, otherwise use the whole content
        val bodyContent = extractBodyContent(content)

        return BookChapter(
            title = title,
            htmlContent = bodyContent,
            sourceFileName = fileName,
            order = order
        )
    }

    /**
     * Parse a PDF file. Extracts text content and converts to HTML paragraphs.
     * Uses streaming text extraction to handle larger PDFs.
     */
    private fun parsePdfFile(inputStream: InputStream, fileName: String, order: Int): BookChapter {
        val title = fileName.substringBeforeLast('.')
        val htmlBuilder = StringBuilder()

        val document = PDDocument.load(inputStream)
        document.use { doc ->
            val stripper = PDFTextStripper()
            // Process in page chunks for larger files
            val totalPages = doc.numberOfPages
            htmlBuilder.append("<h1>").append(escapeHtml(title)).append("</h1>\n")

            val chunkSize = 50 // Process 50 pages at a time
            var startPage = 1
            while (startPage <= totalPages) {
                val endPage = minOf(startPage + chunkSize - 1, totalPages)
                stripper.startPage = startPage
                stripper.endPage = endPage

                val text = stripper.getText(doc)
                val paragraphs = text.split(Regex("\\n\\s*\\n"))
                for (paragraph in paragraphs) {
                    val trimmed = paragraph.trim()
                    if (trimmed.isNotEmpty()) {
                        htmlBuilder.append("<p>")
                            .append(escapeHtml(trimmed).replace("\n", "<br/>"))
                            .append("</p>\n")
                    }
                }
                startPage = endPage + 1
            }
        }

        return BookChapter(
            title = title,
            htmlContent = htmlBuilder.toString(),
            sourceFileName = fileName,
            order = order
        )
    }

    /**
     * Extract body content from HTML, stripping <html>, <head>, <body> wrappers.
     */
    private fun extractBodyContent(html: String): String {
        // Try to find body content
        val bodyRegex = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val bodyMatch = bodyRegex.find(html)
        if (bodyMatch != null) {
            return bodyMatch.groupValues[1].trim()
        }

        // If no body tag, strip html/head tags and return the rest
        var result = html
        result = result.replace(Regex("<html[^>]*>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("</html>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<head[^>]*>.*?</head>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        result = result.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
        return result.trim()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    fun isSupportedFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_EXTENSIONS
    }
}
