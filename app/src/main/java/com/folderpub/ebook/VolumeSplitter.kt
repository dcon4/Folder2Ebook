package com.folderpub.ebook

import com.folderpub.debug.DebugLogger

data class Volume(
    val index: Int,
    val chapters: List<ChapterContent>,
    val estimatedSizeBytes: Long
)

object VolumeSplitter {

    private const val TAG = "VolumeSplitter"
    private const val CHARS_PER_MB = 1_000_000
    private const val OVERHEAD_BYTES_PER_CHAPTER = 2_000

    fun splitIntoVolumes(
        chapters: List<ChapterContent>,
        maxMbPerVolume: Int
    ): List<Volume> {
        if (chapters.isEmpty()) return emptyList()
        val maxBytes = maxMbPerVolume * 1024L * 1024L

        val chapterSizes = chapters.map { estimateChapterBytes(it) }
        val volumes = mutableListOf<Volume>()
        var currentChapters = mutableListOf<ChapterContent>()
        var currentBytes = 0L
        var volumeIndex = 0

        for (i in chapters.indices) {
            val chapter = chapters[i]
            val size = chapterSizes[i]

            if (currentBytes + size > maxBytes && currentChapters.isNotEmpty()) {
                volumeIndex++
                volumes.add(
                    Volume(
                        index = volumeIndex,
                        chapters = currentChapters.toList(),
                        estimatedSizeBytes = currentBytes
                    )
                )
                currentChapters = mutableListOf()
                currentBytes = 0
            }

            currentChapters.add(chapter)
            currentBytes += size
        }

        if (currentChapters.isNotEmpty()) {
            volumeIndex++
            volumes.add(
                Volume(
                    index = volumeIndex,
                    chapters = currentChapters.toList(),
                    estimatedSizeBytes = currentBytes
                )
            )
        }

        DebugLogger.log(
            TAG,
            "Split ${chapters.size} chapters into ${volumes.size} volumes (max ${maxMbPerVolume}MB each)"
        )
        return volumes
    }

    private fun estimateChapterBytes(chapter: ChapterContent): Long {
        return (chapter.bodyHtml.length * 0.3).toLong() + OVERHEAD_BYTES_PER_CHAPTER
    }
}