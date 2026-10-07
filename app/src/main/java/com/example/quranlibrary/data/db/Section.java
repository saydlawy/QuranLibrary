package com.example.quranlibrary.data.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * يمثل قسمًا في مكتبة الفيديوهات (مثل: القرآن الكريم، الحديث، الفقه).
 * قابل للتوسع: يحتوي على حقول للترتيب، الأيقونة، والتمييز بين الأقسام الافتراضية والمخصصة.
 */
@Entity(tableName = "sections")
public class Section {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "icon_key")
    public String iconKey;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @ColumnInfo(name = "is_default")
    public boolean isDefault;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * منشئ كامل يستخدم عند إضافة قسم جديد.
     */
    public Section(String name, String iconKey, int sortOrder, boolean isDefault) {
        this.name = name;
        this.iconKey = iconKey;
        this.sortOrder = sortOrder;
        this.isDefault = isDefault;
        this.createdAt = System.currentTimeMillis();
    }
}
