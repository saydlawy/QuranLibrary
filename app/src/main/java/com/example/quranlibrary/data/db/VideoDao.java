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

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    Video getVideoById(int id);

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    LiveData<Video> getVideoByIdLive(int id);

    @Query("SELECT COUNT(*) FROM videos WHERE section_id = :sectionId")
    int getCountBySection(int sectionId);

    @Query("SELECT * FROM videos WHERE download_status IN ('PENDING', 'DOWNLOADING', 'PAUSED')")
    List<Video> getIncompleteDownloads();

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

    @Query("UPDATE videos SET watch_position_ms = :positionMs WHERE id = :id")
    void updateWatchPosition(int id, long positionMs);

    @Query("UPDATE videos SET is_favorite = :isFavorite WHERE id = :id")
    void updateFavorite(int id, boolean isFavorite);

    @Query("DELETE FROM videos WHERE id = :id")
    void deleteById(int id);
}
