package com.wyrm.omrajput.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMediaTest {
    @Test
    fun releaseMediaAllowsOnlyPinnedHttpsHosts() {
        assertTrue(trustedReleaseMediaUrl("https://github.com/user-attachments/assets/a/b"))
        assertTrue(trustedReleaseMediaUrl("https://release-assets.githubusercontent.com/file.png"))
        assertFalse(trustedReleaseMediaUrl("http://github.com/user-attachments/assets/a/b"))
        assertFalse(trustedReleaseMediaUrl("https://github.com.evil.example/file.png"))
        assertFalse(trustedReleaseMediaUrl("https://example.com/file.png"))
    }

    @Test
    fun videoLinksAreRecognizedWithoutTreatingImagesAsVideo() {
        assertTrue(videoReleaseUrl("https://release-assets.githubusercontent.com/demo.mp4"))
        assertTrue(videoReleaseUrl("https://github.com/user-attachments/assets/a/b"))
        assertFalse(videoReleaseUrl("https://user-images.githubusercontent.com/demo.png"))
    }
}
