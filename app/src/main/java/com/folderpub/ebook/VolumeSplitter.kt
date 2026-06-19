package com.folderpub.ebook

import com.folderpub.debug.DebugLogger

data class Volume(
    val index: Int,
    val chapters: List<ChapterContent>,
    val pageEstimate: Int
)

object VolumeSplitter {

    private const val TAG = "VolumeSplitter"

    fun splitIntoVolumes(
        chapters: List<ChapterContent>,
        maxPagesPerVolume: Int
    ): List<Volume> {
        if (chapters.isEmpty()) return emptyList()

        val chapterPages = chapters.map { estimatePages(it) }
        val volumes = mutableListOf<Volume>()
        var currentChapters = mutableListOf<ChapterContent>()
        var currentPages = 0
        var volumeIndex = 0

        for (i in chapters.indices) {
            val chapter = chapters[i]
            val pages = chapterPages[i]

            if (currentPages + pages > maxPagesPerVolume && currentChapters.isNotEmpty()) {
                volumeIndex++
                volumes.add(
                    Volume(
                        index = volumeIndex,
                        chapters = currentChapters.toList(),
                        pageEstimate = currentPages
                    )
                )
                currentChapters = mutableListOf()
                currentPages = 0
            }

            currentChapters.add(chapter)
            currentPages += pages
        }

        if (currentChapters.isNotEmpty()) {
            volumeIndex++
            volumes.add(
                Volume(
                    index = volumeIndex,
                    chapters = currentChapters.toList(),
                    pageEstimate = currentPages
                )
            )
        }

        DebugLogger.log(
            TAG,
            "Split ${chapters.size} chapters into ${volumes.size} volumes (max $maxPagesPerVolume pages each)"
        )
        return volumes
    }

    private fun estimatePages(chapter: ChapterContent): Int {
        val textLength = chapter.bodyHtml.length
        val words = textLength / 6
        val lines = words / 12
        val pages = (lines / 40).coerceAtLeast(1)
        return pages
    }
}
