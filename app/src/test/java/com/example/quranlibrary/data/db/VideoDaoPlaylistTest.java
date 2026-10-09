package com.example.quranlibrary.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.quranlibrary.data.model.DownloadStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoDaoPlaylistTest {

    private AppDatabase database;
    private VideoDao videoDao;
    private int sectionId;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        Section section = new Section("اختبار قائمة التشغيل", "test", 1, false);
        sectionId = (int) database.sectionDao().insert(section);
        videoDao = database.videoDao();
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
    }

    @Test
    public void lookupFindsVideoOnlyInMatchingPlaylistAndSection() {
        Video existing = new Video(sectionId, "فيديو", "https://youtube.com/watch?v=abc");
        existing.sourceId = "abc";
        existing.playlistId = "playlist-one";
        existing.downloadStatus = DownloadStatus.PENDING;
        videoDao.insert(existing);

        assertNotNull(videoDao.getBySourceIdSectionAndPlaylist(sectionId, "abc", "playlist-one"));
        assertNull(videoDao.getBySourceIdSectionAndPlaylist(sectionId, "abc", "playlist-two"));
    }

    @Test
    public void urlFallbackFindsVideoWithoutSourceIdInSamePlaylist() {
        String url = "https://example.com/video/1";
        Video existing = new Video(sectionId, "فيديو", url);
        existing.playlistId = "external-list";
        videoDao.insert(existing);

        assertNotNull(videoDao.getByUrlSectionAndPlaylist(sectionId, url, "external-list"));
        assertNull(videoDao.getByUrlSectionAndPlaylist(sectionId, url, "other-list"));
    }

    @Test
    public void playlistQueueReturnsPendingItemsByPosition() {
        Video later = new Video(sectionId, "الثاني", "https://youtube.com/watch?v=second");
        later.playlistId = "queue-list";
        later.playlistPosition = 2;
        videoDao.insert(later);

        Video first = new Video(sectionId, "الأول", "https://youtube.com/watch?v=first");
        first.playlistId = "queue-list";
        first.playlistPosition = 1;
        videoDao.insert(first);

        Video next = videoDao.getNextPendingPlaylistVideo("queue-list");
        assertNotNull(next);
        assertEquals("الأول", next.title);
        assertEquals(Integer.valueOf(1), next.playlistPosition);
    }
}
