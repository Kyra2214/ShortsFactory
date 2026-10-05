package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.SubtitleStyleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

/** Goldens validados num FFmpeg real (6.1): cadeia crop/scale/fps e grafo de overlays de legenda. */
class FfmpegFilterBuilderTest {

    private val style = SubtitleStyleConfig("creator", 64, 78.0)
    private val base =
        "crop=w=min(iw\\,ih*1080/1920):h=min(ih\\,iw*1920/1080):x=(iw-ow)/2:y=(ih-oh)/2,scale=1080:1920,fps=30"

    private fun spec(
        subtitles: List<SubtitleSegment> = emptyList(),
        startMs: Long = 10_000L,
        endMs: Long = 13_000L,
        fps: Int = 30,
        width: Int = 1080,
        height: Int = 1920
    ) = ClipSpec(
        inputPath = "in.mp4", outputPath = "out.mp4", startMs = startMs, endMs = endMs,
        targetWidth = width, targetHeight = height, fps = fps,
        focusTrack = null, subtitles = subtitles, subtitleStyle = style
    )

    @Test
    fun `video filter without track is centered crop scale fps`() {
        assertEquals(base, FfmpegFilterBuilder.videoFilter(spec()))
    }

    @Test
    fun `extra filters are appended and blank ones ignored`() {
        assertEquals("$base,eq=brightness=0.1", FfmpegFilterBuilder.videoFilter(spec(), listOf("", "eq=brightness=0.1")))
    }

    @Test
    fun `invalid fps and interval are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FfmpegFilterBuilder.videoFilter(spec(fps = 0)) }
        assertThrows(IllegalArgumentException::class.java) { FfmpegFilterBuilder.videoFilter(spec(fps = 121)) }
        assertThrows(IllegalArgumentException::class.java) { FfmpegFilterBuilder.videoFilter(spec(startMs = 5L, endMs = 5L)) }
        assertThrows(IllegalArgumentException::class.java) { FfmpegFilterBuilder.videoFilter(spec(width = 0)) }
    }

    @Test
    fun `no subtitles means no graph`() {
        assertNull(FfmpegFilterBuilder.filterGraph(spec()))
    }

    @Test
    fun `subtitles fully outside the clip mean no graph`() {
        assertNull(FfmpegFilterBuilder.filterGraph(spec(listOf(SubtitleSegment(1_000L, 2_000L, listOf("fora"))))))
    }

    @Test
    fun `one subtitle becomes one overlay with clip relative times`() {
        val graph = FfmpegFilterBuilder.filterGraph(spec(listOf(SubtitleSegment(10_500L, 11_500L, listOf("oi")))))!!
        assertEquals(
            "[0:v]$base[b0];[b0][1:v]overlay=x=(W-w)/2:y=min(H-h\\,H*78.0/100-h/2):" +
                "enable='between(t\\,0.500\\,1.500)'[b1]",
            graph.filterComplex
        )
        assertEquals("b1", graph.outputLabel)
        assertEquals(listOf(SubtitleSegment(500L, 1_500L, listOf("oi"))), graph.subtitles)
    }

    @Test
    fun `two subtitles are chained in time order and clipped to the clip`() {
        val graph = FfmpegFilterBuilder.filterGraph(
            spec(
                listOf(
                    SubtitleSegment(12_000L, 14_000L, listOf("b")),
                    SubtitleSegment(9_000L, 10_250L, listOf("a"))
                )
            )
        )!!
        assertEquals(listOf(SubtitleSegment(0L, 250L, listOf("a")), SubtitleSegment(2_000L, 3_000L, listOf("b"))), graph.subtitles)
        assertEquals("b2", graph.outputLabel)
        assertEquals(
            "[0:v]$base[b0];" +
                "[b0][1:v]overlay=x=(W-w)/2:y=min(H-h\\,H*78.0/100-h/2):enable='between(t\\,0.000\\,0.250)'[b1];" +
                "[b1][2:v]overlay=x=(W-w)/2:y=min(H-h\\,H*78.0/100-h/2):enable='between(t\\,2.000\\,3.000)'[b2]",
            graph.filterComplex
        )
    }

    @Test
    fun `times use a dot decimal separator regardless of default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val graph = FfmpegFilterBuilder.filterGraph(spec(listOf(SubtitleSegment(10_500L, 11_500L, listOf("oi")))))!!
            assertEquals(true, graph.filterComplex.contains("between(t\\,0.500\\,1.500)"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
