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
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

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
    private volatile String processId;

    @Override
    public boolean onStartJob(JobParameters params) {
        jobCancelled = false;

        PersistableBundle extras = params.getExtras();
        int videoId = extras.getInt(EXTRA_VIDEO_ID, -1);
        int sectionId = extras.getInt(EXTRA_SECTION_ID, -1);
        String url = extras.getString(EXTRA_URL);
        String outputDir = extras.getString(EXTRA_OUTPUT_DIR);
        String title = extras.getString(EXTRA_TITLE, "فيديو");
        int quality = extras.getInt(EXTRA_QUALITY, 0);
        String mode = extras.getString(EXTRA_MODE, "auto");

        if (videoId <= 0 || sectionId <= 0 || url == null || outputDir == null) {
            Log.e(TAG, "بيانات التحميل ناقصة");
            jobFinished(params, false);
            return false;
        }

        if ("playlist".equalsIgnoreCase(mode)) {
            Log.e(TAG, "وضع playlist مؤجل إلى M6؛ M2 يثبت محرك الفيديو المفرد فقط");
            VideoDao dao = AppDatabase.getInstance(this).videoDao();
            dao.updateStatusWithError(videoId, DownloadStatus.FAILED,
                    "وضع playlist سيُفعل في M6");
            jobFinished(params, false);
            return false;
        }

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 34) {
            setNotification(params, NOTIFICATION_ID,
                    buildNotification(title, 0, "جاري تهيئة محرك التنزيل"),
                    JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        }

        VideoDao dao = AppDatabase.getInstance(this).videoDao();
        final AtomicInteger lastPercent = new AtomicInteger(-1);
        processId = "video-" + videoId;

        new Thread(() -> {
            try {
                initEngine();

                YoutubeDLRequest request = buildRequest(
                        url, outputDir, quality, mode);

                dao.updateProgress(videoId, 0, DownloadStatus.DOWNLOADING);

                final String currentProcessId = processId;
                YoutubeDLResponse response = YoutubeDL.getInstance().execute(
                        request,
                        currentProcessId,
                        (progress, eta, line) -> {
                            if (jobCancelled) return kotlin.Unit.INSTANCE;

                            int percent = Math.max(0, Math.min(100, Math.round(progress)));
                            int current = lastPercent.get();
                            if (percent == 100 || current < 0 || percent - current >= 5) {
                                if (lastPercent.compareAndSet(current, percent)) {
                                    dao.updateProgress(
                                            videoId, percent, DownloadStatus.DOWNLOADING);
                                }
                            }

                            updateNotification(
                                    title,
                                    percent,
                                    eta > 0 ? "متبقي " + eta + " ثانية" : "جاري التنزيل");
                            return kotlin.Unit.INSTANCE;
                        });

                if (jobCancelled) {
                    dao.updateStatusWithError(
                            videoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
                    return;
                }

                if (response.getExitCode() != 0) {
                    String error = response.getErr() == null || response.getErr().trim().isEmpty()
                            ? "yt-dlp exit code " + response.getExitCode()
                            : response.getErr().trim();
                    dao.updateStatusWithError(videoId, DownloadStatus.FAILED, error);
                    return;
                }

                String finalPath = findPrintedPath(response.getOut(), outputDir);
                if (finalPath.isEmpty()) {
                    dao.updateStatusWithError(
                            videoId, DownloadStatus.FAILED,
                            "اكتمل yt-dlp لكن لم يتم العثور على المسار النهائي");
                    return;
                }

                File output = new File(finalPath);
                if (!output.isFile() || output.length() <= 0) {
                    dao.updateStatusWithError(
                            videoId, DownloadStatus.FAILED,
                            "المسار النهائي غير صالح: " + finalPath);
                    return;
                }

                dao.markCompleted(
                        videoId,
                        finalPath,
                        output.length(),
                        0,
                        DownloadStatus.COMPLETED);
                Log.d(TAG, "اكتمل التحميل: " + finalPath);
                updateNotification(title, 100, "اكتمل التحميل");
            } catch (YoutubeDL.CanceledException e) {
                dao.updateStatusWithError(
                        videoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
            } catch (Exception e) {
                Log.e(TAG, "فشل محرك yt-dlp", e);
                dao.updateStatusWithError(
                        videoId, DownloadStatus.FAILED,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                processId = null;
                cancelNotification();
                jobFinished(params, false);
            }
        }, "ytdlp-" + videoId).start();

        return true;
    }

    private void initEngine() throws Exception {
        YoutubeDL.getInstance().init(getApplicationContext());
        FFmpeg.getInstance().init(getApplicationContext());
    }

    private YoutubeDLRequest buildRequest(
            String url, String outputDir, int quality, String mode) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);

        String height = quality > 0 ? "[height<=" + quality + "]" : "";
        String format = "bv*" + height + "+ba/b" + height + "/b";
        if (quality == -1) {
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best");
            request.addOption("-x");
            request.addOption("--audio-format", "m4a");
        } else {
            request.addOption("-f", format);
            request.addOption("--merge-output-format", "mp4");
        }

        request.addOption("-o", new File(outputDir, "%(title)s.%(ext)s").getAbsolutePath());
        request.addOption("--print", "after_move:filepath");
        request.addOption("--no-mtime");
        request.addOption("--retries", "5");
        request.addOption("--fragment-retries", "10");
        request.addOption("--socket-timeout", "30");
        request.addOption("--continue");
        request.addOption("--no-overwrites");
        request.addOption("--trim-filenames", "150");
        request.addOption("--newline");
        request.addOption("--no-warnings");
        // YouTube currently has SABR/403 cases on some clients; Android client returns direct CDN URLs.
        request.addOption("--extractor-args", "youtube:player_client=android");

        if ("video".equalsIgnoreCase(mode) || "auto".equalsIgnoreCase(mode)) {
            request.addOption("--no-playlist");
        }

        return request;
    }

    private String findPrintedPath(String output, String outputDir) {
        if (output == null || output.trim().isEmpty()) return "";

        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String candidate = lines[i].trim();
            if (candidate.isEmpty()) continue;

            File file = new File(candidate);
            if (file.isFile()) return file.getAbsolutePath();

            File inOutput = new File(outputDir, candidate);
            if (inOutput.isFile()) return inOutput.getAbsolutePath();
        }
        return "";
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        jobCancelled = true;
        String id = processId;
        if (id != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(id);
            } catch (Exception e) {
                Log.w(TAG, "تعذر إلغاء عملية yt-dlp", e);
            }
        }
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
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("جاري تحميل: " + title)
                        .setContentText(status == null || status.isEmpty()
                                ? percent + "%" : status)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true);
        if (percent >= 0) {
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private void updateNotification(String title, int percent, String status) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(title, percent, status));
        }
    }

    private void cancelNotification() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
