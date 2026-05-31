# Folder2Ebook

An Android app that combines multiple files (text, HTML, PDF) from a single folder into a well-formatted EPUB ebook with an auto-generated Table of Contents.

## Features

- **Multi-format support**: Reads `.txt`, `.html`, `.htm`, `.xhtml`, and `.pdf` files
- **Smart title extraction**: Uses HTML `<title>` tags when available, falls back to filename
- **Auto-generated Table of Contents**: Every file becomes a chapter with a navigable TOC entry
- **Large file handling**: Streaming parsers and chunked PDF processing — no arbitrary size limits
- **EPUB 3.0 compliant**: Generated ebooks work with all major e-readers (Kindle via Send-to-Kindle, Google Play Books, Apple Books, Kobo, etc.)
- **Share functionality**: Share the generated EPUB via any app (email, cloud storage, etc.)
- **Material Design UI**: Clean, modern interface with file previews

## How It Works

1. **Select a Folder** — Use the built-in folder picker to choose a directory containing your files
2. **Preview Files** — See all supported files that will be included, with type and size info
3. **Set Metadata** — Enter a book title and optional author name
4. **Generate** — Tap "Generate EPUB" and watch the progress
5. **Share** — Share the resulting `.epub` file to any app

## Supported File Types

| Type | Extensions | Title Source |
|------|-----------|--------------|
| Plain Text | `.txt`, `.text` | Filename (minus extension) |
| HTML | `.html`, `.htm`, `.xhtml` | `<title>` tag or filename |
| PDF | `.pdf` | Filename (minus extension) |

## Architecture

```
com.folder2ebook/
├── Folder2EbookApp.kt          # Application class (PDFBox init)
├── model/
│   └── BookChapter.kt          # Data model for chapters
├── parser/
│   └── FileParser.kt           # Multi-format file parser
├── epub/
│   └── EpubBuilder.kt          # EPUB 3.0 generator
└── ui/
    ├── MainActivity.kt         # Main UI with folder picker
    ├── MainViewModel.kt        # Business logic & state management
    └── FileListAdapter.kt      # RecyclerView adapter for file list
```

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Steps
1. Clone this repository
2. Open in Android Studio
3. Sync Gradle
4. Run on device/emulator (API 26+)

## Dependencies

- **AndroidX**: Core KTX, AppCompat, Material, ConstraintLayout, DocumentFile
- **PdfBox-Android**: PDF text extraction (`com.tom-roush:pdfbox-android:2.0.27.0`)
- **Kotlin Coroutines**: Async file processing

## Design Decisions

- **No file size limits**: Uses streaming I/O and chunked processing so large PDFs and text files won't cause OOM
- **Storage Access Framework**: Uses `ACTION_OPEN_DOCUMENT_TREE` for folder access — works on all Android 8+ devices without special permissions
- **EPUB 3.0**: Modern standard with built-in navigation support
- **Files sorted alphabetically**: Predictable chapter ordering (tip: prefix files with numbers like `01_intro.txt`)

## License

MIT
