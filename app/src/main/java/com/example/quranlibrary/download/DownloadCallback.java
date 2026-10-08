package com.example.quranlibrary.download;

/**
 * واجهة الأحداث التي تستدعيها Python (على خيط خلفي).
 * يجب ألا ترمي استثناءات من داخل هذه الدوال.
 */
public interface DownloadCallback {

    /**
     * @param percent -1 عندما يكون الحجم الإجمالي غير معروف.
     * @param speed   سرعة التحميل بصيغة قابلة للقراءة (مثل "1.2 MB/s") أو "".
     * @param eta     الوقت المتبقي (مثل "1:30") أو "".
     * @param title   عنوان العنصر الحالي.
     * @param index   رقم العنصر (1-based).
     * @param total   إجمالي عدد العناصر.
     */
    void onProgress(int percent, String speed, String eta, String title, int index, int total);

    /**
     * عند اكتمال تحميل عنصر واحد.
     */
    void onItemFinished(int index, int total, String title, String path);

    /**
     * عند فشل تحميل عنصر واحد (لا يوقف بقية القائمة).
     */
    void onItemFailed(int index, int total, String title, String error);
}
