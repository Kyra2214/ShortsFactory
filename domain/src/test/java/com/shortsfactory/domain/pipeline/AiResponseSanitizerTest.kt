package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.ai.AiHttpException
import com.shortsfactory.domain.ai.isTransientFailure
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseSanitizerTest {

    private val transcript = Transcript(
        listOf(TranscriptSegment(2_000L, 10_000L, "a"), TranscriptSegment(10_000L, 60_000L, "b"))
    )

    private fun c(start: Long, end: Long, title: String = "ok") =
        ShortCandidate(0.5f, start, end, title, "h", "t", "r")

    private fun reasons(r: SanitizedCandidates) = r.discarded.map { it.reason }

    @Test
    fun `candidato valido passa`() {
        val r = AiResponseSanitizer.sanitize(listOf(c(5_000, 30_000)), 120_000L, transcript)
        assertEquals(1, r.kept.size)
        assertTrue(r.discarded.isEmpty())
    }

    @Test
    fun `descarta sem titulo, sem intervalo, invertido e negativo`() {
        val r = AiResponseSanitizer.sanitize(
            listOf(
                c(5_000, 30_000, title = "  "),
                c(ShortCandidate.MISSING_MS, 30_000),
                c(5_000, ShortCandidate.MISSING_MS),
                c(30_000, 30_000),
                c(30_000, 20_000),
                c(-5_000, 20_000)
            ),
            120_000L, transcript
        )
        assertTrue(r.kept.isEmpty())
        assertEquals(
            listOf(
                DiscardReason.BLANK_TITLE, DiscardReason.MISSING_INTERVAL, DiscardReason.MISSING_INTERVAL,
                DiscardReason.INVALID_INTERVAL, DiscardReason.INVALID_INTERVAL, DiscardReason.INVALID_INTERVAL
            ),
            reasons(r)
        )
    }

    @Test
    fun `descarta fim alem da duracao do video mas aceita exatamente a duracao`() {
        val r = AiResponseSanitizer.sanitize(listOf(c(5_000, 60_001), c(5_000, 60_000)), 60_000L, transcript)
        assertEquals(listOf(DiscardReason.OUTSIDE_VIDEO), reasons(r))
        assertEquals(1, r.kept.size)
    }

    @Test
    fun `duracao desconhecida nao aplica a regra do video`() {
        val r = AiResponseSanitizer.sanitize(listOf(c(5_000, 60_000)), 0L, transcript)
        assertEquals(1, r.kept.size)
    }

    @Test
    fun `descarta fora da regiao transcrita respeitando a folga de 1 segundo`() {
        val r = AiResponseSanitizer.sanitize(
            listOf(c(0, 5_000), c(1_000, 5_000), c(5_000, 61_000), c(5_000, 61_001), c(70_000, 80_000)),
            0L, transcript
        )
        // cobertura 2_000..60_000, folga 1_000 → início ≥ 1_000 e fim ≤ 61_000
        assertEquals(listOf(DiscardReason.OUTSIDE_TRANSCRIPT, DiscardReason.OUTSIDE_TRANSCRIPT, DiscardReason.OUTSIDE_TRANSCRIPT), reasons(r))
        assertEquals(listOf(1_000L to 5_000L, 5_000L to 61_000L), r.kept.map { it.startMs to it.endMs })
    }

    @Test
    fun `resumo conta por motivo`() {
        val r = AiResponseSanitizer.sanitize(
            listOf(c(5_000, 30_000), c(0, 0, "x"), c(0, 0, "y"), c(5_000, 999_000)), 120_000L, transcript
        )
        val s = r.summary()
        assertTrue(s, s.startsWith("1 válidos, 3 descartados"))
        assertTrue(s, s.contains("intervalo inválido: 2"))
        assertTrue(s, s.contains("fora da duração do vídeo: 1"))
    }

    @Test
    fun `classificacao de falha transitoria e tipada`() {
        assertTrue(AiHttpException(429).isTransientFailure())
        assertTrue(AiHttpException(408).isTransientFailure())
        assertTrue(AiHttpException(503).isTransientFailure())
        assertFalse(AiHttpException(401).isTransientFailure())
        assertFalse(AiHttpException(404).isTransientFailure())
        assertTrue(SocketTimeoutException("x").isTransientFailure())
        assertTrue(AiException("wrap", transient = false, cause = IOException("rede")).isTransientFailure())
        assertFalse(AiException("timeout na mensagem não conta").isTransientFailure())
        assertFalse(IllegalStateException("HTTP 503 só no texto").isTransientFailure())
    }
}
