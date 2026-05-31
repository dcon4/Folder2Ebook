package com.folder2ebook.model

/**
 * Represents a single chapter in the ebook, derived from one source file.
 */
data class BookChapter(
    val title: String,
    val htmlContent: String,
    val sourceFileName: String,
    val order: Int
)
