package com.folderpub.ebook

import org.jsoup.Jsoup

object TitleExtractor {

    fun extractTitle(
        fileName: String,
        content: String,
        isHtml: Boolean
    ): String {
        if (isHtml) {
            val fromTag = tryHtmlTitle(content)
            if (fromTag != null) return fromTag
        }

        val fromName = cleanFilename(fileName)
        if (fromName != null) return fromName

        val fromContent = tryFirstSentence(content)
        if (fromContent != null) return fromContent

        return fileName.trim()
    }

    private fun tryHtmlTitle(content: String): String? {
        return try {
            val doc = Jsoup.parse(content)
            val title = doc.title().trim()
            if (title.isNotEmpty()) title.take(120) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanFilename(fileName: String): String? {
        val cleaned = fileName.substringBeforeLast(".").replace("_", " ").trim()
        if (cleaned.isEmpty()) return null
        return cleaned
    }

    private fun tryFirstSentence(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        var text = trimmed
        if (!text.contains("<")) {
            val firstLine = text.lines().first().trim()
            if (firstLine.isNotEmpty()) text = firstLine
        }
        if (text.length > 80) {
            val cutoff = text.indexOfAny(charArrayOf('.', '!', '?', '\n'))
            if (cutoff in 1..80) {
                return text.substring(0, cutoff + 1).trim()
            }
            return text.take(77) + "..."
        }
        return text
    }
}