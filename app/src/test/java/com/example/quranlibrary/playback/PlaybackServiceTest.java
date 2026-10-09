package com.example.quranlibrary.playback;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlaybackServiceTest {

    @Test
    public void serviceCreatesMediaSessionAndPlayerThenReleases() throws Exception {
        ServiceController<PlaybackService> controller =
                Robolectric.buildService(PlaybackService.class).create();
        try {
            PlaybackService service = controller.get();
            assertNotNull(service);

            Field sessionField = PlaybackService.class.getDeclaredField("mediaSession");
            sessionField.setAccessible(true);
            assertNotNull("MediaSession should be created with the service",
                    sessionField.get(service));

            Field playerField = PlaybackService.class.getDeclaredField("player");
            playerField.setAccessible(true);
            assertNotNull("ExoPlayer should be owned by the service", playerField.get(service));
        } finally {
            controller.destroy();
        }
    }
}
