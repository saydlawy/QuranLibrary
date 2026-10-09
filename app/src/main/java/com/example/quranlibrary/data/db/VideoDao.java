package com.example.quranlibrary.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.quranlibrary.data.model.DownloadStatus;

import java.util.List;

@Dao
public interface VideoDao {

    @Query("SELECT * FROM videos WHERE section_id = :sectionId ORDER BY created_at DESC")
    LiveData<List<Video>> getVideosBySection(int sectionId);

    @Query("SELECT * FROM videos ORDER BY created_at DESC")
    LiveData<List<Video>> getAllVideos();

    @Query("SELECT * FROM videos ORDER BY id ASC")
    List<Video> getAllVideosSnapshot();

    @Query("SELECT * FROM videos WHERE section_id = :sectionId AND youtube_url = :url LIMIT 1")
    Video getByUrlAndSection(int sectionId, String url);

    @Query("SELECT * FROM videos WHERE section_id = :sectionId AND source_id = :sourceId LIMIT 1")
    Video getBySourceIdAndSection(int sectionId, String sourceId);

    @Query("SELECT * FROM videos WHERE section_id = :sectionId AND source_id = :sourceId AND playlist_id = :playlistId LIMIT 1")
    Video getBySourceIdSectionAndPlaylist(int sectionId, String sourceId, String playlistId);

    @Query("SELECT * FROM videos WHERE section_id = :sectionId AND youtube_url = :url AND playlist_id = :playlistId LIMIT 1")
    Video getByUrlSectionAndPlaylist(int sectionId, String url, String playlistId);

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    Video getVideoById(int id);

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    LiveData<Video> getVideoByIdLive(int id);

    @Query("SELECT COUNT(*) FROM videos WHERE section_id = :sectionId")
    int getCountBySection(int sectionId);

    @Query("SELECT * FROM videos WHERE download_status IN ('PENDING', 'DOWNLOADING', 'PAUSED')")
    List<Video> getIncompleteDownloads();

    @Query("SELECT * FROM videos WHERE playlist_id = :playlistId AND download_status = 'PENDING' ORDER BY playlist_position ASC LIMIT 1")
    Video getNextPendingPlaylistVideo(String playlistId);

    @Insert
    long insert(Video video);

    @Update
    void update(Video video);

    @Delete
    void delete(Video video);

    @Query("UPDATE videos SET download_status = :status WHERE id = :id")
    void updateStatus(int id, DownloadStatus status);

    @Query("UPDATE videos SET download_status = :status, error_message = :error WHERE id = :id")
    void updateStatusWithError(int id, DownloadStatus status, String error);

    @Query("UPDATE videos SET progress = :progress, download_status = :status WHERE id = :id")
    void updateProgress(int id, int progress, DownloadStatus status);

    @Query("UPDATE videos SET file_path = :filePath, size_bytes = :sizeBytes, duration_ms = :durationMs, download_status = :status, progress = 100, error_message = NULL WHERE id = :id")
    void markCompleted(int id, String filePath, long sizeBytes, long durationMs, DownloadStatus status);

    @Query("UPDATE videos SET file_path = :filePath, size_bytes = :sizeBytes, duration_ms = :durationMs, downloaded_at = :downloadedAt, sha256 = :sha256, download_status = :status, progress = 100, error_message = NULL WHERE id = :id")
    void markCompletedWithMetadata(int id, String filePath, long sizeBytes, long durationMs,
                                   long downloadedAt, String sha256, DownloadStatus status);

    @Query("UPDATE videos SET watch_position_ms = :positionMs WHERE id = :id")
    void updateWatchPosition(int id, long positionMs);

    @Query("UPDATE videos SET is_favorite = :isFavorite WHERE id = :id")
    void updateFavorite(int id, boolean isFavorite);

    @Query("DELETE FROM videos WHERE id = :id")
    void deleteById(int id);
}
