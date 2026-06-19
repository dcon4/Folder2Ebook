package com.folderpub.ebook

import org.jsoup.Jsoup

object TitleExtractor {

    fun extractTitle(
        fileName: String,
        content: String,
        isHtml: Boolean
    ): String {
        val nameNoExt = fileName.substringBeforeLast(".").trim()

        val level1 = tryLevel1(nameNoExt)
        if (level1 != null) return level1

        if (isHtml) {
            val level2 = tryLevel2(content)
            if (level2 != null) return level2
        }

        val level3 = tryLevel3(content)
        if (level3 != null) return level3

        return fileName.trim()
    }

    private fun tryLevel1(nameNoExt: String): String? {
        val cleaned = nameNoExt.replace("_", " ").trim()
        if (looksLikeRealWords(cleaned)) {
            return cleaned
        }
        return null
    }

    private fun tryLevel2(content: String): String? {
        return try {
            val doc = Jsoup.parse(content)
            val title = doc.title().trim()
            if (title.isNotEmpty()) {
                if (title.length > 120) title.take(120) else title
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryLevel3(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        val firstLine = trimmed.lines().first().trim()
        if (firstLine.isEmpty()) return null
        return if (firstLine.length <= 80) {
            firstLine
        } else {
            firstLine.take(77) + "..."
        }
    }

    private fun looksLikeRealWords(s: String): Boolean {
        var letterCount = 0
        var digitCount = 0
        for (c in s) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' -> letterCount++
                c in '0'..'9' -> digitCount++
            }
        }
        return letterCount > digitCount * 2 && letterCount >= 3
    }
}
