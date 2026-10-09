package com.example.quranlibrary.data.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.model.DownloadStatus;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LibraryTransferManagerTest {

    @Test
    public void safeChildAllowsFilesInsideStagingDirectory() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "library-transfer-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            File child = LibraryTransferManager.safeChild(root, "media/video_1.mp4");
            assertTrue(child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator));
        } finally {
            root.delete();
        }
    }

    @Test
    public void safeChildRejectsPathTraversal() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "library-transfer-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            LibraryTransferManager.safeChild(root, "../outside.txt");
            fail("Expected traversal path to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: canonical path must remain within the extraction root.
        } finally {
            root.delete();
        }
    }

    @Test
    public void extensionNormalizesSupportedFileExtensions() {
        assertEquals(".mp4", LibraryTransferManager.extension("lecture.MP4"));
        assertEquals(".m4a", LibraryTransferManager.extension("recitation.m4a"));
        assertEquals(".bin", LibraryTransferManager.extension("filename.invalidextensiontoolong"));
    }

    @Test
    public void videoMetadataRoundTripsThroughBackupManifest() throws Exception {
        Video original = new Video(7, "محاضرة تجريبية", "https://youtube.com/watch?v=abc123");
        original.durationMs = 987654L;
        original.sizeBytes = 456789L;
        original.quality = "1080p";
        original.downloadStatus = DownloadStatus.COMPLETED;
        original.progress = 100;
        original.watchPositionMs = 123456L;
        original.isFavorite = true;
        original.createdAt = 1700000000000L;
        original.sourceId = "abc123";
        original.sourceType = "youtube";
        original.channelName = "قناة تجريبية";
        original.thumbnailUrl = "https://example.com/thumb.jpg";
        original.publishedAt = 1690000000000L;
        original.playlistId = "playlist-42";
        original.playlistPosition = 3;
        original.downloadedAt = 1700001000000L;
        original.sha256 = "test-sha256";
        original.metadataStatus = "AVAILABLE";

        JSONObject manifestItem = LibraryTransferManager.videoToJson(original);
        Video restored = LibraryTransferManager.videoFromJson(manifestItem, 9);

        assertEquals(9, restored.sectionId);
        assertEquals(original.title, restored.title);
        assertEquals(original.youtubeUrl, restored.youtubeUrl);
        assertEquals(original.durationMs, restored.durationMs);
        assertEquals(original.sizeBytes, restored.sizeBytes);
        assertEquals(original.quality, restored.quality);
        assertEquals(original.downloadStatus, restored.downloadStatus);
        assertEquals(original.progress, restored.progress);
        assertEquals(original.watchPositionMs, restored.watchPositionMs);
        assertTrue(restored.isFavorite);
        assertEquals(original.sourceId, restored.sourceId);
        assertEquals(original.sourceType, restored.sourceType);
        assertEquals(original.channelName, restored.channelName);
        assertEquals(original.thumbnailUrl, restored.thumbnailUrl);
        assertEquals(original.publishedAt, restored.publishedAt);
        assertEquals(original.playlistId, restored.playlistId);
        assertEquals(original.playlistPosition, restored.playlistPosition);
        assertEquals(original.downloadedAt, restored.downloadedAt);
        assertEquals(original.sha256, restored.sha256);
        assertEquals(original.metadataStatus, restored.metadataStatus);
    }

    @Test
    public void unknownDownloadStatusFallsBackToPending() throws Exception {
        JSONObject item = new JSONObject();
        item.put("title", "فيديو");
        item.put("youtubeUrl", "https://example.com/video");
        item.put("downloadStatus", "UNRECOGNIZED_STATUS");

        Video restored = LibraryTransferManager.videoFromJson(item, 1);
        assertNotNull(restored);
        assertEquals(DownloadStatus.PENDING, restored.downloadStatus);
    }
}
