package com.shortsfactory.ai

import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.pipeline.GenerationSummaryHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal data class ChatProviderConfig(
    val displayName: String,
    val chatEndpoint: String,
    val modelsEndpoint: String?,
    val defaultModels: List<String>,
    val modelFilter: (String) -> Boolean
) {
    companion object {
        fun xAi() = ChatProviderConfig(
            displayName = "IA xAI/Grok",
            chatEndpoint = "https://api.x.ai/v1/chat/completions",
            modelsEndpoint = "https://api.x.ai/v1/models",
            defaultModels = listOf("grok-4.6", "grok-4-fast", "grok-3-mini"),
            modelFilter = { model ->
                val normalized = model.lowercase()
                normalized.startsWith("grok") &&
                    !normalized.contains("imagine") &&
                    !normalized.contains("video") &&
                    !normalized.contains("image") &&
                    !normalized.contains("voice")
            }
        )

        fun openAi() = ChatProviderConfig(
            displayName = "OpenAI",
            chatEndpoint = "https://api.openai.com/v1/chat/completions",
            modelsEndpoint = "https://api.openai.com/v1/models",
            defaultModels = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o"),
            modelFilter = { model ->
                val normalized = model.lowercase()
                normalized.startsWith("gpt-") &&
                    !normalized.contains("audio") &&
                    !normalized.contains("image") &&
                    !normalized.contains("realtime") &&
                    !normalized.contains("transcribe")
            }
        )
    }
}

/**
 * Provedor de chat com descoberta de modelos e fallback entre chaves.
 *
 * A API de modelos é consultada para priorizar os modelos de texto liberados para cada chave.
 * Quando uma chave ou modelo falha, a próxima combinação é tentada automaticamente.
 */
internal open class GrokProvider(
    private val keyStore: SecureKeyStore,
    private val config: ChatProviderConfig = ChatProviderConfig.xAi(),
    private val keyProvider: () -> List<String> = { keyStore.getApiKeys() }
) : AIProvider {

    override val providerName: String = config.displayName

    @Volatile private var overrideKeys: List<String>? = null

    @Volatile var lastSuccessfulModel: String? = null
        private set

    @Volatile var lastSuccessfulKeyIndex: Int? = null
        private set

    /** Chave temporária usada apenas para testar conexão antes de salvar. */
    fun overrideKey(key: String) {
        overrideKeys(listOf(key))
    }

    /** Chaves temporárias usadas apenas para testar conexão antes de salvar. */
    fun overrideKeys(keys: List<String>) {
        overrideKeys = keys.map(String::trim).filter(String::isNotEmpty).distinct()
    }

    /** Persiste uma chave validada. */
    fun persistApiKey(key: String) {
        persistApiKeys(listOf(key))
    }

    /** Persiste as chaves validadas na ordem de fallback definida pelo usuário. */
    fun persistApiKeys(keys: List<String>) {
        keyStore.saveApiKeys(keys)
    }

    override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult {
        val prompt = buildPrompt(transcript, hint)
        return withFallback(prompt, ::parseAnalysis)
    }

    override suspend fun searchTrends(
        query: String,
        region: String,
        platform: String,
        niche: String
    ): List<TrendCard> {
        val prompt = buildTrendPrompt(query, region, platform, niche)
        return withFallback(prompt, ::parseTrends)
    }

    override suspend fun analyzeTrends(query: String, region: String): String {
        val prompt = buildTrendAnalysisPrompt(query, region)
        return withFallback(prompt, ::extractMessageContent)
    }

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String {
        val prompt = buildBriefPrompt(trendTitle, platform, region)
        return withFallback(prompt, ::extractMessageContent)
    }

    /**
     * Executa a mesma operação em cada combinação chave/modelo até obter uma resposta válida.
     * Erros 401/403/404/429/5xx e falhas de parsing não interrompem o fallback.
     */
    private suspend fun <T> withFallback(prompt: String, transform: (String) -> T): T {
        val configuredKeys = apiKeys()
        require(configuredKeys.isNotEmpty()) {
            "Nenhuma chave configurada para ${config.displayName}. Configure em Configurações → IA e chaves."
        }

        var lastError: Throwable? = null
        configuredKeys.forEachIndexed { keyIndex, apiKey ->
            val discoveredModels = runCatching { listAvailableTextModels(apiKey) }
                .getOrDefault(emptyList())
            val models = (discoveredModels + config.defaultModels).distinct()

            models.forEach { model ->
                try {
                    val reply = callGrok(apiKey, model, prompt)
                    val result = transform(reply)
                    lastSuccessfulModel = model
                    lastSuccessfulKeyIndex = keyIndex
                    return result
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    lastError = error
                }
            }
        }

        throw IllegalStateException(
            "Nenhuma chave/modelo disponível para ${config.displayName} no momento" +
                (lastError?.message?.let { ": ${it.take(180)}" } ?: ".")
        )
    }

    private fun apiKeys(): List<String> = overrideKeys ?: keyProvider()

    private fun listAvailableTextModels(apiKey: String): List<String> {
        val endpoint = config.modelsEndpoint ?: return emptyList()
        return withHttpConnection(
        method = "GET",
        endpoint = endpoint,
        apiKey = apiKey
    ) { connection ->
        val response = readResponse(connection)
        val root = Json.parseToJsonElement(response).jsonObject
        root["data"]?.jsonArray?.mapNotNull { element ->
            element.jsonObject["id"]?.jsonPrimitive?.content
        }?.filter(config.modelFilter) ?: emptyList()
        }
    }

    private fun buildPrompt(transcript: Transcript, hint: GenerationSummaryHint): String {
        val segmentsText = transcript.segments.joinToString("\n") { "[${it.startMs}-${it.endMs}] ${it.text}" }
        return """Analise a transcrição de um vídeo e identifique os melhores trechos para virar Shorts verticais.
Preset de duração: ${hint.preset}.
Retorne JSON com campos: title (título do vídeo), summary (resumo), suggestedDurationMs (duração sugerida ou null),
e candidates (lista de até 12, cada um com: score 0-100, startMs, endMs, title, hook, topic, reason,
e subtitles com trechos de até 5 palavras por segmento no intervalo).
Se a IA não tiver dados reais de análise, estime com base no texto da transcrição.

Transcrição:
$segmentsText"""
    }

    private fun buildTrendPrompt(query: String, region: String, platform: String, niche: String): String =
        """Com base no conhecimento público sobre tendências atuais, liste até 8 tendências de vídeos curtos para:
região=$region, plataforma=$platform, nicho=$niche, busca="${query.ifEmpty { niche }}".
Para cada tendência retorne (em JSON, campos: title, platform, region, views ou null, engagement ou null,
sourceUrl, openable booleano): título do tema em alta, plataforma principal, região, estimativa honesta de
visualizações (ou null se desconhecida), nível de engajamento descritivo (ou null), URL de busca na plataforma
(que pode ser aberta publicamente), e openable=true quando for uma URL de busca pública.
Não invente métricas: quando não souber, retorne null. Inclua apenas conteúdo acessível publicamente."""

    private fun buildTrendAnalysisPrompt(query: String, region: String): String =
        """Com base em conhecimento público sobre tendências atuais de vídeos curtos, analise o mercado
para região=$region e tema="$query".
Trate isso como análise de tendências, não como garantia de viralização.
Liste em texto claro (Markdown): assuntos em crescimento, formatos que aparecem com frequência,
duração típica dos vídeos, estilos de abertura, temas recorrentes, palavras-chave e hashtags recorrentes.
Seja honesto: quando não souber, diga que não há dados públicos suficientes."""

    private fun buildBriefPrompt(trendTitle: String, platform: String, region: String): String =
        """Uma tendência de vídeo curto foi identificada: "$trendTitle" (plataforma=$platform, região=$region).
Em vez de copiar esse conteúdo, produza um briefing para um CONTEÚDO ORIGINAL inspirado na tendência.
Retorne em Markdown:
- Tendência identificada (resumo curto)
- Estrutura observada (ex.: 1. Gancho rápido, 2. Demonstração, 3. Resultado, 4. Conclusão)
- Sugestão de conteúdo original (roteiro com gancho, desenvolvimento e CTA em português do Brasil)
- Variações de ângulo (2 a 3 formas diferentes de abordar o mesmo tema)
Não invente métricas. O objetivo é inspirar criação original, nunca reproduzir conteúdo de terceiros."""

    private suspend fun callGrok(apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            withHttpConnection(
                method = "POST",
                endpoint = config.chatEndpoint,
                apiKey = apiKey
            ) { connection ->
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val body = """{"model":"${escapeJson(model).trim('"')}","messages":[{"role":"user","content":${escapeJson(prompt)}}]}"""
                OutputStreamWriter(connection.outputStream).use { it.write(body) }
                readResponse(connection)
            }
        }

    private fun <T> withHttpConnection(
        method: String,
        endpoint: String,
        apiKey: String,
        block: (HttpURLConnection) -> T
    ): T {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (statusCode !in 200..299) {
            throw RuntimeException("xAI retornou erro $statusCode: ${response.take(200)}")
        }
        return response
    }

    private fun parseAnalysis(reply: String): AIAnalysisResult {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(extractJson(extractMessageContent(reply))).jsonObject
        val candidates = root["candidates"]?.jsonArray?.map { element ->
            val obj = element.jsonObject
            ShortCandidate(
                score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                startMs = obj["startMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                endMs = obj["endMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                hook = obj["hook"]?.jsonPrimitive?.content ?: "",
                topic = obj["topic"]?.jsonPrimitive?.content ?: "",
                reason = obj["reason"]?.jsonPrimitive?.content ?: "",
                subtitles = obj["subtitles"]?.jsonArray?.mapNotNull { seg ->
                    val so = seg.jsonObject
                    SubtitleSegment(
                        startMs = so["startMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        endMs = so["endMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        words = so["words"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    )
                } ?: emptyList()
            )
        } ?: emptyList()
        return AIAnalysisResult(
            title = root["title"]?.jsonPrimitive?.content ?: "Vídeo analisado",
            summary = root["summary"]?.jsonPrimitive?.content ?: "",
            candidates = candidates,
            suggestedDurationMs = root["suggestedDurationMs"]?.jsonPrimitive?.content?.toLongOrNull()
        )
    }

    private fun parseTrends(reply: String): List<TrendCard> {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(extractJson(extractMessageContent(reply))).jsonObject
        return (root["trends"] ?: root["results"])?.jsonArray?.map { element ->
            val obj = element.jsonObject
            TrendCard(
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                platform = obj["platform"]?.jsonPrimitive?.content ?: "",
                region = obj["region"]?.jsonPrimitive?.content ?: "",
                views = obj["views"]?.jsonPrimitive?.takeIf { it.content != "null" }?.content,
                engagement = obj["engagement"]?.jsonPrimitive?.takeIf { it.content != "null" }?.content,
                sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.content ?: "",
                openable = obj["openable"]?.jsonPrimitive?.content == "true"
            )
        } ?: emptyList()
    }

    private fun extractMessageContent(raw: String): String {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return raw
        return root["choices"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?: raw
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else "{}"
    }

    private fun escapeJson(value: String): String =
        kotlinx.serialization.json.JsonPrimitive(value).toString()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 30_000
    }
}

/** OpenAI usa a mesma camada de chat, mas lê a chave já cadastrada para transcrição. */
internal class OpenAiProvider(keyStore: SecureKeyStore) : GrokProvider(
    keyStore = keyStore,
    config = ChatProviderConfig.openAi(),
    keyProvider = { listOfNotNull(keyStore.getTranscriptionApiKey()) }
)
