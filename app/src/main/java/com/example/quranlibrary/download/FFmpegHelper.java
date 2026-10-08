package com.example.quranlibrary.download;

import android.content.Context;
import android.util.Log;

import java.io.File;

public class FFmpegHelper {

    private static final String TAG = "FFmpegDebug";
    private static String cachedPath = null;

    public static synchronized String getFFmpegPath(Context context) {
        if (cachedPath != null) return cachedPath;

        // 1. nativeLibraryDir (المسار المفضّل للتنفيذ)
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        String result = search(nativeLibDir, 2);

        // 2. filesDir
        if (result.isEmpty()) {
            result = search(context.getFilesDir(), 3);
        }

        // 3. noBackupFilesDir
        if (result.isEmpty()) {
            result = search(context.getNoBackupFilesDir(), 3);
        }

        // 4. externalFilesDir
        if (result.isEmpty() && context.getExternalFilesDir(null) != null) {
            result = search(context.getExternalFilesDir(null), 3);
        }

        cachedPath = result;
        Log.d(TAG, "FINAL FFmpeg path = " + cachedPath);
        return cachedPath;
    }

    private static String search(File dir, int maxDepth) {
        if (dir == null || !dir.exists()) {
            Log.d(TAG, "search skip (not exists): " + (dir == null ? "null" : dir.getAbsolutePath()));
            return "";
        }
        Log.d(TAG, "searching in: " + dir.getAbsolutePath());
        return searchRecursive(dir, maxDepth, 0);
    }

    private static String searchRecursive(File dir, int maxDepth, int depth) {
        if (depth > maxDepth) return "";
        File[] files = dir.listFiles();
        if (files == null) return "";

        String found = "";
        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory()) {
                Log.d(TAG, indent(depth) + "[D] " + name);
                String sub = searchRecursive(f, maxDepth, depth + 1);
                if (!sub.isEmpty() && found.isEmpty()) found = sub;
            } else {
                Log.d(TAG, indent(depth) + "[F] " + name + " (size=" + f.length() + ", exec=" + f.canExecute() + ")");
                if (name.equals("ffmpeg") || name.equals("libffmpeg.so")) {
                    if (f.canExecute() || f.setExecutable(true)) {
                        if (found.isEmpty()) found = f.getAbsolutePath();
                    }
                }
            }
        }
        return found;
    }

    private static String indent(int d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d; i++) sb.append("  ");
        return sb.toString();
    }
}
