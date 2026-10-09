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
    public void sectionSnapshotPreservesPlaylistPositionBeforeStandaloneVideos() {
        Video second = new Video(sectionId, "الفيديو الثاني", "https://example.com/second");
        second.playlistId = "PL_ORDER";
        second.playlistPosition = 2;
        second.createdAt = 200L;
        videoDao.insert(second);

        Video first = new Video(sectionId, "الفيديو الأول", "https://example.com/first");
        first.playlistId = "PL_ORDER";
        first.playlistPosition = 1;
        first.createdAt = 100L;
        videoDao.insert(first);

        Video standalone = new Video(sectionId, "فيديو منفرد", "https://example.com/solo");
        standalone.createdAt = 300L;
        videoDao.insert(standalone);

        java.util.List<Video> ordered = videoDao.getVideosBySectionSnapshot(sectionId);

        assertEquals(3, ordered.size());
        assertEquals("الفيديو الأول", ordered.get(0).title);
        assertEquals("الفيديو الثاني", ordered.get(1).title);
        assertEquals("فيديو منفرد", ordered.get(2).title);
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
