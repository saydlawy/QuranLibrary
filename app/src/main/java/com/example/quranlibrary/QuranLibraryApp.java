package com.example.quranlibrary;

import android.app.Application;
import android.util.Log;

import com.example.quranlibrary.download.PythonBridge;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class QuranLibraryApp extends Application {

    private static final String TAG = "QuranLibraryApp";

    @Override
    public void onCreate() {
        super.onCreate();
        PythonBridge.init(this);

        // تهيئة youtubedl-android (تستخرج FFmpeg وPython وyt-dlp)
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                Log.d(TAG, "youtubedl-android initialized successfully");
            } catch (YoutubeDLException e) {
                Log.e(TAG, "youtubedl-android init failed", e);
            }
        }).start();
    }
}
