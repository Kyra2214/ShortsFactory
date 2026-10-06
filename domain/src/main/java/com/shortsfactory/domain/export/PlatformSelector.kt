package com.shortsfactory.domain.export

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.model.ExportPlatform
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Plataforma sugerida pela IA para um corte, com justificativa curta (obrigatória). */
data class PlatformSuggestion(val platform: ExportPlatform, val reason: String)

data class PlatformSuggestionRequest(
    val clipTitle: String,
    val hook: String,
    val topic: String,
    val description: String,
    val clipDurationSec: Double,
    val candidates: List<PlatformProfile>
)

/** Saída da IA é entrada não confiável: só plataformas pedidas, sem repetição, com justificativa e que comportam a duração. */
object PlatformSuggestionParser {
    const val MAX_REASON_CHARS = 160
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String, request: PlatformSuggestionRequest): List<PlatformSuggestion> {
        val start = jsonText.indexOf('{')
        val end = jsonText.lastIndexOf('}')
        require(start in 0 until end) { "Resposta sem JSON." }
        val root = json.parseToJsonElement(jsonText.substring(start, end + 1)).jsonObject
        val items = (root["platforms"] ?: root["items"])?.jsonArray ?: error("Resposta sem lista de plataformas.")
        return items.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                val key = obj["platform"]?.jsonPrimitive?.content.orEmpty()
                val profile = request.candidates.firstOrNull { it.platform.key == key } ?: return@runCatching null
                if (request.clipDurationSec > profile.maxDurationSec) return@runCatching null
                val reason = obj["reason"]?.jsonPrimitive?.content.orEmpty().trim().replace(Regex("\\s+"), " ")
                if (reason.isEmpty()) return@runCatching null
                PlatformSuggestion(profile.platform, if (reason.length > MAX_REASON_CHARS) reason.take(MAX_REASON_CHARS).trimEnd() else reason)
            }.getOrNull()
        }.distinctBy { it.platform }
    }
}

/**
 * Caso de uso: a IA (via roteador) escolhe plataformas por corte e justifica. Não decide parâmetros de
 * codificação (isso é do perfil). Sem IA ou sem sugestão válida lança [AiException]; nada é inventado.
 */
class PlatformSelector(private val ai: AIProvider) {
    suspend fun suggest(request: PlatformSuggestionRequest): List<PlatformSuggestion> {
        require(request.candidates.isNotEmpty()) { "Nenhuma plataforma candidata." }
        val reply = ai.generateText(buildPrompt(request))
        val parsed = try {
            PlatformSuggestionParser.parse(reply, request)
        } catch (e: IllegalArgumentException) {
            throw AiException("Resposta de sugestão inválida: ${e.message}", cause = e)
        } catch (e: IllegalStateException) {
            throw AiException("Resposta de sugestão inválida: ${e.message}", cause = e)
        } catch (e: SerializationException) {
            throw AiException("Resposta de sugestão inválida.", cause = e)
        }
        if (parsed.isEmpty()) throw AiException("A IA não devolveu sugestões válidas de plataforma.")
        return parsed
    }

    internal fun buildPrompt(request: PlatformSuggestionRequest): String {
        val platforms = request.candidates.joinToString("\n") {
            "- ${it.platform.key} (${it.platform.label}): duração máxima ${it.maxDurationSec}s"
        }
        return """Escolha em quais plataformas de vídeo curto vertical este corte rende melhor e justifique cada escolha em uma frase curta, em português do Brasil.
Título do corte: ${request.clipTitle}
Gancho: ${request.hook}
Tema: ${request.topic}
Descrição: ${request.description}
Duração do corte: ${"%.0f".format(java.util.Locale.ROOT, request.clipDurationSec)}s
Plataformas candidatas:
$platforms
Use só o que está acima; não invente métricas nem dados de audiência. Não sugira plataforma cuja duração máxima seja menor que a do corte. Não escolha parâmetros de codificação.
Responda SOMENTE com JSON: {"platforms":[{"platform":"<chave>","reason":"<justificativa curta>"}]}"""
    }
}
