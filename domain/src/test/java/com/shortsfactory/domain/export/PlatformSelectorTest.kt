package com.shortsfactory.domain.export

import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.model.ExportPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSelectorTest {
    private fun request(durationSec: Double) =
        PlatformSuggestionRequest("t", "h", "tema", "d", durationSec, PlatformProfiles.all())

    @Test fun `sugestao valida mantem justificativa`() = runTest {
        val ai = FakeTextAi("""{"platforms":[{"platform":"yt","reason":"Cabe em Shorts."},{"platform":"tt","reason":"Gancho forte."}]}""")
        val result = PlatformSelector(ai).suggest(request(30.0))
        assertEquals(listOf(ExportPlatform.YOUTUBE, ExportPlatform.TIKTOK), result.map { it.platform })
        assertEquals("Cabe em Shorts.", result.first().reason)
    }

    @Test fun `descarta justificativa vazia plataforma desconhecida e repetida`() {
        val reply = """{"platforms":[{"platform":"yt","reason":""},{"platform":"zz","reason":"x"},{"platform":"tt","reason":"a"},{"platform":"tt","reason":"b"}]}"""
        val result = PlatformSuggestionParser.parse(reply, request(30.0))
        assertEquals(listOf(ExportPlatform.TIKTOK), result.map { it.platform })
        assertEquals("a", result.single().reason)
    }

    @Test fun `descarta plataforma cuja duracao maxima nao comporta o corte`() {
        val reply = """{"platforms":[{"platform":"ig","reason":"a"},{"platform":"yt","reason":"b"}]}"""
        val result = PlatformSuggestionParser.parse(reply, request(120.0))
        assertEquals(listOf(ExportPlatform.YOUTUBE), result.map { it.platform })
    }

    @Test fun `justificativa longa e cortada`() {
        val reply = """{"platforms":[{"platform":"yt","reason":"${"r".repeat(500)}"}]}"""
        val reason = PlatformSuggestionParser.parse(reply, request(30.0)).single().reason
        assertTrue(reason.length <= PlatformSuggestionParser.MAX_REASON_CHARS)
    }

    @Test fun `sem sugestao valida lanca AiException`() = runTest {
        failsWith<AiException> { PlatformSelector(FakeTextAi("""{"platforms":[]}""")).suggest(request(30.0)) }
        failsWith<AiException> { PlatformSelector(FakeTextAi("sem json")).suggest(request(30.0)) }
    }

    @Test fun `prompt traz chaves duracao e limites das candidatas`() {
        val prompt = PlatformSelector(FakeTextAi("")).buildPrompt(request(45.0))
        assertTrue(prompt.contains("yt") && prompt.contains("45s") && prompt.contains("duração máxima"))
    }
}
