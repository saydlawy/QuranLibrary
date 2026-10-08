package com.example.quranlibrary.ui;

import android.app.Application;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.quranlibrary.data.db.Section;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.model.DownloadStatus;
import com.example.quranlibrary.data.repository.SectionRepository;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.example.quranlibrary.download.DownloadScheduler;

import java.io.File;
import java.util.List;

public class DownloadViewModel extends AndroidViewModel {

    private final SectionRepository sectionRepository;
    private final VideoRepository videoRepository;

    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDownloading = new MutableLiveData<>(false);

    private LiveData<Video> watchedVideo;
    private final Observer<Video> videoObserver = video -> {
        if (video == null) return;
        if (video.downloadStatus == DownloadStatus.COMPLETED) {
            isDownloading.setValue(false);
            statusMessage.setValue("تم التحميل بنجاح: " + video.title);
        } else if (video.downloadStatus == DownloadStatus.FAILED) {
            isDownloading.setValue(false);
            String err = (video.errorMessage == null || video.errorMessage.isEmpty())
                    ? "سبب غير معروف" : video.errorMessage;
            statusMessage.setValue("فشل التحميل: " + err);
        }
    };

    public DownloadViewModel(@NonNull Application application) {
        super(application);
        sectionRepository = new SectionRepository(application);
        videoRepository = new VideoRepository(application);
    }

    public LiveData<List<Section>> getAllSections() {
        return sectionRepository.getAllSections();
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public LiveData<Boolean> getIsDownloading() {
        return isDownloading;
    }

    public void startDownload(String url, Section section, String title,
                              int quality, String mode) {
        if (url == null || url.trim().isEmpty()) {
            statusMessage.setValue("الرجاء إدخال رابط الفيديو");
            return;
        }
        if (section == null) {
            statusMessage.setValue("الرجاء اختيار قسم");
            return;
        }

        String cleanUrl = url.trim();
        String cleanTitle = (title == null || title.trim().isEmpty())
                ? "فيديو جديد" : title.trim();

        File outputDir = new File(
                getApplication().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "videos");
        if (!outputDir.exists()) outputDir.mkdirs();

        Video video = new Video(section.id, cleanTitle, cleanUrl);

        new Thread(() -> {
            long newId = videoRepository.insert(video);
            int videoId = (int) newId;

            ContextCompat.getMainExecutor(getApplication()).execute(() -> {
                observeVideo(videoId);
                boolean scheduled = DownloadScheduler.schedule(
                        getApplication(),
                        videoId,
                        section.id,
                        cleanUrl,
                        outputDir.getAbsolutePath(),
                        cleanTitle,
                        quality,
                        mode);
                if (scheduled) {
                    isDownloading.setValue(true);
                    statusMessage.setValue("بدأ التحميل في الخلفية");
                } else {
                    isDownloading.setValue(false);
                    statusMessage.setValue("فشل جدولة التحميل");
                }
            });
        }).start();
    }

    private void observeVideo(int videoId) {
        if (watchedVideo != null) {
            watchedVideo.removeObserver(videoObserver);
        }
        watchedVideo = videoRepository.getVideoByIdLive(videoId);
        watchedVideo.observeForever(videoObserver);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (watchedVideo != null) {
            watchedVideo.removeObserver(videoObserver);
            watchedVideo = null;
        }
    }
}
