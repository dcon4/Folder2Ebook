package com.folderpub.ebook

import com.folderpub.debug.DebugLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ReadabilityFormatter {

    private const val TAG = "ReadabilityFormatter"

    fun extractArticle(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            removeCruft(doc)
            val article = findArticle(doc)
            if (article != null) {
                cleanArticle(article)
                article.html()
            } else {
                doc.body().html()
            }
        } catch (e: Throwable) {
            DebugLogger.verbose(TAG, "Readability extraction failed: ${e.message}")
            Jsoup.parse(html).body().html()
        }
    }

    fun formatTextAsHtml(text: String): String {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        val sb = StringBuilder()
        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isEmpty()) continue
            val escaped = trimmed
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            sb.append("<p>$escaped</p>\n")
        }
        return sb.toString()
    }

    private fun findArticle(doc: Document): Element? {
        doc.select("article").first()?.let { return it }
        doc.select("[role=main]").first()?.let { return it }
        doc.select("main").first()?.let { return it }
        doc.select("body").first()?.let { body ->
            val candidates = body.children().filter { candidate ->
                val text = candidate.text()
                text.length > 200 && candidate.tagName() !in listOf("script", "style", "nav", "header", "footer")
            }
            return candidates.maxByOrNull { scoreElement(it) }
        }
        return null
    }

    private fun scoreElement(el: Element): Int {
        val textLen = el.text().length
        val linkLen = el.select("a").sumOf { it.text().length }
        val commas = el.text().count { it == ',' }
        return textLen - linkLen + (commas * 5)
    }

    private fun cleanArticle(el: Element) {
        el.select("script, style, nav, footer, header, aside, .sidebar, .nav, .footer, .header, .ad, .advertisement, .social-share, .share, .comments, .comment, .related, .recommended").remove()
        el.select("*").forEach { child ->
            val attr = child.attr("class")
            if (attr.contains("ad", ignoreCase = true) || attr.contains("sidebar", ignoreCase = true) || attr.contains("comment", ignoreCase = true) || attr.contains("share", ignoreCase = true) || attr.contains("nav", ignoreCase = true)) {
                child.remove()
            }
        }
        el.select("img[src~=(?i)\\.(svg|gif)]").remove()
    }

    private fun removeCruft(doc: Document) {
        doc.select("script, style, nav, footer, header, aside, noscript, iframe, form").remove()
        doc.select("*").forEach { el ->
            if (el.id().isNotEmpty()) {
                val id = el.id().lowercase()
                if (id in setOf("sidebar", "nav", "navigation", "menu", "footer", "header", "comments", "comment", "advertisement", "ads", "social", "share", "related-posts", "recommendations")) {
                    el.remove()
                }
            }
        }
    }
}