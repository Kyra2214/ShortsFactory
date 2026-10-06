package com.shortsfactory.domain.ai.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Modelo devolvido pela API de listagem; [free] é `null` quando o provedor não informa preço. */
data class DiscoveredModel(val id: String, val free: Boolean?)

/**
 * Descoberta dinâmica: lê a lista real de modelos do provedor e escolhe os candidatos de chat gratuitos,
 * sem lista de permissão fixa nem números de limite.
 */
object FreeModelDiscovery {

    const val MAX_MODELS = 5

    private val NON_TEXT = listOf(
        "embed", "rerank", "whisper", "tts", "audio", "speech", "transcribe", "moderation", "guard",
        "image", "imagen", "video", "dall", "flux", "diffusion", "sdxl", "ocr", "realtime"
    )
    private val LIGHT_HINTS = listOf("free", "flash", "lite", "mini", "small", "instant", "nano", "air")

    /** Aceita `{"data":[...]}` ou `{"models":[...]}`; formato desconhecido ou inválido resulta em lista vazia. */
    fun parse(json: String): List<DiscoveredModel> {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyList()
        val array = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id")?.removePrefix("models/")?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            DiscoveredModel(id, isFree(id, obj["pricing"] as? JsonObject))
        }.distinctBy { it.id }
    }

    /**
     * Candidatos em ordem de tentativa. Com informação de preço, só entram modelos gratuitos; sem ela,
     * entram modelos de chat com nome de porte leve primeiro. Limitado a [MAX_MODELS].
     */
    fun select(discovered: List<DiscoveredModel>): List<String> {
        val chat = discovered.filter { m -> NON_TEXT.none { m.id.lowercase().contains(it) } }
        val priced = chat.filter { it.free != null }
        val pool = if (priced.isNotEmpty()) priced.filter { it.free == true } else chat
        return pool
            .sortedBy { m -> if (LIGHT_HINTS.any { m.id.lowercase().contains(it) }) 0 else 1 }
            .map { it.id }
            .take(MAX_MODELS)
    }

    private fun isFree(id: String, pricing: JsonObject?): Boolean? {
        if (pricing == null) return if (id.endsWith(":free")) true else null
        val prompt = pricing.str("prompt")?.toDoubleOrNull()
        val completion = pricing.str("completion")?.toDoubleOrNull()
        if (prompt == null || completion == null) return if (id.endsWith(":free")) true else null
        return prompt == 0.0 && completion == 0.0
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
}
