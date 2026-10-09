package com.example.quranlibrary.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * قاعدة بيانات التطبيق الرئيسية.
 * تهيئة الأقسام الافتراضية تتم عند إنشاء قاعدة جديدة.
 * الترقية من الإصدار 3 إلى 4 تحفظ السجلات وتضيف بيانات المصدر الوصفية.
 */
@Database(
        entities = {Section.class, Video.class},
        version = 4,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS videos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "section_id INTEGER NOT NULL, title TEXT, youtube_url TEXT, "
                    + "file_path TEXT, thumbnail_path TEXT, duration_ms INTEGER NOT NULL, "
                    + "size_bytes INTEGER NOT NULL, quality TEXT, download_status TEXT, "
                    + "progress INTEGER NOT NULL, watch_position_ms INTEGER NOT NULL, "
                    + "is_favorite INTEGER NOT NULL, created_at INTEGER NOT NULL, "
                    + "FOREIGN KEY(section_id) REFERENCES sections(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_videos_section_id ON videos(section_id)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE videos ADD COLUMN error_message TEXT");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE videos ADD COLUMN source_id TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN source_type TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN channel_name TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN thumbnail_url TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN published_at INTEGER");
            db.execSQL("ALTER TABLE videos ADD COLUMN playlist_id TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN playlist_position INTEGER");
            db.execSQL("ALTER TABLE videos ADD COLUMN downloaded_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE videos ADD COLUMN sha256 TEXT");
            db.execSQL("ALTER TABLE videos ADD COLUMN metadata_status TEXT DEFAULT 'PENDING'");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_videos_source_id ON videos(source_id)");
        }
    };

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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .addCallback(seedCallback)
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
                AppDatabase instance = INSTANCE;
                if (instance == null) {
                    return;
                }
                SectionDao dao = instance.sectionDao();
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
