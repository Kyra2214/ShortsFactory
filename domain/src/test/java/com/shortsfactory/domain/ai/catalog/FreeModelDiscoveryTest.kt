package com.shortsfactory.domain.ai.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeModelDiscoveryTest {

    @Test
    fun parseLeDataERemovePrefixoModels() {
        val list = FreeModelDiscovery.parse("""{"data":[{"id":"models/gemini-flash"},{"id":"m2"},{"id":"m2"},{"id":""}]}""")
        assertEquals(listOf("gemini-flash", "m2"), list.map { it.id })
        assertTrue(list.all { it.free == null })
    }

    @Test
    fun parseAceitaChaveModels() {
        assertEquals(listOf("a"), FreeModelDiscovery.parse("""{"models":[{"id":"a"}]}""").map { it.id })
    }

    @Test
    fun parseInvalidoOuSemListaRetornaVazio() {
        assertTrue(FreeModelDiscovery.parse("não é json").isEmpty())
        assertTrue(FreeModelDiscovery.parse("""{"x":1}""").isEmpty())
    }

    @Test
    fun parseLePrecoZeroESufixoFree() {
        val list = FreeModelDiscovery.parse(
            """{"data":[
            {"id":"a","pricing":{"prompt":"0","completion":"0"}},
            {"id":"b","pricing":{"prompt":"0.001","completion":"0"}},
            {"id":"c:free"},
            {"id":"d","pricing":{"prompt":"x"}}]}"""
        ).associate { it.id to it.free }
        assertEquals(true, list["a"])
        assertEquals(false, list["b"])
        assertEquals(true, list["c:free"])
        assertNull(list["d"])
    }

    @Test
    fun selectComPrecoMantemSoGratuitos() {
        val result = FreeModelDiscovery.select(
            listOf(DiscoveredModel("pago", false), DiscoveredModel("g1", true), DiscoveredModel("g2", true))
        )
        assertEquals(listOf("g1", "g2"), result)
    }

    @Test
    fun selectSemPrecoNaoAssumeQueModeloEhGratuito() {
        val result = FreeModelDiscovery.select(
            listOf(
                DiscoveredModel("big-model", null),
                DiscoveredModel("text-embedding-3", null),
                DiscoveredModel("whisper-large", null),
                DiscoveredModel("model-flash", null),
                DiscoveredModel("model-explicit:free", true)
            )
        )
        assertEquals(listOf("model-explicit:free"), result)
    }

    @Test
    fun selectLimitaQuantidade() {
        val many = (1..20).map { DiscoveredModel("m$it:free", true) }
        assertEquals(FreeModelDiscovery.MAX_MODELS, FreeModelDiscovery.select(many).size)
    }
}
