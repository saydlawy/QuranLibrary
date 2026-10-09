package com.example.quranlibrary.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.example.quranlibrary.data.model.DownloadStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppDatabaseMigrationTest {

    @Test
    public void migrationFromVersion3PreservesVideoAndAddsMetadataDefaults() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "migration-v3-test.db";
        context.deleteDatabase(name);

        SQLiteDatabase legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        try {
            legacy.execSQL("CREATE TABLE sections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "name TEXT, icon_key TEXT, sort_order INTEGER NOT NULL, "
                    + "is_default INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE videos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "section_id INTEGER NOT NULL, title TEXT, youtube_url TEXT, file_path TEXT, "
                    + "thumbnail_path TEXT, duration_ms INTEGER NOT NULL, size_bytes INTEGER NOT NULL, "
                    + "quality TEXT, download_status TEXT, progress INTEGER NOT NULL, error_message TEXT, "
                    + "watch_position_ms INTEGER NOT NULL, is_favorite INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL, FOREIGN KEY(section_id) REFERENCES sections(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            legacy.execSQL("CREATE INDEX index_videos_section_id ON videos(section_id)");
            legacy.execSQL("INSERT INTO sections (id, name, icon_key, sort_order, is_default, created_at) "
                    + "VALUES (1, 'القرآن الكريم', 'quran', 1, 1, 100)");
            legacy.execSQL("INSERT INTO videos (id, section_id, title, youtube_url, file_path, "
                    + "thumbnail_path, duration_ms, size_bytes, quality, download_status, progress, "
                    + "error_message, watch_position_ms, is_favorite, created_at) VALUES "
                    + "(7, 1, 'اختبار محفوظ', 'https://example.com/video', '/videos/test.mp4', "
                    + "NULL, 12345, 678, '720', 'COMPLETED', 100, NULL, 4500, 1, 200)");

            // Apply the exact SQL migration defined in AppDatabase.MIGRATION_3_4.
            legacy.execSQL("ALTER TABLE videos ADD COLUMN source_id TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN source_type TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN channel_name TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN thumbnail_url TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN published_at INTEGER");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN playlist_id TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN playlist_position INTEGER");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN downloaded_at INTEGER NOT NULL DEFAULT 0");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN sha256 TEXT");
            legacy.execSQL("ALTER TABLE videos ADD COLUMN metadata_status TEXT NOT NULL DEFAULT 'PENDING'");
            legacy.execSQL("CREATE INDEX IF NOT EXISTS index_videos_source_id ON videos(source_id)");
            legacy.setVersion(4);

            try (Cursor cursor = legacy.rawQuery(
                    "SELECT title, youtube_url, file_path, download_status, watch_position_ms, "
                            + "size_bytes, source_id, thumbnail_url, playlist_id, downloaded_at, "
                            + "metadata_status FROM videos WHERE id = 7", null)) {
                assertNotNull(cursor);
                assertEquals("Expected the existing video row to survive migration", 1,
                        cursor.getCount());
                cursor.moveToFirst();
                assertEquals("اختبار محفوظ", cursor.getString(0));
                assertEquals("https://example.com/video", cursor.getString(1));
                assertEquals("/videos/test.mp4", cursor.getString(2));
                assertEquals("COMPLETED", cursor.getString(3));
                assertEquals(4500L, cursor.getLong(4));
                assertEquals(678L, cursor.getLong(5));
                assertNull(cursor.getString(6));
                assertNull(cursor.getString(7));
                assertNull(cursor.getString(8));
                assertEquals(0L, cursor.getLong(9));
                assertEquals("PENDING", cursor.getString(10));
            }
        } finally {
            legacy.close();
            context.deleteDatabase(name);
        }
    }

    @Test
    public void roomMigratesVersion1ThroughVersion4WithoutDroppingSections() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "migration-v1-to-v4-test.db";
        context.deleteDatabase(name);

        SQLiteDatabase legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        try {
            legacy.execSQL("CREATE TABLE sections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "name TEXT, icon_key TEXT, sort_order INTEGER NOT NULL, "
                    + "is_default INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            legacy.execSQL("INSERT INTO sections "
                    + "(id, name, icon_key, sort_order, is_default, created_at) "
                    + "VALUES (9, 'قسم قديم', 'archive', 4, 0, 123)");
            legacy.setVersion(1);
        } finally {
            legacy.close();
        }

        AppDatabase migrated = androidx.room.Room.databaseBuilder(
                        context, AppDatabase.class, name)
                .addMigrations(AppDatabase.MIGRATION_1_2,
                        AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .allowMainThreadQueries()
                .build();
        try {
            try {
                migrated.getOpenHelper().getWritableDatabase();
            } catch (RuntimeException exception) {
                System.err.println("ROOM_MIGRATION_DIAGNOSTIC: " + exception);
                exception.printStackTrace(System.err);
                throw exception;
            }
            Section section = migrated.sectionDao().getSectionByName("قسم قديم");
            assertNotNull("Existing section should survive all migrations", section);
            assertEquals(9, section.id);
            assertEquals(0, migrated.videoDao().getAllVideosSnapshot().size());
        } finally {
            migrated.close();
            context.deleteDatabase(name);
        }
    }

    @Test
    public void newVideoStartsWithExplicitPendingMetadataState() {
        Video video = new Video(1, "عنوان تجريبي", "https://example.com/watch?v=abc");
        assertEquals(DownloadStatus.PENDING, video.downloadStatus);
        assertEquals("PENDING", video.metadataStatus);
        assertEquals(0L, video.downloadedAt);
        assertNull(video.sourceId);
        assertNull(video.channelName);
    }
}
