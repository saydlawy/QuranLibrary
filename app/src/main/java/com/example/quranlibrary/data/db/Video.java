package com.example.quranlibrary.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.quranlibrary.data.model.DownloadStatus;

@Entity(
        tableName = "videos",
        foreignKeys = @ForeignKey(
                entity = Section.class,
                parentColumns = "id",
                childColumns = "section_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("section_id"), @Index("source_id")}
)
public class Video {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "section_id")
    public int sectionId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "youtube_url")
    public String youtubeUrl;

    @ColumnInfo(name = "file_path")
    public String filePath;

    @ColumnInfo(name = "thumbnail_path")
    public String thumbnailPath;

    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    @ColumnInfo(name = "size_bytes")
    public long sizeBytes;

    @ColumnInfo(name = "quality")
    public String quality;

    @ColumnInfo(name = "download_status")
    public DownloadStatus downloadStatus;

    @ColumnInfo(name = "progress")
    public int progress;

    @ColumnInfo(name = "error_message")
    public String errorMessage;

    @ColumnInfo(name = "watch_position_ms")
    public long watchPositionMs;

    @ColumnInfo(name = "is_favorite")
    public boolean isFavorite;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "source_id")
    public String sourceId;

    @ColumnInfo(name = "source_type")
    public String sourceType;

    @ColumnInfo(name = "channel_name")
    public String channelName;

    @ColumnInfo(name = "thumbnail_url")
    public String thumbnailUrl;

    @ColumnInfo(name = "published_at")
    public Long publishedAt;

    @ColumnInfo(name = "playlist_id")
    public String playlistId;

    @ColumnInfo(name = "playlist_position")
    public Integer playlistPosition;

    @ColumnInfo(name = "downloaded_at", defaultValue = "0")
    public long downloadedAt;

    @ColumnInfo(name = "sha256")
    public String sha256;

    @NonNull
    @ColumnInfo(name = "metadata_status", defaultValue = "'PENDING'")
    public String metadataStatus;

    public Video(int sectionId, String title, String youtubeUrl) {
        this.sectionId = sectionId;
        this.title = title;
        this.youtubeUrl = youtubeUrl;
        this.downloadStatus = DownloadStatus.PENDING;
        this.progress = 0;
        this.createdAt = System.currentTimeMillis();
        this.downloadedAt = 0L;
        this.metadataStatus = "PENDING";
    }
}
