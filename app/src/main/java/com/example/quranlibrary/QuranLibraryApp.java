package com.example.quranlibrary;

import android.app.Application;

import com.example.quranlibrary.download.PythonBridge;

import dagger.hilt.android.HiltAndroidApp;

/**
 * نقطة بداية التطبيق.
 * - @HiltAndroidApp: تُهيّئ Hilt لكل التطبيق.
 * - تهيّئ Python مرة واحدة قبل أي استخدام.
 */
@HiltAndroidApp
public class QuranLibraryApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // تهيئة Python مرة واحدة عند بدء التطبيق
        PythonBridge.init(this);
    }
}
