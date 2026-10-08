package com.example.quranlibrary.data.db;

import androidx.room.TypeConverter;

import com.example.quranlibrary.data.model.DownloadStatus;

/**
 * محولات الأنواع لـ Room.
 * Room لا يدعم enums مباشرة، فنحتاج تحويلها إلى String (أو int) قبل الحفظ.
 */
public class Converters {

    @TypeConverter
    public static String fromDownloadStatus(DownloadStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static DownloadStatus toDownloadStatus(String value) {
        return value == null ? null : DownloadStatus.valueOf(value);
    }
}
