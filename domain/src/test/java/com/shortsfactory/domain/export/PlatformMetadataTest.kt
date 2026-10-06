package com.shortsfactory.domain.export

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.pipeline.GenerationSummaryHint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica, em testes suspensos, que o bloco lança [T]. */
internal suspend inline fun <reified T : Throwable> failsWith(block: suspend () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw e
    }
    throw AssertionError("Esperava ${T::class.simpleName}, mas nada foi lançado.")
}

internal class FakeTextAi(private val reply: String) : AIProvider {
    override val providerName: String = "fake"
    override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult =
        throw UnsupportedOperationException()
    override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> =
        throw UnsupportedOperationException()
    override suspend fun analyzeTrends(query: String, region: String): String = throw UnsupportedOperationException()
    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
        throw UnsupportedOperationException()
    override suspend fun generateText(prompt: String): String = reply
}

class PlatformMetadataTest {
    private val yt = PlatformProfiles.forPlatform(ExportPlatform.YOUTUBE)
    private val ig = PlatformProfiles.forPlatform(ExportPlatform.INSTAGRAM)

    @Test fun `titulo e cortado no limite do perfil`() {
        val m = PlatformMetadataValidator.sanitize(yt, "t".repeat(300), "d", emptyList())!!
        assertEquals(yt.textLimits.titleMaxChars, m.title.length)
    }

    @Test fun `plataforma sem titulo descarta o titulo`() {
        val m = PlatformMetadataValidator.sanitize(ig, "Titulo", "Descricao", emptyList())!!
        assertEquals("", m.title)
        assertEquals("Descricao", m.description)
    }

    @Test fun `hashtags sao normalizadas sem duplicadas e invalidas`() {
        val m = PlatformMetadataValidator.sanitize(ig, "", "d", listOf("a", "#B", "b", "c d", "!!"))!!
        assertEquals(listOf("#a", "#B", "#cd"), m.hashtags)
    }

    @Test fun `quantidade de hashtags respeita o limite`() {
        val tags = (1..100).map { "tag$it" }
        assertEquals(ig.textLimits.maxHashtags, PlatformMetadataValidator.sanitize(ig, "", "d", tags)!!.hashtags.size)
    }

    @Test fun `descricao mais hashtags cabem no limite`() {
        val m = PlatformMetadataValidator.sanitize(yt, "t", "x".repeat(9000), listOf("a", "b", "c"))!!
        val tagsLength = m.hashtags.sumOf { it.length + 1 } + 1
        assertTrue(m.description.length + tagsLength <= yt.textLimits.descriptionMaxChars)
    }

    @Test fun `texto vazio nao gera metadado`() {
        assertNull(PlatformMetadataValidator.sanitize(ig, "", "  ", listOf("a")))
    }

    @Test fun `parser aceita texto em volta e ignora plataforma nao pedida`() {
        val reply = """Segue: {"platforms":[{"platform":"yt","title":"A","description":"B","hashtags":["#x"]},{"platform":"tt","title":"","description":"C","hashtags":[]}]} fim"""
        val items = PlatformMetadataParser.parse(reply, listOf(yt))
        assertEquals(1, items.size)
        assertEquals(ExportPlatform.YOUTUBE, items.single().platform)
        assertEquals(listOf("#x"), items.single().hashtags)
    }

    @Test fun `parser rejeita resposta sem json`() {
        assertThrows(IllegalArgumentException::class.java) { PlatformMetadataParser.parse("sem json", listOf(yt)) }
    }

    @Test fun `gerador devolve metadados validos`() = runTest {
        val ai = FakeTextAi("""{"platforms":[{"platform":"yt","title":"Titulo","description":"Desc","hashtags":["#a"]}]}""")
        val result = PlatformMetadataGenerator(ai).generate(PlatformMetadataRequest("t", "h", "tema", "d", listOf(yt)))
        assertEquals("Titulo", result.single().title)
    }

    @Test fun `gerador falha sem inventar quando a resposta e invalida`() = runTest {
        val request = PlatformMetadataRequest("t", "h", "tema", "d", listOf(yt))
        failsWith<AiException> { PlatformMetadataGenerator(FakeTextAi("lixo")).generate(request) }
        val other = FakeTextAi("""{"platforms":[{"platform":"tt","title":"","description":"x","hashtags":[]}]}""")
        failsWith<AiException> { PlatformMetadataGenerator(other).generate(request) }
    }

    @Test fun `fallback usa so titulo e gancho`() {
        val onYt = PlatformMetadataValidator.fallback(yt, "Titulo", "Gancho")!!
        assertEquals("Titulo", onYt.title)
        assertEquals("Gancho", onYt.description)
        val onIg = PlatformMetadataValidator.fallback(ig, "Titulo", "Gancho")!!
        assertEquals("", onIg.title)
        assertTrue(onIg.description.contains("Titulo") && onIg.description.contains("Gancho"))
        assertNotNull(onIg)
    }

    @Test fun `provedor padrao nao oferece geracao de texto`() = runTest {
        val bare = object : AIProvider {
            override val providerName = "bare"
            override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult =
                throw UnsupportedOperationException()
            override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> =
                throw UnsupportedOperationException()
            override suspend fun analyzeTrends(query: String, region: String): String = throw UnsupportedOperationException()
            override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
                throw UnsupportedOperationException()
        }
        failsWith<AiException> { bare.generateText("x") }
    }
}
