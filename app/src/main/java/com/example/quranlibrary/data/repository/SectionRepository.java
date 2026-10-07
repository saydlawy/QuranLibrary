package com.example.quranlibrary.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Section;
import com.example.quranlibrary.data.db.SectionDao;

import java.util.List;

/**
 * Repository للأقسام. يمثل نقطة الوصول الوحيدة للبيانات من طبقة الواجهة.
 * - يجرد مصدر البيانات (Room) عن ViewModel.
 * - يسهل التبديل لمصدر بيانات آخر لاحقًا (مثل شبكة) دون تغيير الواجهة.
 */
public class SectionRepository {

    private final SectionDao sectionDao;
    private final LiveData<List<Section>> allSections;

    public SectionRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        sectionDao = db.sectionDao();
        allSections = sectionDao.getAllSections();
    }

    /**
     * إرجاع جميع الأقسام كـ LiveData (تفاعلي).
     */
    public LiveData<List<Section>> getAllSections() {
        return allSections;
    }

    /**
     * إضافة قسم جديد في الخلفية.
     */
    public void insert(Section section) {
        AppDatabase.getWriteExecutor().execute(() -> sectionDao.insert(section));
    }

    /**
     * تحديث قسم موجود في الخلفية.
     */
    public void update(Section section) {
        AppDatabase.getWriteExecutor().execute(() -> sectionDao.update(section));
    }

    /**
     * حذف قسم في الخلفية.
     */
    public void delete(Section section) {
        AppDatabase.getWriteExecutor().execute(() -> sectionDao.delete(section));
    }
}
