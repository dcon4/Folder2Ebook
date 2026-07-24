package com.folderpub.ebook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.folderpub.debug.DebugLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume

object ReadabilityFormatter {

    private const val TAG = "ReadabilityFormatter"
    private const val PAGE_TIMEOUT_MS = 30_000L
    private const val JS_EVAL_TIMEOUT_MS = 30_000L

    private var readabilityJs: String? = null

    private fun getReadabilityJs(context: Context): String {
        if (readabilityJs == null) {
            try {
                val stream = context.assets.open("readability.js")
                val reader = BufferedReader(InputStreamReader(stream))
                readabilityJs = reader.readText()
                reader.close()
                DebugLogger.verbose(TAG, "Readability.js loaded (${readabilityJs!!.length} chars)")
            } catch (e: Exception) {
                DebugLogger.log(TAG, "Failed to load readability.js: ${e.message}")
                readabilityJs = ""
            }
        }
        return readabilityJs!!
    }

    suspend fun extractArticle(context: Context, html: String, fallbackTitle: String): Pair<String, String> {
        val readability = getReadabilityJs(context)
        if (readability.isBlank()) {
            DebugLogger.verbose(TAG, "readability.js unavailable, falling back to Jsoup")
            return extractWithJsoup(html, fallbackTitle)
        }

        val extractionJs = readability + """
            (function() {
                try {
                    var article = new Readability(document).parse();
                    if (article && article.content) {
                        return JSON.stringify({
                            title: (article.title || '').trim(),
                            content: article.content
                        });
                    }
                    return JSON.stringify({title: '', content: ''});
                } catch(e) {
                    return JSON.stringify({title: '', content: ''});
                }
            })();
        """.trimIndent()

        return try {
            withTimeout(PAGE_TIMEOUT_MS + JS_EVAL_TIMEOUT_MS) {
                extractWithWebView(context, extractionJs, html, fallbackTitle)
            }
        } catch (e: Throwable) {
            DebugLogger.verbose(TAG, "WebView extraction failed: ${e.message}, falling back to Jsoup")
            extractWithJsoup(html, fallbackTitle)
        }
    }

    private suspend fun extractWithWebView(
        context: Context,
        extractionJs: String,
        html: String,
        fallbackTitle: String
    ): Pair<String, String> {
        return suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var done = false

            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.6367.113 Mobile Safari/537.36"
            }

            val pageTimeout = Runnable {
                if (done) return@Runnable
                done = true
                DebugLogger.verbose(TAG, "WebView page timeout")
                wv.destroy()
                if (!continuation.isCancelled) continuation.resume(Pair(fallbackTitle, ""))
            }
            mainHandler.postDelayed(pageTimeout, PAGE_TIMEOUT_MS)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (done) return
                    if (url.isBlank() || url == "about:blank") return
                    done = true
                    mainHandler.removeCallbacks(pageTimeout)

                    var jsDone = false
                    val jsTimeout = Runnable {
                        if (jsDone) return@Runnable
                        jsDone = true
                        DebugLogger.verbose(TAG, "WebView JS timeout")
                        wv.destroy()
                        if (!continuation.isCancelled) continuation.resume(Pair(fallbackTitle, ""))
                    }
                    mainHandler.postDelayed(jsTimeout, JS_EVAL_TIMEOUT_MS)

                    view.evaluateJavascript(extractionJs) { result ->
                        if (jsDone) return@evaluateJavascript
                        jsDone = true
                        mainHandler.removeCallbacks(jsTimeout)
                        wv.destroy()
                        try {
                            val inner = JSONTokener(result ?: "\"\"").nextValue().toString()
                            val json = JSONObject(inner)
                            val title = json.optString("title", "").ifBlank { fallbackTitle }
                            val content = json.optString("content", "")
                            if (content.isNotBlank()) {
                                DebugLogger.verbose(TAG, "WebView extraction OK: ${content.length} chars")
                                if (!continuation.isCancelled) continuation.resume(Pair(title, cleanForXhtml(content)))
                            } else {
                                DebugLogger.verbose(TAG, "WebView extraction empty, falling back to Jsoup")
                                if (!continuation.isCancelled) continuation.resume(extractWithJsoup("", fallbackTitle))
                            }
                        } catch (e: Exception) {
                            DebugLogger.verbose(TAG, "WebView JS parse error: ${e.message}")
                            if (!continuation.isCancelled) continuation.resume(extractWithJsoup("", fallbackTitle))
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, code: Int, desc: String?, failUrl: String?) {
                    if (done) return
                    done = true
                    mainHandler.removeCallbacks(pageTimeout)
                    wv.destroy()
                    if (!continuation.isCancelled) continuation.resume(Pair(fallbackTitle, ""))
                }
            }

            wv.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacks(pageTimeout)
                try { wv.destroy() } catch (_: Throwable) {}
            }
        }
    }

    private fun extractWithJsoup(html: String, fallbackTitle: String): Pair<String, String> {
        if (html.isBlank()) return Pair(fallbackTitle, "")
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, nav, footer, header, aside, form, iframe, noscript, svg, canvas, video, audio, picture, source").remove()
            var title = doc.select("h1").firstOrNull()?.text()?.trim()
            if (title.isNullOrBlank()) title = doc.title().trim()
            if (title.isNullOrBlank()) title = fallbackTitle

            val body = doc.body() ?: return Pair(fallbackTitle, "")
            val contentEl = doc.select("article").firstOrNull()
                ?: body.children().toList().filter { it.text().length > 200 }.maxByOrNull { it.text().length }
                ?: body

            Pair(title, cleanForXhtml(contentEl.html()))
        } catch (e: Exception) {
            DebugLogger.verbose(TAG, "Jsoup fallback error: ${e.message}")
            Pair(fallbackTitle, "")
        }
    }

    private fun cleanForXhtml(html: String): String {
        if (html.isBlank()) return html
        return try {
            val doc = Jsoup.parseBodyFragment(html)

            doc.select("picture").forEach { picture ->
                val img = picture.select("img").firstOrNull()
                if (img != null) {
                    picture.replaceWith(img)
                } else {
                    picture.remove()
                }
            }

            doc.select("source, video, audio, canvas, svg, noscript, iframe, form, style, script").remove()

            doc.select("img").forEach { img ->
                img.removeAttr("srcset")
                img.removeAttr("srcset")
                img.removeAttr("loading")
                img.removeAttr("decoding")
                img.removeAttr("fetchpriority")
            }

            doc.body().html()
        } catch (e: Exception) {
            DebugLogger.verbose(TAG, "cleanForXhtml error: ${e.message}")
            html
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
}