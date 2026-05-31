package com.folder2ebook

import android.app.Application

class Folder2EbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox for Android
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }
}
