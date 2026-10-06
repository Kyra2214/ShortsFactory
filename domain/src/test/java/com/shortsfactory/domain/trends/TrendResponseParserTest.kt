package com.shortsfactory.domain.trends

import com.shortsfactory.domain.model.ProviderCapability
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrendResponseParserTest {

    @Test
    fun `views do LLM com openable true nao vira metrica`() {
        val cards = TrendResponseParser.parse(
            """{"trends":[{"title":"T","platform":"yt","region":"br","views":"2M","engagement":"8%","sourceUrl":"https://x.com/a","openable":true}]}"""
        )
        val c = cards.single()
        assertNull(c.views)
        assertNull(c.engagement)
        assertEquals(TrendOrigin.AI_INFERENCE, c.origin)
        assertFalse(c.metricsVerified)
        assertEquals("visualizações: 2M, engajamento: 8%", c.aiEstimate)
        assertTrue(c.openable)
    }

    @Test
    fun `null e vazio nao geram estimativa`() {
        val c = TrendResponseParser.parse(
            """{"results":[{"title":"T","views":null,"engagement":"null","sourceUrl":"","openable":true}]}"""
        ).single()
        assertNull(c.aiEstimate)
        assertFalse(c.openable)
    }

    @Test
    fun `sem lista retorna vazio`() {
        assertTrue(TrendResponseParser.parse("{}").isEmpty())
    }

    @Test
    fun `invariantes de TrendCard`() {
        fun card(origin: TrendOrigin, views: String? = null, verified: Boolean = false, est: String? = null) =
            TrendCard("t", "p", "r", views, null, "", false, origin = origin, metricsVerified = verified, aiEstimate = est)
        assertEquals("1M", card(TrendOrigin.OFFICIAL_API, views = "1M", verified = true).views)
        listOf(
            { card(TrendOrigin.AI_INFERENCE, views = "1M") },
            { card(TrendOrigin.LINK_ONLY, views = "1M") },
            { card(TrendOrigin.LINK_ONLY, verified = true) },
            { card(TrendOrigin.LINK_ONLY, est = "x") },
            { card(TrendOrigin.OFFICIAL_API, est = "x") }
        ).forEach { build ->
            try {
                build()
                fail("esperava IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `capability mapeia para origin`() {
        ProviderCapability.entries.forEach { assertEquals(it.name, it.toOrigin().name) }
    }
}
