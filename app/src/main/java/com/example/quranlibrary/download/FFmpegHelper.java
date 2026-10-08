package com.example.quranlibrary.download;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * مساعد للحصول على مسار FFmpeg المُضمَّن في التطبيق.
 * - مكتبة youtubedl-android:ffmpeg تضع libffmpeg.so في nativeLibraryDir.
 * - يضمن أن الملف قابل للتنفيذ.
 * - يخزّن المسار مؤقتًا لتجنب الحساب المتكرر.
 */
public class FFmpegHelper {

    private static final String TAG = "FFmpegHelper";
    private static String cachedPath = null;

    public static synchronized String getFFmpegPath(Context context) {
        if (cachedPath != null) return cachedPath;

        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File ffmpeg = new File(nativeLibDir, "libffmpeg.so");

        if (!ffmpeg.exists()) {
            Log.e(TAG, "libffmpeg.so not found in nativeLibraryDir: " + nativeLibDir);
            cachedPath = "";
            return cachedPath;
        }

        if (!ffmpeg.canExecute()) {
            boolean ok = ffmpeg.setExecutable(true);
            Log.d(TAG, "setExecutable result: " + ok);
        }

        cachedPath = ffmpeg.getAbsolutePath();
        Log.d(TAG, "FFmpeg path: " + cachedPath);
        return cachedPath;
    }
}
