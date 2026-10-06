package com.shortsfactory.domain.export

import com.shortsfactory.domain.pipeline.InputVideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExportOutputValidatorTest {

    private fun info(w: Int = 1080, h: Int = 1920, d: Long = 30_000, audio: Boolean = true) =
        InputVideoInfo(path = "x.mp4", durationMs = d, width = w, height = h, fps = 30.0, hasAudio = audio)

    private fun check(i: InputVideoInfo) = ExportOutputValidator.validate(i, 1080, 1920, 30_000)

    @Test
    fun validFileHasNoError() = assertNull(check(info()))

    @Test
    fun toleratesOneSecondOfDuration() = assertNull(check(info(d = 31_000)))

    @Test
    fun wrongResolutionIsRejected() = assertNotNull(check(info(w = 720, h = 1280)))

    @Test
    fun invalidDurationIsRejected() {
        assertNotNull(check(info(d = 0)))
        assertNotNull(check(info(d = 31_001)))
    }

    @Test
    fun missingAudioIsRejected() = assertEquals("O arquivo exportado não contém áudio.", check(info(audio = false)))
}
