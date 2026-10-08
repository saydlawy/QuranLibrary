package com.example.quranlibrary.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.google.android.material.appbar.MaterialToolbar;

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

        VideoAdapter adapter = new VideoAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (sectionId <= 0) {
            showEmptyState();
            return;
        }

        VideoRepository repository = new VideoRepository(getApplication());
        repository.getVideosBySection(sectionId).observe(this, videos -> {
            if (videos == null || videos.isEmpty()) {
                adapter.submitList(java.util.Collections.emptyList());
                showEmptyState();
                return;
            }

            adapter.submitList(videos);
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        });
    }

    private void showEmptyState() {
        emptyView.setVisibility(View.VISIBLE);
    }
}
