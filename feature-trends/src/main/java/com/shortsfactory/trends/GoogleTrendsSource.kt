package com.shortsfactory.trends

import android.util.Xml
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendOrigin
import com.shortsfactory.domain.model.TrendRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.xmlpull.v1.XmlPullParser

/**
 * Busca as pesquisas em alta do Google Trends (RSS público, sem chave) por país.
 * Devolve lista vazia quando o país não é suportado, a rede falha ou nada combina com o nicho;
 * quem chama então recorre às IAs.
 */
internal object GoogleTrendsSource {
    /** Países com feed no Google Trends. China e Global não têm. */
    private val GEO = mapOf("br" to "BR", "us" to "US", "jp" to "JP", "kr" to "KR")

    private const val TIMEOUT_MS = 8_000
    private const val MAX_CARDS = 15

    internal data class Item(val title: String, val traffic: String?, val newsTitles: List<String>)

    suspend fun fetch(region: TrendRegion, niche: String): List<TrendCard> =
        withContext(Dispatchers.IO) {
            val geo = GEO[region.key] ?: return@withContext emptyList()
            val items = parse(download("https://trends.google.com/trending/rss?geo=$geo"))
            filterByNiche(items, niche).take(MAX_CARDS).map { item ->
                TrendCard(
                    title = item.traffic?.let { "${item.title} · $it buscas" } ?: item.title,
                    platform = "Google Trends",
                    region = region.label,
                    views = null,
                    engagement = null,
                    sourceUrl = "https://www.google.com/search?q=" + URLEncoder.encode(item.title, "UTF-8"),
                    openable = true,
                    origin = TrendOrigin.LINK_ONLY
                )
            }
        }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ShortsFactory")
            if (connection.responseCode != 200) error("Google Trends respondeu HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(xml: String): List<Item> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val items = mutableListOf<Item>()
        var inItem = false
        var title = ""
        var traffic: String? = null
        var news = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                // XmlPullParser pode expor a tag namespaceada como `ht:approx_traffic`
                // ou somente como `approx_traffic`, dependendo da configuração do parser.
                when (parser.name.substringAfter(':')) {
                    "item" -> { inItem = true; title = ""; traffic = null; news = mutableListOf() }
                    "title" -> if (inItem) title = parser.nextText().trim()
                    "approx_traffic" -> if (inItem) traffic = parser.nextText().trim().ifEmpty { null }
                    "news_item_title" -> if (inItem) news.add(parser.nextText().trim())
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "item") {
                if (title.isNotEmpty()) items.add(Item(title, traffic, news))
                inItem = false
            }
            event = parser.next()
        }
        return items
    }

    /** Sem nicho: lista geral. Com nicho: só itens cujo título/notícias contêm a palavra inteira. */
    internal fun filterByNiche(items: List<Item>, niche: String): List<Item> {
        val words = niche.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        val patterns = words.map {
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        }
        return items.filter { item ->
            val text = (listOf(item.title) + item.newsTitles).joinToString(" ")
            patterns.any { it.containsMatchIn(text) }
        }
    }
}
