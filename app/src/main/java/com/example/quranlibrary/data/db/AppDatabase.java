package com.example.quranlibrary.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * قاعدة بيانات التطبيق الرئيسية.
 * - نمط Singleton.
 * - تهيئة الأقسام الافتراضية عند أول إنشاء.
 * - TypeConverters لدعم enums.
 */
@Database(
        entities = {Section.class, Video.class},
        version = 3,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract SectionDao sectionDao();
    public abstract VideoDao videoDao();

    private static volatile AppDatabase INSTANCE;
    private static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "quran_library_db")
                            .addCallback(seedCallback)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback seedCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                SectionDao dao = INSTANCE.sectionDao();
                if (dao.getCount() == 0) {
                    dao.insertAll(defaultSections());
                }
            });
        }
    };

    private static List<Section> defaultSections() {
        List<Section> list = new ArrayList<>();
        list.add(new Section("القرآن الكريم", "quran", 1, true));
        list.add(new Section("الحديث الشريف", "hadith", 2, true));
        list.add(new Section("الفقه", "fiqh", 3, true));
        return list;
    }

    public static ExecutorService getWriteExecutor() {
        return databaseWriteExecutor;
    }
}
