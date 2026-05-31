package com.folder2ebook.epub

import com.folder2ebook.model.BookChapter
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a valid EPUB 3.0 file from a list of BookChapters.
 * EPUB is essentially a ZIP file with a specific structure:
 *   mimetype (uncompressed)
 *   META-INF/container.xml
 *   OEBPS/content.opf
 *   OEBPS/toc.xhtml (navigation document / Table of Contents)
 *   OEBPS/chapter_N.xhtml (one per chapter)
 *
 * Designed to handle larger books with many chapters efficiently.
 */
class EpubBuilder {

    companion object {
        private const val MIMETYPE = "application/epub+zip"
        private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
    }

    /**
     * Build an EPUB file from the given chapters.
     * @param chapters List of BookChapter objects in desired order
     * @param bookTitle Title for the ebook
     * @param author Author name
     * @param outputFile Destination file for the EPUB
     */
    fun buildEpub(
        chapters: List<BookChapter>,
        bookTitle: String,
        author: String,
        outputFile: File
    ) {
        val bookId = UUID.randomUUID().toString()

        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. mimetype must be first entry, stored (not compressed)
                writeMimetype(zos)

                // 2. META-INF/container.xml
                writeEntry(zos, "META-INF/container.xml", CONTAINER_XML)

                // 3. Write each chapter as XHTML
                for (chapter in chapters) {
                    val chapterFilename = "chapter_${chapter.order}.xhtml"
                    val chapterContent = buildChapterXhtml(chapter)
                    writeEntry(zos, "OEBPS/$chapterFilename", chapterContent)
                }

                // 4. Table of Contents (nav document)
                val tocContent = buildTocXhtml(chapters, bookTitle)
                writeEntry(zos, "OEBPS/toc.xhtml", tocContent)

                // 5. Stylesheet
                val css = buildStylesheet()
                writeEntry(zos, "OEBPS/style.css", css)

                // 6. content.opf (package document)
                val opfContent = buildContentOpf(chapters, bookTitle, author, bookId)
                writeEntry(zos, "OEBPS/content.opf", opfContent)
            }
        }
    }

    private fun writeMimetype(zos: ZipOutputStream) {
        val bytes = MIMETYPE.toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype")
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        val crc = CRC32()
        crc.update(bytes)
        entry.crc = crc.value
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun writeEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        entry.method = ZipEntry.DEFLATED
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildChapterXhtml(chapter: BookChapter): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>${escapeXml(chapter.title)}</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
${chapter.htmlContent}
</body>
</html>"""
    }

    private fun buildTocXhtml(chapters: List<BookChapter>, bookTitle: String): String {
        val navItems = chapters.joinToString("\n") { chapter ->
            """    <li><a href="chapter_${chapter.order}.xhtml">${escapeXml(chapter.title)}</a></li>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>${escapeXml(bookTitle)}</h1>
    <h2>Table of Contents</h2>
    <ol>
$navItems
    </ol>
  </nav>
</body>
</html>"""
    }

    private fun buildContentOpf(
        chapters: List<BookChapter>,
        bookTitle: String,
        author: String,
        bookId: String
    ): String {
        val manifestItems = chapters.joinToString("\n") { chapter ->
            """    <item id="chapter_${chapter.order}" href="chapter_${chapter.order}.xhtml" media-type="application/xhtml+xml"/>"""
        }

        val spineItems = chapters.joinToString("\n") { chapter ->
            """    <itemref idref="chapter_${chapter.order}"/>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:$bookId</dc:identifier>
    <dc:title>${escapeXml(bookTitle)}</dc:title>
    <dc:creator>${escapeXml(author)}</dc:creator>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">${java.time.LocalDateTime.now().toString().substring(0, 19)}Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="style" href="style.css" media-type="text/css"/>
$manifestItems
  </manifest>
  <spine>
    <itemref idref="nav"/>
$spineItems
  </spine>
</package>"""
    }

    private fun buildStylesheet(): String {
        return """body {
    font-family: Georgia, serif;
    margin: 1em;
    line-height: 1.6;
    color: #333;
}

h1 {
    font-size: 1.8em;
    margin-bottom: 0.5em;
    color: #222;
    border-bottom: 1px solid #ccc;
    padding-bottom: 0.3em;
}

h2 {
    font-size: 1.4em;
    margin-bottom: 0.4em;
    color: #444;
}

p {
    margin-bottom: 0.8em;
    text-align: justify;
}

nav ol {
    list-style-type: decimal;
    padding-left: 1.5em;
}

nav li {
    margin-bottom: 0.5em;
}

nav a {
    color: #0066cc;
    text-decoration: none;
}

nav a:hover {
    text-decoration: underline;
}
"""
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
