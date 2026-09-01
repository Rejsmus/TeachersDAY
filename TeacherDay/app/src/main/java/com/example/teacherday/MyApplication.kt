package com.example.teacherday

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}