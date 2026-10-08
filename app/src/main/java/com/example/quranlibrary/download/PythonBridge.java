package com.example.quranlibrary.download;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/**
 * واجهة Java رفيعة حول downloader.py.
 * يجب استدعاؤها من خيط خلفي (JobService/Executor)، وليس من الخيط الرئيسي.
 */
public final class PythonBridge {

    private static volatile boolean initialized = false;
    private static PyObject module;

    private PythonBridge() {}

    public static synchronized void init(Context context) {
        if (!initialized) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context.getApplicationContext()));
            }
            initialized = true;
        }
        if (module == null) {
            module = Python.getInstance().getModule("downloader");
        }
    }

    /**
     * جلب معلومات (بدون تحميل) كسلسلة JSON.
     * البنية: {ok, kind, title, count, entries[]} أو {ok:false, error}
     */
    public static String getInfo(String url, String mode) {
        if (module == null) return "{\"ok\":false,\"error\":\"Python not initialized\"}";
        return module.callAttr("get_info", url, mode).toString();
    }

    /**
     * تحميل فيديو واحد أو قائمة تشغيل.
     *
     * @param quality 0 = أفضل جودة، 1080/720/480/360 = حد أقصى للارتفاع، -1 = صوت فقط.
     * @param mode    "auto" أو "video" أو "playlist".
     * @param cb      كائن DownloadCallback (قد يكون null).
     * @return سلسلة JSON تحتوي النتيجة (ok, cancelled, kind, folder, downloaded[], failed[], warnings[], error).
     */
    public static String download(String url,
                                  String outDir,
                                  String ffmpegPath,
                                  int quality,
                                  String mode,
                                  DownloadCallback cb) {
        if (module == null) return "{\"ok\":false,\"error\":\"Python not initialized\"}";
        return module.callAttr("download",
                url,
                outDir,
                ffmpegPath == null ? "" : ffmpegPath,
                quality,
                mode,
                cb
        ).toString();
    }

    /**
     * إلغاء التحميل الحالي.
     */
    public static void cancel() {
        if (module != null) {
            try {
                module.callAttr("cancel");
            } catch (Exception ignored) {
            }
        }
    }
}
