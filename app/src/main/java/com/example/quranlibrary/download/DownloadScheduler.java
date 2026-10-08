package com.example.quranlibrary.download;

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
 * ملاحظة: setNotification() تُستدعى داخل onStartJob() وليس هنا.
 */
public class DownloadScheduler {

    private static final String TAG = "DownloadScheduler";

    @RequiresPermission(android.Manifest.permission.RUN_USER_INITIATED_JOBS)
    public static void schedule(Context context,
                                int videoId,
                                String url,
                                String outputDir,
                                String title) {

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
}
