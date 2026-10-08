package com.example.quranlibrary.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.RequiresPermission;

/**
 * مساعد جدولة مهمة UIDT للتحميل.
 * - يستخدم setUserInitiated(true) على API 34+.
 * - ينشئ قناة إشعار قبل استخدام الإشعار.
 */
public class DownloadScheduler {

    private static final String TAG = "DownloadScheduler";
    private static final String CHANNEL_ID = "download_channel";

    @RequiresPermission(android.Manifest.permission.RUN_USER_INITIATED_JOBS)
    public static void schedule(Context context,
                                int videoId,
                                String url,
                                String outputDir,
                                String title) {

        ensureChannel(context);

        PersistableBundle extras = new PersistableBundle();
        extras.putInt(DownloadJobService.EXTRA_VIDEO_ID, videoId);
        extras.putString(DownloadJobService.EXTRA_URL, url);
        extras.putString(DownloadJobService.EXTRA_OUTPUT_DIR, outputDir);
        extras.putString(DownloadJobService.EXTRA_TITLE, title);

        ComponentName component = new ComponentName(context, DownloadJobService.class);

        JobInfo.Builder builder = new JobInfo.Builder(videoId, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setExtras(extras);

        // setUserInitiated متاح من API 34
        if (Build.VERSION.SDK_INT >= 34) {
            builder.setUserInitiated(true);
        }

        // setNotification متاح من API 26 (مع NotificationChannel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setContentTitle("تحميل الفيديو")
                    .setContentText(title)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .build();
            builder.setNotification(notification);
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            int result = scheduler.schedule(builder.build());
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "تم جدولة المهمة: " + videoId);
            } else {
                Log.e(TAG, "فشل جدولة المهمة: " + videoId);
            }
        }
    }

    public static void cancel(Context context, int jobId) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(jobId);
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "تحميل الفيديوهات",
                        NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(channel);
            }
        }
    }
}
