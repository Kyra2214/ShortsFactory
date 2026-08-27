package com.shortsfactory.domain.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaValidatorTest {
    @Test
    fun `valid metadata passes`() {
        val result = MediaValidator.validate(metadata(hasAudio = true))

        assertTrue(result.valid)
        assertEquals(MediaValidationCode.VALID, result.code)
    }

    @Test
    fun `missing audio fails when audio is required`() {
        val result = MediaValidator.validate(metadata(hasAudio = false))

        assertEquals(MediaValidationCode.AUDIO_MISSING, result.code)
        assertTrue(!result.valid)
    }

    @Test
    fun `empty media fails before checking audio`() {
        val result = MediaValidator.validate(metadata(sizeBytes = 0L, hasAudio = false))

        assertEquals(MediaValidationCode.FILE_EMPTY, result.code)
    }

    @Test
    fun `invalid dimensions fail`() {
        val result = MediaValidator.validate(metadata(width = 0))

        assertEquals(MediaValidationCode.DIMENSIONS_UNKNOWN, result.code)
    }

    @Test
    fun `audio can be optional for silent media workflows`() {
        val result = MediaValidator.validate(metadata(hasAudio = false), requireAudio = false)

        assertTrue(result.valid)
    }

    private fun metadata(
        sizeBytes: Long = 1_000L,
        width: Int = 1_080,
        height: Int = 1_920,
        hasAudio: Boolean = true
    ) = MediaMetadata(
        path = "/tmp/video.mp4",
        sizeBytes = sizeBytes,
        durationMs = 30_000L,
        width = width,
        height = height,
        hasAudio = hasAudio
    )
}
