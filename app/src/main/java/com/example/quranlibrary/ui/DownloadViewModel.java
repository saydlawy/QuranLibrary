package com.example.quranlibrary.ui;

import android.app.Application;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.quranlibrary.data.db.Section;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.repository.SectionRepository;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.example.quranlibrary.download.DownloadScheduler;

import java.io.File;
import java.util.List;

/**
 * ViewModel لشاشة التحميل.
 * - يعرض قائمة الأقسام للاختيار.
 * - يبدأ التحميل عبر UIDT.
 * - يعرض حالة العملية (نجاح/خطأ).
 */
public class DownloadViewModel extends AndroidViewModel {

    private final SectionRepository sectionRepository;
    private final VideoRepository videoRepository;

    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDownloading = new MutableLiveData<>(false);

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

    /**
     * يبدأ تحميل فيديو جديد.
     */
    public void startDownload(String url, Section section, String title) {
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
                ? "فيديو جديد"
                : title.trim();

        // مجلد التخزين داخل مجلد التطبيق (لا يحتاج صلاحيات)
        File outputDir = new File(
                getApplication().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "videos");
        if (!outputDir.exists()) outputDir.mkdirs();

        // إنشاء سجل مؤقت في قاعدة البيانات
        Video video = new Video(section.id, cleanTitle, cleanUrl);

        // إدراج متزامن في Thread منفصل للحصول على id
        new Thread(() -> {
            long newId = videoRepository.insert(video);
            int videoId = (int) newId;

            // جدولة المهمة على الخيط الرئيسي (لأن JobScheduler يتطلب ذلك أحيانًا)
            getApplication().getMainExecutor().execute(() -> {
                try {
                    DownloadScheduler.schedule(
                            getApplication(),
                            videoId,
                            cleanUrl,
                            outputDir.getAbsolutePath(),
                            cleanTitle);
                    isDownloading.postValue(true);
                    statusMessage.postValue("بدأ التحميل في الخلفية");
                } catch (Exception e) {
                    statusMessage.postValue("فشل بدء التحميل: " + e.getMessage());
                }
            });
        }).start();
    }
}
