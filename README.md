# FolderPub

Throw files into a folder and make an ebook.

An Android app that scans a folder (including all subfolders) for HTML, text,
and PDF files, and combines them into an EPUB or PDF ebook. Each file becomes
a chapter. File names automatically become chapter titles in the table of
contents.

## Features

- **Folder scanning** -- Pick any folder on your device; the app scans all
  subfolders recursively for supported files.
- **Supported input** -- `.html`, `.htm`, `.txt`, and `.pdf` files.
- **Smart chapter titles** -- File names are analyzed to become readable
  chapter titles. Falls back to HTML `<title>` tags, then first line of
  content, then the raw filename.
- **EPUB output** (default) -- Standard EPUB 3 format, works in any ebook
  reader (iBooks, Google Play Books, KOReader, etc.).
- **PDF output** -- Generated with proper pagination. PDF input files are
  merged page-by-page when output is PDF.
- **Multi-volume splitting** -- Automatically splits large books into
  multiple volumes at chapter boundaries.
- **PDF text extraction** -- Text is extracted from PDF input files for
  EPUB output, with a warning if extraction quality is low.

## Build

```
git clone https://github.com/dcon4/Folder2Ebook.git
cd Folder2Ebook
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPLv3
