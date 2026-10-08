package com.example.quranlibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.db.Video;
import com.example.quranlibrary.data.model.DownloadStatus;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.Collections;

public class SectionVideosActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_SECTION_NAME = "extra_section_name";

    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section_videos);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyView = findViewById(R.id.emptyView);
        RecyclerView recyclerView = findViewById(R.id.videosRecyclerView);

        int sectionId = getIntent().getIntExtra(EXTRA_SECTION_ID, -1);
        String sectionName = getIntent().getStringExtra(EXTRA_SECTION_NAME);
        toolbar.setTitle(sectionName == null || sectionName.trim().isEmpty()
                ? getString(R.string.app_name)
                : sectionName);

        VideoAdapter adapter = new VideoAdapter(this::openVideo);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (sectionId <= 0) {
            showEmptyState();
            return;
        }

        VideoRepository repository = new VideoRepository(getApplication());
        repository.getVideosBySection(sectionId).observe(this, videos -> {
            if (videos == null || videos.isEmpty()) {
                adapter.submitList(Collections.emptyList());
                showEmptyState();
                return;
            }

            adapter.submitList(videos);
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        });
    }

    private void openVideo(Video video) {
        if (video.downloadStatus != DownloadStatus.COMPLETED
                || video.filePath == null
                || video.filePath.trim().isEmpty()) {
            Toast.makeText(this, R.string.video_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        File videoFile = new File(video.filePath);
        if (!videoFile.isFile() || videoFile.length() <= 0) {
            Toast.makeText(this, R.string.video_file_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_ID, video.id);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, video.title);
        intent.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, video.filePath);
        intent.putExtra(VideoPlayerActivity.EXTRA_POSITION_MS, video.watchPositionMs);
        startActivity(intent);
    }

    private void showEmptyState() {
        emptyView.setVisibility(View.VISIBLE);
    }
}
