package com.example.quranlibrary.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.quranlibrary.data.model.DownloadStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class VideoDaoPlaylistDeduplicationTest {

    private AppDatabase database;
    private VideoDao videoDao;
    private int sectionId;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        videoDao = database.videoDao();
        sectionId = (int) database.sectionDao().insert(
                new Section("قائمة الاختبار", "test", 1, false));
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
    }

    @Test
    public void sourceIdQueryFindsDuplicateOnlyInsideSamePlaylist() {
        Video video = new Video(sectionId, "فيديو", "https://youtube.com/watch?v=abc");
        video.sourceId = "abc";
        video.playlistId = "PL_TEST";
        video.downloadStatus = DownloadStatus.COMPLETED;
        videoDao.insert(video);

        Video samePlaylist = videoDao.getBySourceIdSectionAndPlaylist(
                sectionId, "abc", "PL_TEST");
        Video otherPlaylist = videoDao.getBySourceIdSectionAndPlaylist(
                sectionId, "abc", "PL_OTHER");

        assertNotNull(samePlaylist);
        assertEquals("abc", samePlaylist.sourceId);
        assertEquals(DownloadStatus.COMPLETED, samePlaylist.downloadStatus);
        assertNull(otherPlaylist);
    }

    @Test
    public void urlQueryFindsDuplicateWhenSourceIdIsUnavailable() {
        String url = "https://example.com/watch/without-id";
        Video video = new Video(sectionId, "فيديو بلا معرّف", url);
        video.playlistId = "PL_URL";
        videoDao.insert(video);

        Video duplicate = videoDao.getByUrlSectionAndPlaylist(sectionId, url, "PL_URL");
        Video differentPlaylist = videoDao.getByUrlSectionAndPlaylist(
                sectionId, url, "PL_DIFFERENT");

        assertNotNull(duplicate);
        assertEquals(url, duplicate.youtubeUrl);
        assertNull(differentPlaylist);
    }
}
