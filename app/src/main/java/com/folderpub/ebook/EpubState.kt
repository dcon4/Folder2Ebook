package com.folderpub.ebook

import kotlinx.coroutines.flow.MutableStateFlow

object EpubState {

    val isScanning = MutableStateFlow(false)
    val isReading = MutableStateFlow(false)
    val isBuilding = MutableStateFlow(false)
    val scanResult = MutableStateFlow<ScanResult?>(null)
    val readDone = MutableStateFlow(0)
    val readTotal = MutableStateFlow(0)
    val chaptersCount = MutableStateFlow(0)
    val pdfWarnings = MutableStateFlow(0)
    val statusMessage = MutableStateFlow("Select a folder to begin")
    val buildProgress = MutableStateFlow("")

    fun reset() {
        scanResult.value = null
        readDone.value = 0
        readTotal.value = 0
        chaptersCount.value = 0
        pdfWarnings.value = 0
        buildProgress.value = ""
    }
}