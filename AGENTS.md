# FolderPub -- Project AGENTS.md

This file is read by any AI agent on the first message of a session.
It documents project-specific conventions and critical context.

## Project overview

Android app that lets the user pick a folder of HTML/text/PDF files, scans
all subfolders recursively, and combines them into EPUB or PDF ebooks.
Each file becomes a chapter. File names become chapter titles via a
four-level smart title derivation.

## Chapter title derivation (TitleExtractor.extractTitle())

Every file's title goes through this four-level decision tree:

### Level 1: Real-word filename (checked first)
Strip extension, replace underscores with spaces. Check `looksLikeRealWords()`:
- Count letters (a-z, A-Z) and digits (0-9)
- If letter count > digit count * 2 AND letter count >= 3: use filename as title
- Examples: `OCR Camera Research.txt` -> "OCR Camera Research",
  `FreeText 1838544295 (1).html` -> falls through (more digits than letters)

### Level 2: HTML `<title>` tag (only if Level 1 failed AND file is .html/.htm)
Parse with Jsoup. If non-empty `<title>` exists, use it (max 120 chars).

### Level 3: First line of content
Trim content, take first line.
- If <= 80 chars: use as-is
- If > 80 chars: truncate to 77 + "..."

### Level 4: Filename with extension (last resort)
Used only if content is empty after trimming.

## Supported input file types

- `.html` / `.htm` -- parsed with Jsoup, body content extracted
- `.txt` -- read as UTF-8 plain text
- `.pdf` -- text extracted via pdfbox-android (may warn if extraction is poor)

## Output formats

- **EPUB 3** (default) -- ZIP-based ebook format with XHTML chapters, CSS, TOC
- **PDF** (option) -- generated with Android PdfDocument API.
  PDF input files are merged page-by-page using PDFBox.

## Multi-volume splitting

- Configurable max pages per volume (default 500)
- Splits at chapter boundaries only (never mid-chapter)
- Files named: `{book}_vol1.epub`, `{book}_vol2.epub`, etc.

## Key files

- `app/src/main/java/com/folderpub/ebook/TitleExtractor.kt` -- 4-level title logic
- `app/src/main/java/com/folderpub/ebook/ChapterScanner.kt` -- folder scanning, file discovery
- `app/src/main/java/com/folderpub/ebook/ContentReader.kt` -- read HTML/TXT/PDF
- `app/src/main/java/com/folderpub/ebook/EpubBuilder.kt` -- EPUB 3 generation
- `app/src/main/java/com/folderpub/ebook/PdfBuilder.kt` -- PDF output + merging
- `app/src/main/java/com/folderpub/ebook/VolumeSplitter.kt` -- volume splitting
- `app/src/main/java/com/folderpub/ui/MainScreen.kt` -- main UI
- `app/src/main/java/com/folderpub/ui/SettingsScreen.kt` -- settings
- `app/src/main/java/com/folderpub/debug/DebugLogger.kt` -- verbose logging + share

## Required features (must exist in every build)

1. In-app debug log share button (top bar bug-report icon)
2. Verbose logging toggle (Settings screen, persisted in DataStore)
3. CI workflow that builds debug APK + uploads as artifact on every push/PR

## CI

GitHub Actions at `.github/workflows/build.yml`:
- Triggers: push to main, PRs against main, workflow_dispatch
- Runs ./gradlew :app:assembleDebug
- Uploads APK as `debug-apk` artifact
- JDK 17, ubuntu-latest

## Non-negotiable rules

- User is blind / uses TalkBack. All UI elements MUST have content descriptions.
- No emoji in code, comments, commit messages, or UI.
- Screen reader accessible formatting: headings, bullet lists, tables.
- Never hardcode secrets or print them in output.
