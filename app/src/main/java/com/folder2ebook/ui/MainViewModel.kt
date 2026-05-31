package com.folder2ebook.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.folder2ebook.epub.EpubBuilder
import com.folder2ebook.model.BookChapter
import com.folder2ebook.parser.FileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the main activity, managing folder selection and EPUB generation.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fileParser = FileParser(application)
    private val epubBuilder = EpubBuilder()

    // UI State
    data class UiState(
        val folderUri: Uri? = null,
        val folderName: String = "",
        val files: List<FileItem> = emptyList(),
        val bookTitle: String = "",
        val author: String = "",
        val isGenerating: Boolean = false,
        val progress: String = "",
        val generatedFile: File? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Handle folder selection from the document picker.
     */
    fun onFolderSelected(folderUri: Uri) {
        val context = getApplication<Application>()

        // Take persistable permissions
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(folderUri, flags)

        viewModelScope.launch(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            val files = documentFile?.listFiles()
                ?.filter { it.isFile && fileParser.isSupportedFile(it.name ?: "") }
                ?.sortedBy { it.name?.lowercase() }
                ?.map { file ->
                    FileItem(
                        fileName = file.name ?: "Unknown",
                        fileSize = file.length(),
                        fileType = file.name?.substringAfterLast('.', "") ?: ""
                    )
                } ?: emptyList()

            val folderName = documentFile?.name ?: "Selected Folder"
            val suggestedTitle = folderName.replace(Regex("[_-]"), " ")
                .replaceFirstChar { it.uppercase() }

            _uiState.value = _uiState.value.copy(
                folderUri = folderUri,
                folderName = folderName,
                files = files,
                bookTitle = suggestedTitle,
                error = null,
                generatedFile = null
            )
        }
    }

    fun onBookTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(bookTitle = title)
    }

    fun onAuthorChanged(author: String) {
        _uiState.value = _uiState.value.copy(author = author)
    }

    /**
     * Generate the EPUB from the selected folder.
     */
    fun generateEpub() {
        val state = _uiState.value
        val folderUri = state.folderUri ?: return

        if (state.bookTitle.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a book title")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                progress = "Reading files...",
                error = null,
                generatedFile = null
            )

            try {
                val chapters = withContext(Dispatchers.IO) {
                    parseAllFiles(folderUri)
                }

                if (chapters.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "No supported files found in the selected folder"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    progress = "Generating EPUB (${chapters.size} chapters)..."
                )

                val outputFile = withContext(Dispatchers.IO) {
                    val ebooksDir = File(getApplication<Application>().filesDir, "ebooks")
                    ebooksDir.mkdirs()
                    val safeTitle = state.bookTitle.replace(Regex("[^a-zA-Z0-9\\s-]"), "")
                        .trim().replace(Regex("\\s+"), "_")
                    val outputFile = File(ebooksDir, "${safeTitle}.epub")

                    epubBuilder.buildEpub(
                        chapters = chapters,
                        bookTitle = state.bookTitle,
                        author = state.author.ifBlank { "Unknown Author" },
                        outputFile = outputFile
                    )
                    outputFile
                }

                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    progress = "Done! EPUB created successfully.",
                    generatedFile = outputFile
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    progress = "",
                    error = "Error generating EPUB: ${e.message}"
                )
            }
        }
    }

    private fun parseAllFiles(folderUri: Uri): List<BookChapter> {
        val context = getApplication<Application>()
        val documentFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()

        val supportedFiles = documentFile.listFiles()
            .filter { it.isFile && fileParser.isSupportedFile(it.name ?: "") }
            .sortedBy { it.name?.lowercase() }

        val chapters = mutableListOf<BookChapter>()
        for ((index, file) in supportedFiles.withIndex()) {
            val fileName = file.name ?: continue
            val uri = file.uri

            try {
                val chapter = fileParser.parseFile(uri, fileName, index + 1)
                chapters.add(chapter)

                // Update progress
                _uiState.value = _uiState.value.copy(
                    progress = "Processing file ${index + 1} of ${supportedFiles.size}: $fileName"
                )
            } catch (e: Exception) {
                // Log but continue with other files
                _uiState.value = _uiState.value.copy(
                    progress = "Warning: Skipped $fileName (${e.message})"
                )
            }
        }
        return chapters
    }
}
