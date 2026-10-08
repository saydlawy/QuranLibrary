package com.example.quranlibrary.download;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * الجسر بين Java و Python.
 * - يهيّئ Python مرة واحدة.
 * - ينفّذ عمليات التحميل في Thread منفصل.
 * - يحوّل نتيجة Python (dict) إلى كائن Java.
 */
public class PythonBridge {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile boolean initialized = false;

    /**
     * تهيئة Python. يجب استدعاؤها مرة واحدة قبل أي عملية تحميل.
     */
    public static synchronized void init(Context context) {
        if (!initialized) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context.getApplicationContext()));
            }
            initialized = true;
        }
    }

    public interface DownloadResultCallback {
        void onComplete(DownloadResult result);
        void onError(String message);
    }

    /**
     * نتيجة التحميل كما تعيدها Python.
     */
    public static class DownloadResult {
        public boolean success;
        public String filePath;
        public String title;
        public long durationMs;
        public long sizeBytes;
        public String error;
    }

    /**
     * يشغّل التحميل في الخلفية.
     */
    public static void download(String url,
                                String outputDir,
                                DownloadProgressCallback progressCallback,
                                DownloadResultCallback resultCallback) {
        EXECUTOR.execute(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("downloader");
                PyObject result = module.callAttr("download", url, outputDir, progressCallback);

                DownloadResult dr = parseResult(result);

                if (resultCallback != null) {
                    resultCallback.onComplete(dr);
                }
            } catch (Exception e) {
                if (resultCallback != null) {
                    String msg = e.getMessage() == null ? "unknown error" : e.getMessage();
                    resultCallback.onError(msg);
                }
            }
        });
    }

    /**
     * يحوّل PyObject (dict) إلى DownloadResult.
     */
    private static DownloadResult parseResult(PyObject result) {
        DownloadResult dr = new DownloadResult();
        Map<PyObject, PyObject> map = result.asMap();
        for (Map.Entry<PyObject, PyObject> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            PyObject value = entry.getValue();
            switch (key) {
                case "success":
                    dr.success = value.toJava(Boolean.class);
                    break;
                case "file_path":
                    dr.filePath = value.toString();
                    break;
                case "title":
                    dr.title = value.toString();
                    break;
                case "duration_ms":
                    dr.durationMs = value.asLong();
                    break;
                case "size_bytes":
                    dr.sizeBytes = value.asLong();
                    break;
                case "error":
                    dr.error = value.toString();
                    break;
            }
        }
        return dr;
    }
}
