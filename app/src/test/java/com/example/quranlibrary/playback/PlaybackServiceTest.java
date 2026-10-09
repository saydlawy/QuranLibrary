package com.example.quranlibrary.playback;

import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlaybackServiceTest {

    @Test
    public void serviceCreatesAndReleasesWithoutActivity() {
        ServiceController<PlaybackService> controller =
                Robolectric.buildService(PlaybackService.class).create();
        assertNotNull(controller.get());
        controller.destroy();
    }
}
