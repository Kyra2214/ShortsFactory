package com.shortsfactory.trends

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleTrendsSourceTest {
    private val items = listOf(
        GoogleTrendsSource.Item("Copa do Brasil", "200K+", listOf("Final movimenta torcedores")),
        GoogleTrendsSource.Item("Basquete nacional", null, listOf("Destaque da Copa")),
        GoogleTrendsSource.Item("Cartoon viral", null, emptyList())
    )

    @Test
    fun `nicho vazio preserva lista geral`() {
        assertEquals(items, GoogleTrendsSource.filterByNiche(items, "  "))
    }

    @Test
    fun `filtro ignora maiusculas e procura titulo ou noticias`() {
        assertEquals(
            listOf(items[0], items[1]),
            GoogleTrendsSource.filterByNiche(items, "copa")
        )
    }

    @Test
    fun `filtro exige palavra inteira`() {
        assertEquals(
            emptyList<GoogleTrendsSource.Item>(),
            GoogleTrendsSource.filterByNiche(items, "cart")
        )
    }
}
