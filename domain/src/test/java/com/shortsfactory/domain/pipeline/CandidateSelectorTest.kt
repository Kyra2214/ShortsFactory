package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.ShortCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSelectorTest {

    private fun candidate(
        id: Long = 1,
        startMs: Long = 0,
        endMs: Long = 30_000,
        score: Float = 0.8f
    ) = ShortCandidate(
        score = score,
        startMs = startMs,
        endMs = endMs,
        title = "Candidato $id",
        hook = "",
        topic = "",
        reason = "",
        focusTrack = null,
        subtitles = emptyList()
    )

    private val selector = CandidateSelector()

    @Test
    fun `selects non-overlapping top candidates within duration preset`() {
        val candidates = listOf(
            candidate(id = 1, startMs = 0, endMs = 30_000, score = 0.9f),
            candidate(id = 2, startMs = 10_000, endMs = 40_000, score = 0.8f),
            candidate(id = 3, startMs = 60_000, endMs = 90_000, score = 0.7f)
        )
        val picked = selector.select(candidates, GenerationConfig(preset = "30s", maxCandidates = 2))
        assertEquals(2, picked.size)
        assertEquals("Candidato 1", picked[0].title)
        assertEquals("Candidato 3", picked[1].title)
    }

    @Test
    fun `rejects candidates longer than the preset`() {
        val candidates = listOf(
            candidate(id = 1, startMs = 0, endMs = 45_000, score = 0.9f)
        )
        val picked = selector.select(candidates, GenerationConfig(preset = "30s", maxCandidates = 2))
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `ai preset accepts any duration, orders by score and honors max candidates`() {
        val candidates = listOf(
            candidate(id = 1, startMs = 0, endMs = 120_000, score = 0.5f),
            candidate(id = 2, startMs = 0, endMs = 60_000, score = 0.9f)
        )
        val picked = selector.select(candidates, GenerationConfig(preset = "ai", maxCandidates = 1))
        assertEquals(1, picked.size)
        assertEquals("Candidato 2", picked[0].title)
    }

    @Test
    fun `rejects invalid ranges and negative starts`() {
        val picked = selector.select(
            listOf(
                candidate(id = 1, startMs = -1, endMs = 1_000, score = 0.9f),
                candidate(id = 2, startMs = 2_000, endMs = 2_000, score = 0.8f),
                candidate(id = 3, startMs = 3_000, endMs = 4_000, score = 0.7f)
            ),
            GenerationConfig(preset = "30s", maxCandidates = 3)
        )

        assertEquals(listOf("Candidato 3"), picked.map { it.title })
    }

    @Test
    fun `zero max candidates returns empty`() {
        assertTrue(selector.select(listOf(candidate()), GenerationConfig("30s", 0)).isEmpty())
    }
}
