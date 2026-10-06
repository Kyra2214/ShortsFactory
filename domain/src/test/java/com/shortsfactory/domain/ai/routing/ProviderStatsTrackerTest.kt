package com.shortsfactory.domain.ai.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStatsTrackerTest {

    private var clock = 1_000_000L
    private fun tracker(json: String? = null, onChange: ((String) -> Unit)? = null) =
        ProviderStatsTracker(json, { clock }, onChange)

    @Test
    fun semHistoricoPreservaOrdemOriginal() {
        assertEquals(listOf("a", "b", "c"), tracker().order(listOf("a", "b", "c")))
    }

    @Test
    fun sucessosSobemEFalhasDescem() {
        val t = tracker()
        repeat(3) { t.record("b", true, 500) }
        repeat(3) { t.record("a", false, 500) }
        assertEquals(listOf("b", "c", "a"), t.order(listOf("a", "b", "c")))
    }

    @Test
    fun falhaRecenteEntraEmQuarentenaEDepoisVolta() {
        val t = tracker()
        repeat(5) { t.record("a", true, 100) }
        t.record("a", false, 100)
        assertEquals(listOf("b", "a"), t.order(listOf("a", "b")))
        clock += 10 * 60_000L
        assertEquals(listOf("a", "b"), t.order(listOf("a", "b")))
    }

    @Test
    fun sucessoZeraFalhasConsecutivas() {
        val t = tracker()
        t.record("a", false, 100)
        t.record("a", true, 100)
        assertEquals(0, t.snapshot().getValue("a").consecutiveFailures)
    }

    @Test
    fun menorLatenciaDesempata() {
        val t = tracker()
        repeat(3) { t.record("lento", true, 50_000) }
        repeat(3) { t.record("rapido", true, 200) }
        assertEquals(listOf("rapido", "lento"), t.order(listOf("lento", "rapido")))
    }

    @Test
    fun serializaERestauraEstatisticas() {
        var saved = ""
        val t = tracker(onChange = { saved = it })
        t.record("a", true, 300)
        t.record("a", false, 300)
        val restored = tracker(saved)
        assertEquals(t.snapshot(), restored.snapshot())
    }

    @Test
    fun jsonInvalidoComecaVazio() {
        assertTrue(tracker("lixo").snapshot().isEmpty())
    }

    @Test
    fun historicoLongoEReduzido() {
        val t = tracker()
        repeat(300) { t.record("a", true, 100) }
        assertTrue(t.snapshot().getValue("a").successes <= 200)
    }
}
