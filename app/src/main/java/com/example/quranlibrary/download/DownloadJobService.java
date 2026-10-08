package com.example.quranlibrary.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;

import java.io.File;

/**
 * خدمة UIDT لتحميل الفيديوهات في الخلفية.
 * - تُشغَّل عبر JobScheduler مع setUserInitiated(true).
 * - تبقى في الخلفية حتى انتهاء التحميل.
 * - تُظهر إشعارًا دائمًا بالتقدم.
 * - تحفظ الحالة في Room لضمان الاستئناف.
 */
public class DownloadJobService extends JobService {

    public static final String TAG = "DownloadJobService";

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_OUTPUT_DIR = "extra_output_dir";
    public static final String EXTRA_TITLE = "extra_title";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private volatile boolean jobCancelled = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob: " + params.getJobId());

        PersistableBundle extras = params.getExtras();
        int videoId = extras.getInt(EXTRA_VIDEO_ID, -1);
        String url = extras.getString(EXTRA_URL);
        String outputDir = extras.getString(EXTRA_OUTPUT_DIR);
        String title = extras.getString(EXTRA_TITLE, "فيديو");

        if (videoId <= 0 || url == null || outputDir == null) {
            Log.e(TAG, "بيانات ناقصة");
            jobFinished(params, false);
            return false;
        }

        createNotificationChannel();
        VideoDao dao = AppDatabase.getInstance(this).videoDao();

        PythonBridge.download(url, outputDir,
                (percent, status, message) -> {
                    if (jobCancelled) return;
                    // تحديث الإشعار
                    updateNotification(title, percent);
                    // تحديث قاعدة البيانات
                    DownloadStatus dbStatus = "error".equals(status)
                            ? DownloadStatus.FAILED
                            : DownloadStatus.DOWNLOADING;
                    dao.updateProgress(videoId, percent, dbStatus);
                },
                new PythonBridge.DownloadResultCallback() {
                    @Override
                    public void onComplete(PythonBridge.DownloadResult result) {
                        try {
                            if (result.success) {
                                dao.markCompleted(videoId, result.filePath,
                                        result.sizeBytes, result.durationMs,
                                        DownloadStatus.COMPLETED);
                                Log.d(TAG, "اكتمل التحميل: " + result.filePath);
                            } else {
                                dao.updateStatus(videoId, DownloadStatus.FAILED);
                                Log.e(TAG, "فشل التحميل: " + result.error);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "خطأ في حفظ النتيجة", e);
                        } finally {
                            cancelNotification();
                            jobFinished(params, false);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        dao.updateStatus(videoId, DownloadStatus.FAILED);
                        Log.e(TAG, "خطأ Python: " + errorMessage);
                        cancelNotification();
                        jobFinished(params, false);
                    }
                });

        return true; // المهمة ما زالت قيد التنفيذ
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob (سيُعاد الجدولة)");
        jobCancelled = true;
        cancelNotification();
        // إرجاع true لإعادة جدولة المهمة عند توفر الشروط
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "تحميل الفيديوهات",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("إشعارات تحميل الفيديوهات");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void updateNotification(String title, int percent) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("جاري تحميل: " + title)
                .setContentText(percent + "%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification);
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
