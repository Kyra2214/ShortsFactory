package com.shortsfactory.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfprobeParserTest {

    @Test
    fun `parses flat ffprobe output`() {
        val output = """
            streams.stream.0.width=1920
            streams.stream.0.height=1080
            streams.stream.0.avg_frame_rate="30000/1001"
            streams.stream.0.codec_type="video"
            streams.stream.1.codec_type="audio"
            format.duration="12.5"
        """.trimIndent()

        val info = FfprobeParser.parse("input.mp4", output)

        assertEquals(12_500L, info.durationMs)
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(29.970029, info.fps, 0.000001)
        assertTrue(info.hasAudio)
    }

    @Test
    fun `uses safe defaults when metadata is unavailable`() {
        val info = FfprobeParser.parse("input.mp4", "")

        assertEquals(0L, info.durationMs)
        assertEquals(0, info.width)
        assertEquals(0, info.height)
        assertEquals(30.0, info.fps, 0.0)
        assertTrue(!info.hasAudio)
    }
}
