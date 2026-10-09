package com.example.quranlibrary.ui;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.repository.VideoRepository;
import com.example.quranlibrary.playback.PlaybackService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_POSITION_MS = "extra_position_ms";
    private static final String STATE_POSITION_MS = "state_position_ms";

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;
    private PlayerView playerView;
    private VideoRepository repository;
    private int videoId;
    private long initialPositionMs;
    private String videoTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        MaterialToolbar toolbar = findViewById(R.id.playerToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        if (videoTitle == null || videoTitle.trim().isEmpty()) {
            videoTitle = getString(R.string.app_name);
        }
        toolbar.setTitle(videoTitle);

        videoId = getIntent().getIntExtra(EXTRA_VIDEO_ID, -1);
        initialPositionMs = savedInstanceState == null
                ? Math.max(0L, getIntent().getLongExtra(EXTRA_POSITION_MS, 0L))
                : Math.max(0L, savedInstanceState.getLong(STATE_POSITION_MS, 0L));
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
        playerView = findViewById(R.id.playerView);
        connectToPlaybackService(file);
    }

    private void connectToPlaybackService(File file) {
        SessionToken token = new SessionToken(
                this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                mediaController = controllerFuture.get();
                playerView.setPlayer(mediaController);
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        Toast.makeText(VideoPlayerActivity.this,
                                R.string.video_playback_error,
                                Toast.LENGTH_LONG).show();
                    }
                });

                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(videoTitle)
                        .build();
                MediaItem item = new MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setMediaMetadata(metadata)
                        .build();
                mediaController.setMediaItem(item);
                mediaController.prepare();
                if (initialPositionMs > 0L) {
                    mediaController.seekTo(initialPositionMs);
                }
                mediaController.play();
            } catch (ExecutionException e) {
                Toast.makeText(this, R.string.video_playback_error, Toast.LENGTH_LONG).show();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Toast.makeText(this, R.string.video_playback_error, Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override
    protected void onStop() {
        savePosition();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mediaController != null) {
            outState.putLong(STATE_POSITION_MS,
                    Math.max(0L, mediaController.getCurrentPosition()));
        } else {
            outState.putLong(STATE_POSITION_MS, initialPositionMs);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        savePosition();
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        mediaController = null;
        super.onDestroy();
    }

    private void savePosition() {
        if (repository != null && videoId > 0 && mediaController != null) {
            long position = Math.max(0L, mediaController.getCurrentPosition());
            initialPositionMs = position;
            repository.updateWatchPosition(videoId, position);
        }
    }
}
