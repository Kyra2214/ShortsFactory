package com.shortsfactory.domain.ai.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApiCatalogParserTest {

    private fun provider(
        id: String = "p1",
        model: String = """{"id":"m1","name":"M1","capabilities":["chat"],"access":"FREE_TIER","endpoint":"https://api.p1.com/v1/","requiresKey":true}""",
        extra: String = ""
    ) = """{"id":"$id","name":"P $id","region":"GLOBAL","officialUrl":"https://p.com","documentationUrl":"https://p.com/docs",$extra"models":[$model]}"""

    private fun catalog(vararg providers: String) = """{"version":"v1","providers":[${providers.joinToString(",")}]}"""

    @Test
    fun parsesValidProviderAndDerivesEndpoints() {
        val result = ApiCatalogParser.parse(catalog(provider()))
        val p = result.provider("p1")!!
        assertEquals("v1", result.version)
        assertEquals(ApiRegion.GLOBAL, p.region)
        assertEquals("https://api.p1.com/v1", p.chatBaseUrl)
        assertEquals("https://api.p1.com/v1/chat/completions", p.chatCompletionsUrl)
        assertEquals("https://api.p1.com/v1/models", p.modelsEndpoint)
        assertTrue(p.requiresKey)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun declaredModelsEndpointWins() {
        val p = ApiCatalogParser.parse(catalog(provider(extra = """"modelsEndpoint":"https://m.p1.com/list",""")))
            .provider("p1")!!
        assertEquals("https://m.p1.com/list", p.modelsEndpoint)
    }

    @Test
    fun nonFreeModelsAreDropped() {
        val paid = """{"id":"m2","name":"M2","access":"PAID","endpoint":"https://api.p1.com/v1","requiresKey":true}"""
        val free = """{"id":"m1","name":"M1","access":"FREE_PERMANENT","endpoint":"https://api.p1.com/v1","requiresKey":true}"""
        val result = ApiCatalogParser.parse(catalog(provider(model = "$paid,$free")))
        assertEquals(listOf("m1"), result.provider("p1")!!.models.map { it.id })
        assertEquals(ApiAccess.FREE_PERMANENT, result.provider("p1")!!.models.first().access)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun providerWithoutFreeModelIsSkipped() {
        val paid = """{"id":"m2","name":"M2","access":"PAID","endpoint":"https://api.p1.com/v1"}"""
        val result = ApiCatalogParser.parse(catalog(provider(model = paid)))
        assertNull(result.provider("p1"))
        assertTrue(result.skipped.any { it.contains("nenhum modelo gratuito") })
    }

    @Test
    fun nonHttpsUrlsAreRejected() {
        val http = """{"id":"m1","name":"M1","access":"FREE_TIER","endpoint":"http://api.p1.com/v1"}"""
        assertNull(ApiCatalogParser.parse(catalog(provider(model = http))).provider("p1"))
        val badDocs = provider().replace("https://p.com/docs", "http://p.com/docs")
        assertNull(ApiCatalogParser.parse(catalog(badDocs)).provider("p1"))
        val badModels = provider(extra = """"modelsEndpoint":"http://m.p1.com/list",""")
        assertNull(ApiCatalogParser.parse(catalog(badModels)).provider("p1"))
    }

    @Test
    fun duplicateIdKeepsFirst() {
        val result = ApiCatalogParser.parse(catalog(provider(), provider()))
        assertEquals(1, result.providers.size)
        assertTrue(result.skipped.any { it.contains("duplicado") })
    }

    @Test
    fun unknownRegionBecomesOther() {
        val result = ApiCatalogParser.parse(catalog(provider().replace("GLOBAL", "MARS")))
        assertEquals(ApiRegion.OTHER, result.provider("p1")!!.region)
    }

    @Test
    fun modelsWithDifferentEndpointsSkipProvider() {
        val a = """{"id":"a","name":"A","access":"FREE_TIER","endpoint":"https://a.com/v1"}"""
        val b = """{"id":"b","name":"B","access":"FREE_TIER","endpoint":"https://b.com/v1"}"""
        assertNull(ApiCatalogParser.parse(catalog(provider(model = "$a,$b"))).provider("p1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedJsonFails() {
        ApiCatalogParser.parse("{not json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingProvidersListFails() {
        ApiCatalogParser.parse("""{"version":"v1"}""")
    }

    @Test
    fun bundledCatalogHasAllTenFreeProviders() {
        val file = listOf(
            File("../feature-ai/src/main/assets/ai_api_catalog.json"),
            File("feature-ai/src/main/assets/ai_api_catalog.json")
        ).firstOrNull { it.isFile }
        assertNotNull("ai_api_catalog.json não encontrado", file)
        val result = ApiCatalogParser.parse(file!!.readText())
        assertEquals(
            setOf("qwen", "moonshot", "volcengine", "siliconflow", "modelscope", "openrouter", "groq", "gemini", "mistral", "zai"),
            result.providers.map { it.id }.toSet()
        )
        assertTrue(result.skipped.isEmpty())
        assertEquals("https://api.z.ai/api/paas/v4/models", result.provider("zai")!!.modelsEndpoint)
        assertEquals("https://dashscope-intl.aliyuncs.com/api/v1/models", result.provider("qwen")!!.modelsEndpoint)
        assertTrue(result.providers.all { it.requiresKey })
    }
}
