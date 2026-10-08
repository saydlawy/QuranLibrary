package com.example.quranlibrary.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_POSITION_MS = "extra_position_ms";

    private ExoPlayer player;
    private VideoRepository repository;
    private int videoId;
    private long initialPositionMs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        MaterialToolbar toolbar = findViewById(R.id.playerToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String title = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        toolbar.setTitle(title == null || title.trim().isEmpty()
                ? getString(R.string.app_name)
                : title);

        videoId = getIntent().getIntExtra(EXTRA_VIDEO_ID, -1);
        initialPositionMs = Math.max(0L, getIntent().getLongExtra(EXTRA_POSITION_MS, 0L));
        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);

        if (videoId <= 0 || filePath == null || filePath.trim().isEmpty()) {
            Toast.makeText(this, R.string.video_file_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File file = new File(filePath);
        if (!file.isFile() || file.length() <= 0) {
            Toast.makeText(this, R.string.video_file_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        repository = new VideoRepository(getApplication());
        PlayerView playerView = findViewById(R.id.playerView);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@androidx.annotation.NonNull PlaybackException error) {
                Toast.makeText(VideoPlayerActivity.this,
                        R.string.video_playback_error,
                        Toast.LENGTH_LONG).show();
            }
        });

        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(file));
        player.setMediaItem(mediaItem);
        player.prepare();
        if (initialPositionMs > 0) {
            player.seekTo(initialPositionMs);
        }
        player.play();
    }

    @Override
    protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        savePosition();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void savePosition() {
        if (repository != null && videoId > 0 && player != null) {
            repository.updateWatchPosition(videoId, Math.max(0L, player.getCurrentPosition()));
        }
    }
}
