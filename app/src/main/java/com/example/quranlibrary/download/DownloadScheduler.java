package com.example.quranlibrary.download;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.RequiresPermission;

public class DownloadScheduler {

    private static final String TAG = "DownloadScheduler";

    @RequiresPermission(android.Manifest.permission.RUN_USER_INITIATED_JOBS)
    public static boolean schedule(Context context,
                                  int videoId,
                                  int sectionId,
                                  String url,
                                  String outputDir,
                                  String title,
                                  int quality,
                                  String mode) {
        return schedule(context, videoId, sectionId, url, outputDir, title,
                quality, mode, null);
    }

    @RequiresPermission(android.Manifest.permission.RUN_USER_INITIATED_JOBS)
    public static boolean schedule(Context context,
                                   int videoId,
                                   int sectionId,
                                   String url,
                                   String outputDir,
                                   String title,
                                   int quality,
                                   String mode,
                                   String playlistId) {
        PersistableBundle extras = new PersistableBundle();
        extras.putInt(DownloadJobService.EXTRA_VIDEO_ID, videoId);
        extras.putInt(DownloadJobService.EXTRA_SECTION_ID, sectionId);
        extras.putString(DownloadJobService.EXTRA_URL, url);
        extras.putString(DownloadJobService.EXTRA_OUTPUT_DIR, outputDir);
        extras.putString(DownloadJobService.EXTRA_TITLE, title);
        extras.putInt(DownloadJobService.EXTRA_QUALITY, quality);
        extras.putString(DownloadJobService.EXTRA_MODE, mode);
        if (playlistId != null && !playlistId.trim().isEmpty()) {
            extras.putString(DownloadJobService.EXTRA_PLAYLIST_ID, playlistId);
        }

        ComponentName component = new ComponentName(context, DownloadJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(videoId, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setExtras(extras);

        if (Build.VERSION.SDK_INT >= 34) {
            builder.setUserInitiated(true);
        }

        JobScheduler scheduler = (JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            Log.e(TAG, "JobScheduler غير متوفر");
            return false;
        }

        int result = scheduler.schedule(builder.build());
        boolean ok = result == JobScheduler.RESULT_SUCCESS;
        if (ok) {
            Log.d(TAG, "تم جدولة المهمة: " + videoId);
        } else {
            Log.e(TAG, "فشل جدولة المهمة: " + videoId);
        }
        return ok;
    }

    public static void cancel(Context context, int jobId) {
        JobScheduler scheduler = (JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(jobId);
        }
    }
}
