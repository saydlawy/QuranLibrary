package com.example.quranlibrary.download;

/**
 * واجهة استقبال التقدم من Python.
 * Python يستدعي onProgress(percent, status, message) مباشرة.
 */
public interface DownloadProgressCallback {

    /**
     * @param percent نسبة التقدم (0-100)
     * @param status الحالة: downloading / processing / error
     * @param message رسالة اختيارية
     */
    void onProgress(int percent, String status, String message);
}
