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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
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
    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";

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
        String playlistId = extras.getString(EXTRA_PLAYLIST_ID);

        if (videoId <= 0 || sectionId <= 0 || url == null || outputDir == null) {
            Log.e(TAG, "بيانات التحميل ناقصة");
            jobFinished(params, false);
            return false;
        }

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 34) {
            setNotification(params, NOTIFICATION_ID,
                    buildNotification(title, 0, "جاري تهيئة محرك التنزيل"),
                    JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        }

        if ("playlist".equalsIgnoreCase(mode)) {
            startPlaylistExpansion(params, videoId, sectionId, url, outputDir, quality, title);
            return true;
        }

        VideoDao dao = AppDatabase.getInstance(this).videoDao();
        final AtomicInteger lastPercent = new AtomicInteger(-1);
        processId = "video-" + videoId;

        new Thread(() -> {
            try {
                initEngine();
                YoutubeDLRequest request = buildRequest(url, outputDir, quality, mode);
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
                boolean wasCancelled = jobCancelled;
                processId = null;
                cancelNotification();
                jobFinished(params, false);
                if (!wasCancelled && playlistId != null && !playlistId.trim().isEmpty()) {
                    scheduleNextPlaylistVideo(playlistId, outputDir, quality);
                }
            }
        }, "ytdlp-" + videoId).start();

        return true;
    }

    private void startPlaylistExpansion(
            JobParameters params,
            int placeholderVideoId,
            int sectionId,
            String url,
            String outputDir,
            int quality,
            String requestedTitle) {
        VideoDao dao = AppDatabase.getInstance(this).videoDao();
        dao.updateProgress(placeholderVideoId, 0, DownloadStatus.DOWNLOADING);
        processId = "playlist-" + placeholderVideoId;

        new Thread(() -> {
            String expandedPlaylistId = null;
            try {
                initEngine();
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--flat-playlist");
                request.addOption("--dump-single-json");
                request.addOption("--skip-download");
                request.addOption("--ignore-errors");
                request.addOption("--no-warnings");
                request.addOption("--extractor-args", "youtube:player_client=android");

                YoutubeDLResponse response = YoutubeDL.getInstance().execute(
                        request,
                        processId,
                        (progress, eta, line) -> {
                            updateNotification(requestedTitle, -1, "جاري قراءة عناصر القائمة");
                            return kotlin.Unit.INSTANCE;
                        });

                if (jobCancelled) {
                    dao.updateStatusWithError(
                            placeholderVideoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
                    return;
                }
                if (response.getExitCode() != 0) {
                    String error = response.getErr() == null || response.getErr().trim().isEmpty()
                            ? "تعذر قراءة قائمة التشغيل (yt-dlp exit code "
                                    + response.getExitCode() + ")"
                            : response.getErr().trim();
                    throw new IllegalStateException(error);
                }

                JSONObject root = parseJsonObject(response.getOut());
                JSONArray entries = root.optJSONArray("entries");
                if (entries == null || entries.length() == 0) {
                    throw new IllegalStateException("لم يعثر yt-dlp على فيديوهات داخل قائمة التشغيل");
                }

                String playlistId = root.optString("id", "").trim();
                if (playlistId.isEmpty()) {
                    playlistId = extractPlaylistId(url);
                }
                if (playlistId.isEmpty()) {
                    throw new IllegalStateException("تعذر تحديد معرّف قائمة التشغيل");
                }
                int insertedCount = 0;
                for (int i = 0; i < entries.length(); i++) {
                    if (jobCancelled) break;
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null || "unavailable".equalsIgnoreCase(entry.optString("_type", ""))) {
                        continue;
                    }

                    String sourceId = entry.optString("id", "").trim();
                    String itemUrl = resolveEntryUrl(entry, sourceId);
                    String itemTitle = entry.optString("title", "").trim();
                    if (itemUrl.isEmpty() || itemTitle.isEmpty()) {
                        Log.w(TAG, "تجاوز عنصر قائمة بلا عنوان أو رابط عند الموضع " + (i + 1));
                        continue;
                    }

                    Video video = new Video(sectionId, itemTitle, itemUrl);
                    video.sourceId = sourceId.isEmpty() ? null : sourceId;
                    video.sourceType = entry.optString("extractor_key",
                            entry.optString("extractor", "youtube"));
                    video.channelName = firstNonEmpty(
                            entry.optString("channel", ""),
                            entry.optString("uploader", ""));
                    video.thumbnailUrl = firstNonEmpty(
                            entry.optString("thumbnail", ""),
                            findThumbnail(entry.optJSONArray("thumbnails")));
                    video.publishedAt = parsePublishedAt(entry.optString("upload_date", ""));
                    video.playlistId = playlistId;
                    video.playlistPosition = entry.optInt("playlist_index", i + 1);
                    video.durationMs = Math.max(0L, entry.optLong("duration", 0L)) * 1000L;
                    video.metadataStatus = "AVAILABLE";
                    dao.insert(video);
                    insertedCount++;
                }

                if (jobCancelled) {
                    dao.updateStatusWithError(
                            placeholderVideoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
                    return;
                }
                if (insertedCount == 0) {
                    throw new IllegalStateException("لم يمكن استخراج أي فيديو صالح من القائمة");
                }

                dao.deleteById(placeholderVideoId);
                expandedPlaylistId = playlistId;
                Log.i(TAG, "تم تسجيل " + insertedCount + " فيديو من القائمة " + playlistId);
                updateNotification(requestedTitle, 100,
                        "تم العثور على " + insertedCount + " فيديو؛ بدء التنزيل");
            } catch (Exception e) {
                Log.e(TAG, "فشل تحليل قائمة التشغيل", e);
                if (jobCancelled) {
                    dao.updateStatusWithError(
                            placeholderVideoId, DownloadStatus.CANCELLED, "أُلغي بواسطة المستخدم");
                } else {
                    dao.updateStatusWithError(
                            placeholderVideoId, DownloadStatus.FAILED,
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            } finally {
                boolean wasCancelled = jobCancelled;
                processId = null;
                cancelNotification();
                jobFinished(params, false);
                if (!wasCancelled && expandedPlaylistId != null) {
                    scheduleNextPlaylistVideo(expandedPlaylistId, outputDir, quality);
                }
            }
        }, "ytdlp-playlist-" + placeholderVideoId).start();
    }

    private void scheduleNextPlaylistVideo(String playlistId, String outputDir, int quality) {
        VideoDao dao = AppDatabase.getInstance(this).videoDao();
        Video next = dao.getNextPendingPlaylistVideo(playlistId);
        while (next != null) {
            boolean scheduled = DownloadScheduler.schedule(
                    getApplicationContext(),
                    next.id,
                    next.sectionId,
                    next.youtubeUrl,
                    outputDir,
                    next.title,
                    quality,
                    "video",
                    playlistId);
            if (scheduled) {
                Log.i(TAG, "تمت جدولة عنصر القائمة " + next.playlistPosition + ": " + next.title);
                return;
            }
            dao.updateStatusWithError(
                    next.id, DownloadStatus.FAILED, "تعذر جدولة تنزيل عنصر قائمة التشغيل");
            next = dao.getNextPendingPlaylistVideo(playlistId);
        }
        Log.i(TAG, "لا توجد عناصر قائمة تشغيل معلقة: " + playlistId);
    }

    private JSONObject parseJsonObject(String output) throws Exception {
        if (output == null) {
            throw new IllegalStateException("لم يرجع yt-dlp بيانات قائمة التشغيل");
        }
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("استجابة yt-dlp ليست JSON صالحًا");
        }
        return new JSONObject(output.substring(start, end + 1));
    }

    private String resolveEntryUrl(JSONObject entry, String sourceId) {
        String webpageUrl = firstNonEmpty(
                entry.optString("webpage_url", ""),
                entry.optString("original_url", ""));
        if (webpageUrl.startsWith("http://") || webpageUrl.startsWith("https://")) {
            return webpageUrl;
        }
        String url = entry.optString("url", "");
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (!sourceId.isEmpty()) {
            return "https://www.youtube.com/watch?v=" + sourceId;
        }
        return "";
    }

    private String extractPlaylistId(String url) {
        if (url == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[?&]list=([^&#]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null ? "" : second.trim();
    }

    @Nullable
    private String findThumbnail(@Nullable JSONArray thumbnails) {
        if (thumbnails == null) return null;
        for (int i = thumbnails.length() - 1; i >= 0; i--) {
            JSONObject item = thumbnails.optJSONObject(i);
            if (item != null) {
                String url = item.optString("url", "");
                if (!url.isEmpty()) return url;
            }
        }
        return null;
    }

    @Nullable
    private Long parsePublishedAt(String uploadDate) {
        if (uploadDate == null || !uploadDate.matches("\\d{8}")) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = format.parse(uploadDate);
            return date == null ? null : date.getTime();
        } catch (ParseException e) {
            return null;
        }
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
