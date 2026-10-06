package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ShortCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        reason = ""
    )

    private val selector = CandidateSelector()
    private fun cfg(preset: String, max: Int = 12) = GenerationConfig(preset, max)
    private fun titles(list: List<ShortCandidate>) = list.map { it.title }

    @Test
    fun `selects non-overlapping top candidates within duration preset`() {
        val picked = selector.select(
            listOf(
                candidate(1, 0, 30_000, 0.9f),
                candidate(2, 10_000, 40_000, 0.8f),
                candidate(3, 60_000, 90_000, 0.7f)
            ),
            cfg("30s", 2)
        )
        assertEquals(listOf("Candidato 1", "Candidato 3"), titles(picked))
    }

    @Test
    fun `rejects candidates longer than the preset`() {
        assertTrue(selector.select(listOf(candidate(1, 0, 45_000, 0.9f)), cfg("30s", 2)).isEmpty())
    }

    @Test
    fun `rejects invalid ranges and negative starts`() {
        val picked = selector.select(
            listOf(
                candidate(1, -1, 10_000, 0.9f),
                candidate(2, 20_000, 20_000, 0.8f),
                candidate(3, 30_000, 40_000, 0.7f)
            ),
            cfg("30s", 3)
        )
        assertEquals(listOf("Candidato 3"), titles(picked))
    }

    @Test
    fun `zero or negative max candidates returns empty`() {
        assertTrue(selector.select(listOf(candidate()), cfg("30s", 0)).isEmpty())
        assertTrue(selector.select(listOf(candidate()), cfg("ai", -5)).isEmpty())
    }

    // --- preset "ai" segue as mesmas regras ---

    @Test
    fun `ai preset removes overlap and keeps the highest score`() {
        val picked = selector.select(
            listOf(
                candidate(1, 0, 40_000, 0.6f),
                candidate(2, 20_000, 60_000, 0.9f),
                candidate(3, 70_000, 100_000, 0.5f)
            ),
            cfg("ai")
        )
        assertEquals(listOf("Candidato 2", "Candidato 3"), titles(picked))
    }

    @Test
    fun `ai preset without suggestion caps at the global maximum`() {
        val picked = selector.select(
            listOf(
                candidate(1, 0, 120_000, 0.9f),
                candidate(2, 200_000, 290_000, 0.5f)
            ),
            cfg("ai")
        )
        assertEquals(listOf("Candidato 2"), titles(picked))
    }

    @Test
    fun `ai preset uses the suggested duration as ceiling`() {
        val candidates = listOf(
            candidate(1, 0, 50_000, 0.9f),
            candidate(2, 100_000, 125_000, 0.5f)
        )
        assertEquals(
            listOf("Candidato 2"),
            titles(selector.select(candidates, cfg("ai"), suggestedDurationMs = 30_000L))
        )
    }

    @Test
    fun `suggested duration is clamped to the global bounds`() {
        val long = listOf(candidate(1, 0, 100_000, 0.9f))
        assertTrue(selector.select(long, cfg("ai"), suggestedDurationMs = 500_000L).isEmpty())
        val short = listOf(candidate(1, 0, 3_000, 0.9f))
        assertEquals(1, selector.select(short, cfg("ai"), suggestedDurationMs = 500L).size)
    }

    @Test
    fun `suggested duration is ignored for fixed presets`() {
        val picked = selector.select(
            listOf(candidate(1, 0, 25_000, 0.9f)),
            cfg("30s"),
            suggestedDurationMs = 10_000L
        )
        assertEquals(1, picked.size)
    }

    // --- limites do vídeo e duração mínima ---

    @Test
    fun `rejects candidate ending after the video and accepts one ending exactly at the end`() {
        val picked = selector.select(
            listOf(
                candidate(1, 90_000, 100_001, 0.9f),
                candidate(2, 80_000, 100_000, 0.5f)
            ),
            cfg("30s"),
            videoDurationMs = 100_000L
        )
        assertEquals(listOf("Candidato 2"), titles(picked))
    }

    @Test
    fun `unknown video duration does not limit the end`() {
        val c = listOf(candidate(1, 500_000, 520_000, 0.9f))
        assertEquals(1, selector.select(c, cfg("30s"), videoDurationMs = null).size)
        assertEquals(1, selector.select(c, cfg("30s"), videoDurationMs = 0L).size)
    }

    @Test
    fun `rejects candidates shorter than the minimum for every preset`() {
        val tiny = listOf(candidate(1, 0, 2_999, 0.9f))
        listOf("15s", "30s", "45s", "60s", "90s", "ai").forEach { preset ->
            assertTrue(preset, selector.select(tiny, cfg(preset)).isEmpty())
        }
        assertEquals(1, selector.select(listOf(candidate(1, 0, 3_000, 0.9f)), cfg("30s")).size)
    }

    @Test
    fun `custom rules change the minimum and the ai ceiling`() {
        val custom = CandidateSelector(SelectionRules(minDurationMs = 10_000L, aiMaxDurationMs = 20_000L))
        val picked = custom.select(
            listOf(
                candidate(1, 0, 5_000, 0.9f),
                candidate(2, 10_000, 40_000, 0.8f),
                candidate(3, 50_000, 65_000, 0.7f)
            ),
            cfg("ai")
        )
        assertEquals(listOf("Candidato 3"), titles(picked))
    }

    // --- determinismo ---

    @Test
    fun `equal scores are ordered by start then end`() {
        val picked = selector.select(
            listOf(
                candidate(3, 60_000, 80_000, 0.5f),
                candidate(1, 0, 20_000, 0.5f),
                candidate(2, 30_000, 50_000, 0.5f)
            ),
            cfg("30s")
        )
        assertEquals(listOf("Candidato 1", "Candidato 2", "Candidato 3"), titles(picked))
    }

    @Test
    fun `equal scores that overlap keep the earliest one`() {
        val picked = selector.select(
            listOf(candidate(2, 10_000, 30_000, 0.5f), candidate(1, 0, 20_000, 0.5f)),
            cfg("30s")
        )
        assertEquals(listOf("Candidato 1"), titles(picked))
    }

    @Test
    fun `result does not depend on input order`() {
        val base = listOf(
            candidate(1, 0, 20_000, 0.7f),
            candidate(2, 10_000, 30_000, 0.7f),
            candidate(3, 40_000, 60_000, 0.9f),
            candidate(4, 50_000, 70_000, 0.9f),
            candidate(5, 80_000, 95_000, 0.2f)
        )
        val expected = titles(selector.select(base, cfg("30s")))
        repeat(10) { seed ->
            val shuffled = base.shuffled(java.util.Random(seed.toLong()))
            assertEquals(expected, titles(selector.select(shuffled, cfg("30s"))))
        }
    }

    @Test
    fun `touching intervals are not overlap`() {
        val picked = selector.select(
            listOf(candidate(1, 0, 10_000, 0.9f), candidate(2, 10_000, 20_000, 0.8f)),
            cfg("30s")
        )
        assertEquals(2, picked.size)
    }

    @Test
    fun `no returned candidate is impossible or overlapping for any preset`() {
        val messy = listOf(
            candidate(1, -5, 10_000, 0.9f),
            candidate(2, 0, 1_000, 0.9f),
            candidate(3, 5_000, 200_000, 0.9f),
            candidate(4, 0, 25_000, 0.8f),
            candidate(5, 20_000, 45_000, 0.8f),
            candidate(6, 40_000, 70_000, 0.7f),
            candidate(7, 95_000, 140_000, 0.7f),
            candidate(8, 60_000, 60_000, 0.7f)
        )
        listOf("15s", "30s", "45s", "60s", "90s", "ai").forEach { key ->
            val maxMs = DurationPreset.fromKey(key).maxMs ?: 90_000L
            val picked = selector.select(messy, cfg(key), videoDurationMs = 120_000L)
            picked.forEach {
                assertTrue(key, it.startMs >= 0 && it.endMs <= 120_000L)
                assertTrue(key, it.endMs - it.startMs in 3_000L..maxMs)
            }
            picked.forEachIndexed { i, a ->
                picked.drop(i + 1).forEach { b -> assertTrue(key, a.endMs <= b.startMs || b.endMs <= a.startMs) }
            }
        }
    }

    // --- relatório e preset desconhecido ---

    @Test
    fun `report explains every rejection`() {
        val result = selector.selectWithReport(
            listOf(
                candidate(1, -1, 10_000, 0.9f),     // INVALID_RANGE
                candidate(2, 0, 1_000, 0.9f),       // TOO_SHORT
                candidate(3, 0, 50_000, 0.9f),      // TOO_LONG
                candidate(4, 90_000, 110_000, 0.9f), // BEYOND_VIDEO
                candidate(5, 0, 20_000, 0.8f),      // selecionado
                candidate(6, 10_000, 30_000, 0.7f), // OVERLAP
                candidate(7, 40_000, 60_000, 0.6f)  // OVER_LIMIT
            ),
            cfg("30s", 1),
            videoDurationMs = 100_000L
        )
        assertEquals(listOf("Candidato 5"), titles(result.selected))
        val reasons = result.rejected.associate { it.candidate.title to it.reason }
        assertEquals(RejectionReason.INVALID_RANGE, reasons["Candidato 1"])
        assertEquals(RejectionReason.TOO_SHORT, reasons["Candidato 2"])
        assertEquals(RejectionReason.TOO_LONG, reasons["Candidato 3"])
        assertEquals(RejectionReason.BEYOND_VIDEO, reasons["Candidato 4"])
        assertEquals(RejectionReason.OVER_LIMIT, reasons["Candidato 6"])
        assertEquals(RejectionReason.OVER_LIMIT, reasons["Candidato 7"])
        assertEquals(6, result.rejected.size)
        assertTrue(result.summary().startsWith("1 selecionados, 6 rejeitados"))
    }

    @Test
    fun `overlap is reported when the limit still has room`() {
        val result = selector.selectWithReport(
            listOf(candidate(1, 0, 20_000, 0.9f), candidate(2, 10_000, 30_000, 0.5f)),
            cfg("30s", 5)
        )
        assertEquals(RejectionReason.OVERLAP, result.rejected.single().reason)
    }

    @Test
    fun `unknown preset fails with a clear error instead of a silent default`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            selector.select(listOf(candidate()), cfg("120s"))
        }
        assertTrue(e.message!!.contains("120s"))
        assertThrows(IllegalArgumentException::class.java) { selector.select(emptyList(), cfg("")) }
    }

    @Test
    fun `invalid selection rules are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SelectionRules(minDurationMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { SelectionRules(minDurationMs = 5_000L, aiMaxDurationMs = 1_000L) }
    }
}
