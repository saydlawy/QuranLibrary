package com.example.quranlibrary.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;

import java.util.List;

/**
 * Repository للفيديوهات. نقطة الوصول الوحيدة لبيانات الفيديو من طبقة الواجهة.
 */
public class VideoRepository {

    private final VideoDao videoDao;

    public VideoRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        videoDao = db.videoDao();
    }

    public LiveData<List<Video>> getVideosBySection(int sectionId) {
        return videoDao.getVideosBySection(sectionId);
    }

    public LiveData<List<Video>> getAllVideos() {
        return videoDao.getAllVideos();
    }

    public Video getVideoById(int id) {
        return videoDao.getVideoById(id);
    }

    public List<Video> getIncompleteDownloads() {
        return videoDao.getIncompleteDownloads();
    }

    /**
     * إدراج فيديو جديد، وإرجاع الـ id المُولَّد.
     */
    public long insert(Video video) {
        return videoDao.insert(video);
    }

    /**
     * إدراج في الخلفية دون انتظار النتيجة (للاستخدام من الـ UI).
     */
    public void insertAsync(Video video) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.insert(video));
    }

    public void update(Video video) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.update(video));
    }

    public void updateStatus(int id, DownloadStatus status) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.updateStatus(id, status));
    }

    public void updateProgress(int id, int progress, DownloadStatus status) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.updateProgress(id, progress, status));
    }

    public void markCompleted(int id, String filePath, long sizeBytes,
                              long durationMs, DownloadStatus status) {
        AppDatabase.getWriteExecutor().execute(() ->
                videoDao.markCompleted(id, filePath, sizeBytes, durationMs, status));
    }

    public void updateWatchPosition(int id, long positionMs) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.updateWatchPosition(id, positionMs));
    }

    public void updateFavorite(int id, boolean isFavorite) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.updateFavorite(id, isFavorite));
    }

    public void delete(Video video) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.delete(video));
    }

    public void deleteById(int id) {
        AppDatabase.getWriteExecutor().execute(() -> videoDao.deleteById(id));
    }
}
