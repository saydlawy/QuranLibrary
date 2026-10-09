package com.example.quranlibrary.data.transfer;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.quranlibrary.data.db.AppDatabase;
import com.example.quranlibrary.data.db.Section;
import com.example.quranlibrary.data.db.SectionDao;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.db.VideoDao;
import com.example.quranlibrary.data.model.DownloadStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class LibraryTransferManager {
    private static final String TAG = "LibraryTransfer";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_ENTRY_BYTES = 10L * 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 50L * 1024L * 1024L * 1024L;

    private LibraryTransferManager() { }

    public static String exportToUri(Context context, Uri destination) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context);
        JSONArray sections = new JSONArray();
        JSONArray videos = new JSONArray();
        Map<Integer, File> media = new HashMap<>();

        for (Section section : db.sectionDao().getAllSectionsSnapshot()) {
            JSONObject item = new JSONObject();
            item.put("id", section.id);
            item.put("name", section.name);
            put(item, "iconKey", section.iconKey);
            item.put("sortOrder", section.sortOrder);
            item.put("isDefault", section.isDefault);
            item.put("createdAt", section.createdAt);
            sections.put(item);
        }

        for (Video video : db.videoDao().getAllVideosSnapshot()) {
            JSONObject item = videoToJson(video);
            File file = video.filePath == null ? null : new File(video.filePath);
            if (file != null && file.isFile() && file.length() > 0) {
                item.put("mediaEntry", "media/video_" + video.id + extension(file.getName()));
                item.put("sha256", sha256(file));
                item.put("sizeBytes", file.length());
                media.put(video.id, file);
            } else {
                item.put("mediaEntry", JSONObject.NULL);
            }
            videos.put(item);
        }

        JSONObject manifest = new JSONObject();
        manifest.put("format", "quran-library-backup");
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("exportedAt", System.currentTimeMillis());
        manifest.put("sections", sections);
        manifest.put("videos", videos);

        OutputStream raw = context.getContentResolver().openOutputStream(destination);
        if (raw == null) throw new IllegalStateException("تعذر فتح ملف التصدير");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();

            for (Map.Entry<Integer, File> entry : media.entrySet()) {
                zip.putNextEntry(new ZipEntry(
                        "media/video_" + entry.getKey() + extension(entry.getValue().getName())));
                try (InputStream input = new BufferedInputStream(new FileInputStream(entry.getValue()))) {
                    copy(input, zip, Long.MAX_VALUE, null);
                }
                zip.closeEntry();
            }
        }
        return "تم تصدير " + sections.length() + " قسم و" + videos.length()
                + " فيديو، منها " + media.size() + " ملف فيديو فعلي.";
    }

    public static String importFromUri(Context context, Uri source) throws Exception {
        File staging = new File(context.getCacheDir(), "library-import-" + System.currentTimeMillis());
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IllegalStateException("تعذر إنشاء مجلد مؤقت للاستيراد");
        }
        try {
            extractSafely(context, source, staging);
            File manifestFile = new File(staging, "manifest.json");
            if (!manifestFile.isFile()) throw new IllegalArgumentException("الأرشيف لا يحتوي manifest.json");

            JSONObject manifest = new JSONObject(readText(manifestFile));
            if (!"quran-library-backup".equals(manifest.optString("format"))
                    || manifest.optInt("formatVersion", -1) != FORMAT_VERSION) {
                throw new IllegalArgumentException("صيغة النسخة الاحتياطية غير مدعومة");
            }
            JSONArray sections = manifest.optJSONArray("sections");
            JSONArray videos = manifest.optJSONArray("videos");
            if (sections == null || videos == null) {
                throw new IllegalArgumentException("ملف manifest ناقص الأقسام أو الفيديوهات");
            }

            File base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (base == null) base = context.getFilesDir();
            File mediaDir = new File(base, "videos/imported");
            if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                throw new IllegalStateException("تعذر إنشاء مجلد ملفات الفيديو المستوردة");
            }

            Map<Integer, String> copied = new HashMap<>();
            for (int i = 0; i < videos.length(); i++) {
                JSONObject item = videos.getJSONObject(i);
                int oldId = item.optInt("id", -1);
                String entryName = nullableString(item, "mediaEntry");
                if (oldId <= 0 || entryName == null) continue;
                File archived = safeChild(staging, entryName);
                if (!archived.isFile()) throw new IllegalArgumentException("ملف فيديو مفقود: " + entryName);
                String expectedHash = nullableString(item, "sha256");
                if (expectedHash != null && !expectedHash.equalsIgnoreCase(sha256(archived))) {
                    throw new IllegalArgumentException("فشل التحقق من SHA-256: " + entryName);
                }
                File target = File.createTempFile("video_" + oldId + "_",
                        extension(archived.getName()), mediaDir);
                try (InputStream input = new BufferedInputStream(new FileInputStream(archived));
                     OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    copy(input, output, Long.MAX_VALUE, null);
                }
                copied.put(oldId, target.getAbsolutePath());
            }

            AppDatabase db = AppDatabase.getInstance(context);
            SectionDao sectionDao = db.sectionDao();
            VideoDao videoDao = db.videoDao();
            Map<Integer, Integer> sectionMap = new HashMap<>();
            int[] counts = {0, 0};

            db.runInTransaction(() -> {
                try {
                    for (int i = 0; i < sections.length(); i++) {
                        JSONObject item = sections.getJSONObject(i);
                        int oldId = item.optInt("id", -1);
                        String name = item.optString("name", "").trim();
                        if (oldId <= 0 || name.isEmpty()) continue;
                        Section local = sectionDao.getSectionByName(name);
                        if (local != null) {
                            sectionMap.put(oldId, local.id);
                            continue;
                        }
                        Section created = new Section(name, nullableString(item, "iconKey"),
                                item.optInt("sortOrder", 999), item.optBoolean("isDefault", false));
                        created.createdAt = item.optLong("createdAt", System.currentTimeMillis());
                        sectionMap.put(oldId, (int) sectionDao.insert(created));
                        counts[0]++;
                    }

                    for (int i = 0; i < videos.length(); i++) {
                        JSONObject item = videos.getJSONObject(i);
                        Integer localSection = sectionMap.get(item.optInt("sectionId", -1));
                        if (localSection == null) throw new IllegalArgumentException("قسم الفيديو غير موجود");
                        String url = item.optString("youtubeUrl", "").trim();
                        String sourceId = nullableString(item, "sourceId");
                        Video existing = sourceId == null
                                ? videoDao.getByUrlAndSection(localSection, url)
                                : videoDao.getBySourceIdAndSection(localSection, sourceId);
                        Video restored = videoFromJson(item, localSection);
                        String localPath = copied.get(item.optInt("id", -1));
                        if (localPath != null) {
                            restored.filePath = localPath;
                            restored.sizeBytes = new File(localPath).length();
                            restored.sha256 = sha256(new File(localPath));
                            restored.downloadStatus = DownloadStatus.COMPLETED;
                            restored.progress = 100;
                            restored.errorMessage = null;
                        } else {
                            restored.filePath = null;
                            restored.sizeBytes = 0;
                            restored.downloadStatus = DownloadStatus.PENDING;
                            restored.progress = 0;
                            restored.errorMessage = null;
                        }
                        if (existing != null) {
                            restored.id = existing.id;
                            videoDao.update(restored);
                        } else {
                            videoDao.insert(restored);
                        }
                        counts[1]++;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("تعذر استيراد بيانات المكتبة", e);
                }
            });
            return "تم استيراد " + counts[0] + " قسم جديد و" + counts[1]
                    + " فيديو، وربط " + copied.size() + " ملف فيديو محلي.";
        } finally {
            deleteTree(staging);
        }
    }

    private static JSONObject videoToJson(Video v) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", v.id);
        o.put("sectionId", v.sectionId);
        put(o, "title", v.title);
        put(o, "youtubeUrl", v.youtubeUrl);
        o.put("durationMs", v.durationMs);
        o.put("sizeBytes", v.sizeBytes);
        put(o, "quality", v.quality);
        o.put("downloadStatus", v.downloadStatus == null ? "PENDING" : v.downloadStatus.name());
        o.put("progress", v.progress);
        put(o, "errorMessage", v.errorMessage);
        o.put("watchPositionMs", v.watchPositionMs);
        o.put("isFavorite", v.isFavorite);
        o.put("createdAt", v.createdAt);
        put(o, "sourceId", v.sourceId);
        put(o, "sourceType", v.sourceType);
        put(o, "channelName", v.channelName);
        put(o, "thumbnailUrl", v.thumbnailUrl);
        put(o, "publishedAt", v.publishedAt);
        put(o, "playlistId", v.playlistId);
        put(o, "playlistPosition", v.playlistPosition);
        o.put("downloadedAt", v.downloadedAt);
        put(o, "sha256", v.sha256);
        put(o, "metadataStatus", v.metadataStatus);
        return o;
    }

    private static Video videoFromJson(JSONObject o, int sectionId) {
        Video v = new Video(sectionId, o.optString("title", "فيديو مستورد"),
                o.optString("youtubeUrl", ""));
        v.durationMs = o.optLong("durationMs", 0);
        v.quality = nullableString(o, "quality");
        v.errorMessage = nullableString(o, "errorMessage");
        v.watchPositionMs = o.optLong("watchPositionMs", 0);
        v.isFavorite = o.optBoolean("isFavorite", false);
        v.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        v.sourceId = nullableString(o, "sourceId");
        v.sourceType = nullableString(o, "sourceType");
        v.channelName = nullableString(o, "channelName");
        v.thumbnailUrl = nullableString(o, "thumbnailUrl");
        v.publishedAt = nullableLong(o, "publishedAt");
        v.playlistId = nullableString(o, "playlistId");
        v.playlistPosition = nullableInteger(o, "playlistPosition");
        v.downloadedAt = o.optLong("downloadedAt", 0);
        v.sha256 = nullableString(o, "sha256");
        v.metadataStatus = o.optString("metadataStatus", "PENDING");
        try {
            v.downloadStatus = DownloadStatus.valueOf(o.optString("downloadStatus", "PENDING"));
        } catch (IllegalArgumentException e) {
            v.downloadStatus = DownloadStatus.PENDING;
        }
        v.progress = o.optInt("progress", 0);
        return v;
    }

    private static void extractSafely(Context context, Uri source, File staging) throws Exception {
        long total = 0;
        Set<String> names = new HashSet<>();
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) throw new IllegalStateException("تعذر فتح ملف الاستيراد");
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.trim().isEmpty() || name.startsWith("/")
                        || name.contains("\\") || name.matches("^[A-Za-z]:.*")
                        || !names.add(name)) {
                    throw new IllegalArgumentException("مسار غير صالح أو مكرر داخل الأرشيف");
                }
                File target = safeChild(staging, name);
                if (entry.isDirectory()) {
                    if (!target.mkdirs() && !target.isDirectory()) throw new IllegalStateException("تعذر إنشاء مجلد");
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IllegalStateException("تعذر إنشاء مجلد استخراج");
                    }
                    long entrySize = 0;
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            entrySize += read;
                            total += read;
                            if (entrySize > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                throw new IllegalArgumentException("حجم الأرشيف يتجاوز الحد الآمن للاستيراد");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    static File safeChild(File root, String relative) throws Exception {
        File child = new File(root, relative);
        if (!child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IllegalArgumentException("مسار ملف خارج مجلد الاستيراد");
        }
        return child;
    }

    private static String readText(File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out, MAX_ENTRY_BYTES, null);
            return out.toString("UTF-8");
        }
    }

    private static void copy(InputStream in, OutputStream out, long limit, long[] total) throws Exception {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            copied += read;
            if (copied > limit) throw new IllegalArgumentException("حجم الملف يتجاوز الحد المسموح");
            out.write(buffer, 0, read);
            if (total != null) total[0] += read;
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte b : digest.digest()) hash.append(String.format(Locale.US, "%02x", b & 0xff));
        return hash.toString();
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && name.length() - dot <= 10
                && name.substring(dot).matches("\\.[A-Za-z0-9]{1,8}")) {
            return name.substring(dot).toLowerCase(Locale.US);
        }
        return ".bin";
    }

    private static void put(JSONObject o, String key, Object value) throws Exception {
        o.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String nullableString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String value = o.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static Long nullableLong(JSONObject o, String key) {
        return !o.has(key) || o.isNull(key) ? null : o.optLong(key);
    }

    private static Integer nullableInteger(JSONObject o, String key) {
        return !o.has(key) || o.isNull(key) ? null : o.optInt(key);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) Log.w(TAG, "تعذر حذف الملف المؤقت: " + file.getAbsolutePath());
    }
}
