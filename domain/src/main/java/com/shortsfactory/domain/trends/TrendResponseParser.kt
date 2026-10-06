package com.shortsfactory.domain.trends

import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendOrigin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Converte a resposta JSON de uma IA em cards `AI_INFERENCE`; nunca produz métricas oficiais. */
object TrendResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): List<TrendCard> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        return (root["trends"] ?: root["results"])?.jsonArray?.map { element ->
            val obj = element.jsonObject
            val sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.content ?: ""
            val openable = obj["openable"]?.jsonPrimitive?.content == "true" && sourceUrl.startsWith("http")
            fun field(name: String): String? =
                obj[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
            val estimate = listOfNotNull(
                field("views")?.let { "visualizações: $it" },
                field("engagement")?.let { "engajamento: $it" }
            ).joinToString(", ").ifEmpty { null }
            TrendCard(
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                platform = obj["platform"]?.jsonPrimitive?.content ?: "",
                region = obj["region"]?.jsonPrimitive?.content ?: "",
                views = null,
                engagement = null,
                sourceUrl = sourceUrl,
                openable = openable,
                origin = TrendOrigin.AI_INFERENCE,
                aiEstimate = estimate
            )
        } ?: emptyList()
    }
}
