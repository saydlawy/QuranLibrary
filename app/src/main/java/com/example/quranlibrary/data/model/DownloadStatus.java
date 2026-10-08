package com.example.quranlibrary.data.model;

/**
 * حالات التحميل الممكنة.
 * تُستخدم في قاعدة البيانات لعرض الحالة الحالية لكل فيديو.
 */
public enum DownloadStatus {
    /** لم يبدأ التحميل بعد */
    PENDING,
    /** جاري التحميل */
    DOWNLOADING,
    /** تم الإيقاف مؤقتًا (يدويًا أو بسبب انقطاع الشبكة) */
    PAUSED,
    /** مكتمل بنجاح */
    COMPLETED,
    /** فشل التحميل */
    FAILED,
    /** تم الإلغاء بواسطة المستخدم */
    CANCELLED
}
