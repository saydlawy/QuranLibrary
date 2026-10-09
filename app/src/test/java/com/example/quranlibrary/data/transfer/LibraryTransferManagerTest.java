package com.example.quranlibrary.data.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;

public class LibraryTransferManagerTest {

    @Test
    public void safeChildAllowsFilesInsideStagingDirectory() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "library-transfer-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            File child = LibraryTransferManager.safeChild(root, "media/video_1.mp4");
            assertTrue(child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator));
        } finally {
            root.delete();
        }
    }

    @Test
    public void safeChildRejectsPathTraversal() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "library-transfer-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            LibraryTransferManager.safeChild(root, "../outside.txt");
            fail("Expected traversal path to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: canonical path must remain within the extraction root.
        } finally {
            root.delete();
        }
    }

    @Test
    public void extensionNormalizesSupportedFileExtensions() {
        assertEquals(".mp4", LibraryTransferManager.extension("lecture.MP4"));
        assertEquals(".m4a", LibraryTransferManager.extension("recitation.m4a"));
        assertEquals(".bin", LibraryTransferManager.extension("filename.invalidextensiontoolong"));
    }
}
