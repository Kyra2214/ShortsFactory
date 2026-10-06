package com.shortsfactory.domain.ai.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.URI

enum class ApiAccess { FREE_TIER, FREE_PERMANENT }

enum class ApiRegion { GLOBAL, CHINA, EUROPE, OTHER }

/** Modelo de partida de um provedor; a lista real vem da descoberta em tempo de execução. */
data class ApiModelSeed(
    val id: String,
    val name: String,
    val capabilities: List<String>,
    val access: ApiAccess,
    val endpoint: String,
    val requiresKey: Boolean
)

data class ApiProviderEntry(
    val id: String,
    val name: String,
    val region: ApiRegion,
    val officialUrl: String,
    val documentationUrl: String,
    private val declaredModelsEndpoint: String?,
    val models: List<ApiModelSeed>
) {
    /** Base OpenAI-compatível do provedor (a mesma em todos os modelos de partida). */
    val chatBaseUrl: String get() = models.first().endpoint.trimEnd('/')

    /** Endpoint de listagem de modelos: o declarado ou `<base>/models`. */
    val modelsEndpoint: String get() = declaredModelsEndpoint ?: "$chatBaseUrl/models"

    val chatCompletionsUrl: String get() = "$chatBaseUrl/chat/completions"

    val requiresKey: Boolean get() = models.any { it.requiresKey }
}

data class ApiCatalog(
    val version: String,
    val providers: List<ApiProviderEntry>,
    /** Entradas descartadas (motivo legível), para diagnóstico; nunca derrubam o catálogo. */
    val skipped: List<String> = emptyList()
) {
    fun provider(id: String): ApiProviderEntry? = providers.firstOrNull { it.id == id }
}

/**
 * Lê `ai_api_catalog.json`. Só entram modelos gratuitos (`FREE_TIER`/`FREE_PERMANENT`), URLs HTTPS e IDs únicos.
 * Os IDs de modelo do arquivo são só ponto de partida: não formam lista de permissão.
 */
object ApiCatalogParser {

    fun parse(json: String): ApiCatalog {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Catálogo de APIs inválido.", e)
        }
        val version = root.str("version") ?: "desconhecida"
        val skipped = mutableListOf<String>()
        val providers = mutableListOf<ApiProviderEntry>()
        val seen = mutableSetOf<String>()

        val array = root["providers"] as? JsonArray
            ?: throw IllegalArgumentException("Catálogo de APIs sem a lista de provedores.")
        array.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                skipped += "provedor #$index: formato inválido"
                return@forEachIndexed
            }
            val id = obj.str("id")?.trim().orEmpty()
            val label = id.ifBlank { "#$index" }
            val entry = parseProvider(obj, id, skipped, label)
            when {
                entry == null -> Unit
                !seen.add(entry.id) -> skipped += "provedor ${entry.id}: id duplicado"
                else -> providers += entry
            }
        }
        return ApiCatalog(version, providers, skipped)
    }

    private fun parseProvider(obj: JsonObject, id: String, skipped: MutableList<String>, label: String): ApiProviderEntry? {
        val name = obj.str("name")?.trim().orEmpty()
        val official = obj.str("officialUrl")
        val docs = obj.str("documentationUrl")
        val declared = obj.str("modelsEndpoint")
        if (id.isBlank() || name.isBlank()) {
            skipped += "provedor $label: id ou nome ausente"
            return null
        }
        if (!isHttps(official) || !isHttps(docs)) {
            skipped += "provedor $id: URL oficial/documentação não é HTTPS"
            return null
        }
        if (declared != null && !isHttps(declared)) {
            skipped += "provedor $id: endpoint de modelos não é HTTPS"
            return null
        }
        val models = (obj["models"] as? JsonArray).orEmpty().mapNotNull { parseModel(it, id, skipped) }
        if (models.isEmpty()) {
            skipped += "provedor $id: nenhum modelo gratuito válido"
            return null
        }
        if (models.map { it.endpoint.trimEnd('/') }.distinct().size != 1) {
            skipped += "provedor $id: modelos com endpoints diferentes"
            return null
        }
        return ApiProviderEntry(
            id = id,
            name = name,
            region = obj.str("region")?.let { r -> ApiRegion.entries.firstOrNull { it.name == r } } ?: ApiRegion.OTHER,
            officialUrl = official!!,
            documentationUrl = docs!!,
            declaredModelsEndpoint = declared,
            models = models
        )
    }

    private fun parseModel(element: kotlinx.serialization.json.JsonElement, providerId: String, skipped: MutableList<String>): ApiModelSeed? {
        val obj = element as? JsonObject ?: return null
        val modelId = obj.str("id")?.trim().orEmpty()
        val access = obj.str("access")?.let { a -> ApiAccess.entries.firstOrNull { it.name == a } }
        val endpoint = obj.str("endpoint")
        if (modelId.isBlank()) return null
        if (access == null) {
            skipped += "modelo $providerId/$modelId: acesso não gratuito"
            return null
        }
        if (!isHttps(endpoint)) {
            skipped += "modelo $providerId/$modelId: endpoint não é HTTPS"
            return null
        }
        return ApiModelSeed(
            id = modelId,
            name = obj.str("name")?.trim()?.ifBlank { null } ?: modelId,
            capabilities = (obj["capabilities"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
            access = access,
            endpoint = endpoint!!,
            requiresKey = obj["requiresKey"]?.jsonPrimitive?.booleanOrNull ?: true
        )
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun isHttps(url: String?): Boolean = url != null && runCatching {
        URI(url).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
    }.getOrDefault(false)
}
