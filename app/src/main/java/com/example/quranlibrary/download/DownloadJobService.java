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

import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * خدمة UIDT لتحميل الفيديوهات في الخلفية.
 * - تستخدم DownloadCallback الجديدة لاستقبال أحداث Python.
 * - تحفظ حالة كل عنصر في Room.
 * - تعرض تقدمًا حقيقيًا في الإشعار.
 */
public class DownloadJobService extends JobService {

    public static final String TAG = "DownloadJobService";

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_OUTPUT_DIR = "extra_output_dir";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_QUALITY = "extra_quality";
    public static final String EXTRA_MODE = "extra_mode";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private volatile boolean jobCancelled = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob: " + params.getJobId());

        PersistableBundle extras = params.getExtras();
        int videoId = extras.getInt(EXTRA_VIDEO_ID, -1);
        int sectionId = extras.getInt(EXTRA_SECTION_ID, -1);
        String url = extras.getString(EXTRA_URL);
        String outputDir = extras.getString(EXTRA_OUTPUT_DIR);
        String title = extras.getString(EXTRA_TITLE, "فيديو");
        int quality = extras.getInt(EXTRA_QUALITY, 0);
        String mode = extras.getString(EXTRA_MODE, "auto");

        if (videoId <= 0 || url == null || outputDir == null || sectionId <= 0) {
            Log.e(TAG, "بيانات ناقصة");
            jobFinished(params, false);
            return false;
        }

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 34) {
            setNotification(params, NOTIFICATION_ID,
                    buildNotification(title, 0, "جاري التحضير"),
                    JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        }

        VideoDao dao = AppDatabase.getInstance(this).videoDao();
        String ffmpegPath = FFmpegHelper.getFFmpegPath(this);
        Log.d(TAG, "FFmpeg path: " + ffmpegPath);

        // حالة التقدم لكل عنصر (لمنع ضغط كبير على DB/Notification)
        final AtomicInteger lastPercent = new AtomicInteger(-1);

        DownloadCallback cb = new DownloadCallback() {
            @Override
            public void onProgress(int percent, String speed, String eta,
                                   String itemTitle, int index, int total) {
                if (jobCancelled) return;

                String prefix = total > 1 ? "[" + index + "/" + total + "] " : "";
                updateNotification(title, percent, prefix + (speed.isEmpty() ? "" : speed));

                // تحديث DB كل 5% أو عند اكتمال
                int current = lastPercent.get();
                if (percent >= 0 && (percent - current >= 5 || percent == 100)) {
                    if (lastPercent.compareAndSet(current, percent)) {
                        dao.updateProgress(videoId, percent, DownloadStatus.DOWNLOADING);
                    }
                }
            }

            @Override
            public void onItemFinished(int index, int total, String itemTitle, String path) {
                Log.d(TAG, "onItemFinished: " + itemTitle + " -> " + path);
            }

            @Override
            public void onItemFailed(int index, int total, String itemTitle, String error) {
                Log.e(TAG, "onItemFailed [" + index + "/" + total + "] " + itemTitle + ": " + error);
            }
        };

        new Thread(() -> {
            try {
                String resultJson = PythonBridge.download(
                        url, outputDir, ffmpegPath, quality, mode, cb);
                handleResult(dao, videoId, sectionId, title, resultJson);
            } catch (Exception e) {
                Log.e(TAG, "استثناء من PythonBridge", e);
                dao.updateStatusWithError(videoId, DownloadStatus.FAILED, e.getMessage());
            } finally {
                cancelNotification();
                jobFinished(params, false);
            }
        }).start();

        return true;
    }

    private void handleResult(VideoDao dao, int videoId, int sectionId,
                              String title, String resultJson) {
        try {
            JSONObject root = new JSONObject(resultJson);
            boolean ok = root.optBoolean("ok", false);
            boolean cancelled = root.optBoolean("cancelled", false);

            if (cancelled) {
                dao.updateStatusWithError(videoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
                return;
            }

            if (ok) {
                JSONArray downloaded = root.optJSONArray("downloaded");
                String firstPath = "";
                long totalSize = 0;
                if (downloaded != null && downloaded.length() > 0) {
                    firstPath = downloaded.optString(0, "");
                    File f = new File(firstPath);
                    if (f.exists()) totalSize = f.length();
                }
                dao.markCompleted(videoId, firstPath, totalSize, 0,
                        DownloadStatus.COMPLETED);
                Log.d(TAG, "اكتمل التحميل: " + firstPath);
            } else {
                String err = root.optString("error", "خطأ غير معروف");
                JSONArray failed = root.optJSONArray("failed");
                if (failed != null && failed.length() > 0) {
                    JSONObject first = failed.optJSONObject(0);
                    if (first != null) err = first.optString("error", err);
                }
                dao.updateStatusWithError(videoId, DownloadStatus.FAILED, err);
                Log.e(TAG, "فشل التحميل: " + err);
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تفسير نتيجة JSON", e);
            dao.updateStatusWithError(videoId, DownloadStatus.FAILED,
                    "خطأ في تفسير النتيجة: " + e.getMessage());
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob");
        jobCancelled = true;
        PythonBridge.cancel();
        cancelNotification();
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "تحميل الفيديوهات", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, int percent, String status) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("جاري تحميل: " + title)
                .setContentText(status == null || status.isEmpty() ? (percent + "%") : status)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (percent >= 0) {
            b.setProgress(100, percent, false);
        } else {
            b.setProgress(0, 0, true);
        }
        return b.build();
    }

    private void updateNotification(String title, int percent, String status) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(title, percent, status));
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
