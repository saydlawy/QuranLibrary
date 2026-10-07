package com.example.quranlibrary.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.quranlibrary.data.db.Section;
import com.example.quranlibrary.data.repository.SectionRepository;

import java.util.List;

/**
 * ViewModel للشاشة الرئيسية.
 * - يحتفظ بالبيانات عبر تغييرات دورة حياة النشاط (مثل دوران الشاشة).
 * - يعزل الواجهة عن مصدر البيانات (Repository).
 */
public class MainViewModel extends AndroidViewModel {

    private final SectionRepository repository;
    private final LiveData<List<Section>> allSections;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new SectionRepository(application);
        allSections = repository.getAllSections();
    }

    public LiveData<List<Section>> getAllSections() {
        return allSections;
    }

    public void addSection(String name) {
        if (name == null || name.trim().isEmpty()) return;
        Section section = new Section(
                name.trim(),
                "default",
                999,   // sort_order مؤقت، يُعاد ترتيبه لاحقًا
                false
        );
        repository.insert(section);
    }
}
