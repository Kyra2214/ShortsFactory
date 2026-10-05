package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimingTest {

    @Test
    fun `fromTranscript clips to the interval and keeps absolute time`() {
        val result = SubtitleTiming.fromTranscript(
            listOf(
                TranscriptSegment(0L, 4_000L, "  olá   mundo "),
                TranscriptSegment(5_000L, 6_000L, "fora")
            ),
            startMs = 1_000L, endMs = 3_000L
        )
        assertEquals(listOf(SubtitleSegment(1_000L, 3_000L, listOf("olá", "mundo"))), result)
    }

    @Test
    fun `fromTranscript drops blank text and touching segments`() {
        val result = SubtitleTiming.fromTranscript(
            listOf(
                TranscriptSegment(0L, 1_000L, "antes"),
                TranscriptSegment(1_000L, 2_000L, "   "),
                TranscriptSegment(3_000L, 4_000L, "depois")
            ),
            startMs = 1_000L, endMs = 3_000L
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toClipRelative subtracts the clip start and clips both ends`() {
        val result = SubtitleTiming.toClipRelative(
            listOf(SubtitleSegment(9_000L, 11_000L, listOf("a")), SubtitleSegment(12_000L, 14_000L, listOf("b"))),
            clipStartMs = 10_000L, clipEndMs = 13_000L
        )
        assertEquals(
            listOf(SubtitleSegment(0L, 1_000L, listOf("a")), SubtitleSegment(2_000L, 3_000L, listOf("b"))),
            result
        )
    }

    @Test
    fun `toClipRelative drops outside empty and wordless segments and sorts`() {
        val result = SubtitleTiming.toClipRelative(
            listOf(
                SubtitleSegment(11_000L, 12_000L, listOf("z")),
                SubtitleSegment(1_000L, 2_000L, listOf("fora")),
                SubtitleSegment(14_000L, 15_000L, listOf("fora")),
                SubtitleSegment(10_500L, 10_500L, listOf("vazio")),
                SubtitleSegment(10_000L, 10_900L, emptyList()),
                SubtitleSegment(10_000L, 10_400L, listOf("a"))
            ),
            clipStartMs = 10_000L, clipEndMs = 13_000L
        )
        assertEquals(
            listOf(SubtitleSegment(0L, 400L, listOf("a")), SubtitleSegment(1_000L, 2_000L, listOf("z"))),
            result
        )
    }
}
