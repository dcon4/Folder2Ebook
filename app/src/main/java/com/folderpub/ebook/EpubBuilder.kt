package com.folderpub.ebook

import android.content.Context
import android.net.Uri
import com.folderpub.debug.DebugLogger
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubBuilder {

    private const val TAG = "EpubBuilder"

    fun buildEpub(
        context: Context,
        chapters: List<ChapterContent>,
        outputStream: OutputStream,
        bookTitle: String
    ) {
        val zip = ZipOutputStream(outputStream)

        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            compressedSize = 20
            crc = 0x2BAB79F9
        })
        zip.write("application/epub+zip".toByteArray())
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("META-INF/"))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("META-INF/container.xml"))
        zip.write(containerXml().toByteArray())
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/"))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/styles/"))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/styles/ebook.css"))
        zip.write(css().toByteArray())
        zip.closeEntry()

        val chapterIds = mutableListOf<String>()
        for ((index, chapter) in chapters.withIndex()) {
            val id = "chapter_${index + 1}"
            chapterIds.add(id)
            val fileName = "chapter_${index + 1}.xhtml"
            zip.putNextEntry(ZipEntry("OEBPS/$fileName"))
            zip.write(chapterXhtml(chapter, id).toByteArray())
            zip.closeEntry()
        }

        zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
        zip.write(tocNcx(bookTitle, chapters, chapterIds).toByteArray())
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/toc.xhtml"))
        zip.write(tocXhtml(bookTitle, chapters, chapterIds).toByteArray())
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
        zip.write(contentOpf(bookTitle, chapters, chapterIds).toByteArray())
        zip.closeEntry()

        zip.finish()
        DebugLogger.log(TAG, "EPUB built: ${chapters.size} chapters, title=$bookTitle")
    }

    private fun containerXml(): String = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun css(): String = """body {
  font-family: serif;
  line-height: 1.6;
  margin: 1em 2em;
}
h1, h2, h3 {
  font-family: sans-serif;
}
pre {
  font-family: monospace;
  white-space: pre-wrap;
  font-size: 0.9em;
}
p {
  margin: 0.5em 0;
}
img {
  max-width: 100%;
  height: auto;
}"""

    private fun chapterXhtml(chapter: ChapterContent, id: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(chapter.title)}</title>
  <link rel="stylesheet" type="text/css" href="styles/ebook.css"/>
</head>
<body>
  <h1 id="$id">${escapeXml(chapter.title)}</h1>
  ${chapter.bodyHtml}
</body>
</html>"""

    private fun tocNcx(
        bookTitle: String,
        chapters: List<ChapterContent>,
        chapterIds: List<String>
    ): String {
        val navPoints = chapters.mapIndexed { index, chapter ->
            val id = chapterIds[index]
            val num = index + 1
            """    <navPoint id="nav_$id" playOrder="$num">
      <navLabel>
        <text>${escapeXml(chapter.title)}</text>
      </navLabel>
      <content src="$id.xhtml#$id"/>
    </navPoint>"""
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:${uuid()}"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle>
    <text>${escapeXml(bookTitle)}</text>
  </docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
    }

    private fun tocXhtml(
        bookTitle: String,
        chapters: List<ChapterContent>,
        chapterIds: List<String>
    ): String {
        val items = chapters.mapIndexed { index, chapter ->
            val id = chapterIds[index]
            """    <li><a href="${id}.xhtml#$id">${escapeXml(chapter.title)}</a></li>"""
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="styles/ebook.css"/>
</head>
<body>
  <h1>Table of Contents</h1>
  <ul>
$items
  </ul>
</body>
</html>"""
    }

    private fun contentOpf(
        bookTitle: String,
        chapters: List<ChapterContent>,
        chapterIds: List<String>
    ): String {
        val uid = "urn:uuid:${uuid()}"

        val manifestItems = buildString {
            appendLine("""    <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            appendLine("""    <item id="css" href="styles/ebook.css" media-type="text/css"/>""")
            for ((index, id) in chapterIds.withIndex()) {
                val file = "${id}.xhtml"
                appendLine("""    <item id="$id" href="$file" media-type="application/xhtml+xml"/>""")
            }
        }

        val spineItems = buildString {
            appendLine("""    <itemref idref="toc"/>""")
            for (id in chapterIds) {
                appendLine("""    <itemref idref="$id"/>""")
            }
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">$uid</dc:identifier>
    <dc:title>${escapeXml(bookTitle)}</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">${nowIso()}</meta>
  </metadata>
  <manifest>
$manifestItems  </manifest>
  <spine toc="ncx">
$spineItems  </spine>
</package>"""
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun uuid(): String {
        return java.util.UUID.randomUUID().toString()
    }

    private fun nowIso(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .format(java.util.Date())
    }
}
