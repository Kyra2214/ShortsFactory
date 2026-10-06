package com.shortsfactory.domain.export

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.model.ExportPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Textos de publicação de um corte para uma plataforma. */
data class PlatformMetadata(
    val platform: ExportPlatform,
    val title: String,
    val description: String,
    /** Hashtags já normalizadas, cada uma começando com `#`. */
    val hashtags: List<String>
)

/** Dados do corte usados no pedido; nada além do que já existe no projeto. */
data class PlatformMetadataRequest(
    val clipTitle: String,
    val hook: String,
    val topic: String,
    val description: String,
    val platforms: List<PlatformProfile>
)

/** Validação e limite de tamanho da saída da IA (entrada não confiável). */
object PlatformMetadataValidator {
    private val hashtagBody = Regex("[\\p{L}\\p{N}_]+")

    /** Aplica os limites do perfil; devolve `null` quando não sobra descrição utilizável. */
    fun sanitize(platform: PlatformProfile, title: String, description: String, hashtags: List<String>): PlatformMetadata? {
        val limits = platform.textLimits
        val cleanTitle = if (limits.titleMaxChars == 0) "" else clip(title.trim().replace(Regex("\\s+"), " "), limits.titleMaxChars)
        val tags = hashtags.mapNotNull { raw ->
            val body = raw.trim().trimStart('#').replace(Regex("\\s+"), "")
            if (body.isNotEmpty() && hashtagBody.matches(body)) "#$body" else null
        }.distinctBy { it.lowercase() }.take(limits.maxHashtags)
        val tagsLength = if (tags.isEmpty()) 0 else tags.sumOf { it.length + 1 } + 1
        val room = limits.descriptionMaxChars - tagsLength
        val cleanDescription = clip(description.trim(), room.coerceAtLeast(0))
        if (cleanDescription.isEmpty() && cleanTitle.isEmpty()) return null
        return PlatformMetadata(platform.platform, cleanTitle, cleanDescription, tags)
    }

    /** Sem IA: só título e gancho do corte (nada inventado), dentro dos limites do perfil. */
    fun fallback(platform: PlatformProfile, clipTitle: String, hook: String): PlatformMetadata? =
        if (platform.textLimits.titleMaxChars == 0) {
            sanitize(platform, "", listOf(clipTitle, hook).filter { it.isNotBlank() }.joinToString("\n"), emptyList())
        } else {
            sanitize(platform, clipTitle, hook, emptyList())
        }

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).trimEnd()
}

/** Pedido/resposta do gerador, sem depender de tela ou Android (reutilizável pelo fluxo automático). */
object PlatformMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Aceita `{"platforms":[{"platform":"yt","title":..,"description":..,"hashtags":[..]}]}`; itens inválidos são ignorados. */
    fun parse(jsonText: String, requested: List<PlatformProfile>): List<PlatformMetadata> {
        val start = jsonText.indexOf('{')
        val end = jsonText.lastIndexOf('}')
        require(start in 0 until end) { "Resposta sem JSON." }
        val root: JsonObject = json.parseToJsonElement(jsonText.substring(start, end + 1)).jsonObject
        val items: JsonArray = (root["platforms"] ?: root["items"])?.jsonArray ?: error("Resposta sem lista de plataformas.")
        return items.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                val key = obj["platform"]?.jsonPrimitive?.content.orEmpty()
                val profile = requested.firstOrNull { it.platform.key == key } ?: return@runCatching null
                PlatformMetadataValidator.sanitize(
                    profile,
                    obj["title"]?.jsonPrimitive?.content.orEmpty(),
                    obj["description"]?.jsonPrimitive?.content.orEmpty(),
                    obj["hashtags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                )
            }.getOrNull()
        }.distinctBy { it.platform }
    }
}

/**
 * Caso de uso: pede à IA (via roteador, `AIProvider.generateText`) título, descrição e hashtags por plataforma.
 * Sem texto inventado: se a IA falhar ou nada for válido, lança [AiException]; quem chama decide o fallback.
 */
class PlatformMetadataGenerator(private val ai: AIProvider) {
    suspend fun generate(request: PlatformMetadataRequest): List<PlatformMetadata> {
        require(request.platforms.isNotEmpty()) { "Nenhuma plataforma informada." }
        val reply = ai.generateText(buildPrompt(request))
        val parsed = try {
            PlatformMetadataParser.parse(reply, request.platforms)
        } catch (e: IllegalArgumentException) {
            throw AiException("Resposta de metadados inválida: ${e.message}", cause = e)
        } catch (e: IllegalStateException) {
            throw AiException("Resposta de metadados inválida: ${e.message}", cause = e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw AiException("Resposta de metadados inválida.", cause = e)
        }
        if (parsed.isEmpty()) throw AiException("A IA não devolveu metadados válidos para as plataformas pedidas.")
        return parsed
    }

    internal fun buildPrompt(request: PlatformMetadataRequest): String {
        val platforms = request.platforms.joinToString("\n") { p ->
            val l = p.textLimits
            "- ${p.platform.key} (${p.platform.label}): " +
                (if (l.titleMaxChars > 0) "título até ${l.titleMaxChars} caracteres; " else "sem título separado; ") +
                "descrição até ${l.descriptionMaxChars} caracteres; até ${l.maxHashtags} hashtags"
        }
        return """Prepare textos de publicação em português do Brasil para um vídeo curto vertical.
Título do corte: ${request.clipTitle}
Gancho: ${request.hook}
Tema: ${request.topic}
Descrição atual: ${request.description}
Plataformas e limites:
$platforms
Use apenas o que está acima; não invente fatos, números nem métricas. Respeite os limites de cada plataforma.
Responda SOMENTE com JSON: {"platforms":[{"platform":"<chave>","title":"","description":"","hashtags":["#exemplo"]}]}"""
    }
}
