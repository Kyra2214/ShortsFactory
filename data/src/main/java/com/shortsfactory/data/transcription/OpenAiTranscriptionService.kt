package com.shortsfactory.data.transcription

import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import com.shortsfactory.domain.pipeline.TranscriptionService
import com.shortsfactory.domain.pipeline.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OpenAiTranscriptionService @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
    private val videoEngine: VideoEngine,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
) : TranscriptionService {

    override suspend fun transcribe(audioPath: String): Transcript = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStore.getTranscriptionApiKey()?.trim().orEmpty()
        check(apiKey.isNotEmpty()) {
            "Configure a chave de transcrição nas configurações de IA antes de analisar o vídeo."
        }

        val audioFile = File(audioPath)
        check(audioFile.isFile && audioFile.length() > 0L) {
            "O áudio temporário não está disponível para transcrição."
        }

        if (audioFile.length() <= MAX_FILE_BYTES) {
            val direct = requestChunk(apiKey, audioFile, 0L)
            return@withContext if (direct.segments.isNotEmpty()) {
                Transcript(direct.segments)
            } else {
                fallbackTranscript(audioPath, direct.text)
            }
        }

        val parentDir = audioFile.parentFile ?: error("Diretório temporário de áudio não encontrado.")
        val chunkDir = File(parentDir, "transcription_chunks")
        try {
            val chunks = splitAudioWithinLimit(audioPath, chunkDir)
            check(chunks.isNotEmpty()) { "Não foi possível dividir o áudio para transcrição." }

            val allSegments = mutableListOf<TranscriptSegment>()
            val allText = mutableListOf<String>()
            var offsetMs = 0L
            chunks.forEach { path ->
                val chunk = requestChunk(apiKey, File(path), offsetMs)
                allSegments += chunk.segments
                if (chunk.text.isNotBlank()) allText += chunk.text.trim()
                val actualDuration = runCatching { videoEngine.probe(path).durationMs }
                    .getOrDefault(0L)
                offsetMs += actualDuration.takeIf { it > 0L } ?: CHUNK_DURATION_MS
            }
            if (allSegments.isNotEmpty()) {
                Transcript(allSegments)
            } else {
                fallbackTranscript(audioPath, allText.joinToString(" "))
            }
        } finally {
            chunkDir.listFiles()?.forEach(File::delete)
            chunkDir.delete()
        }
    }

    private suspend fun splitAudioWithinLimit(audioPath: String, chunkDir: File): List<String> {
        var durationMs = CHUNK_DURATION_MS
        while (true) {
            val chunks = videoEngine.splitAudio(audioPath, chunkDir.absolutePath, durationMs)
            val valid = chunks.isNotEmpty() && chunks.all { path ->
                val file = File(path)
                file.isFile && file.length() in 1L..MAX_FILE_BYTES
            }
            if (valid) return chunks
            chunks.forEach { File(it).delete() }
            if (durationMs <= MIN_CHUNK_DURATION_MS) {
                error("Não foi possível gerar fragmentos de áudio abaixo de 25 MB.")
            }
            durationMs = (durationMs / 2L).coerceAtLeast(MIN_CHUNK_DURATION_MS)
        }
    }

    private fun requestChunk(apiKey: String, audioFile: File, offsetMs: Long): TranscriptChunk {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(TRANSCRIPTIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Serviço de transcrição indisponível (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val parsed = OpenAiTranscriptionParser.parse(json, body)
            return TranscriptChunk(
                text = parsed.text,
                segments = parsed.segments.mapNotNull { segment ->
                    val startMs = ((segment.start ?: 0.0) * 1_000).toLong().coerceAtLeast(0L) + offsetMs
                    val endMs = ((segment.end ?: 0.0) * 1_000).toLong() + offsetMs
                    val text = segment.text.trim()
                    if (endMs <= startMs || text.isEmpty()) null else TranscriptSegment(startMs, endMs, text)
                }
            )
        }
    }

    private fun fallbackTranscript(audioPath: String, text: String): Transcript {
        val normalized = text.trim()
        if (normalized.isEmpty()) return Transcript(emptyList())
        val durationMs = videoEngine.probe(audioPath).durationMs.coerceAtLeast(1_000L)
        return Transcript(listOf(TranscriptSegment(0L, durationMs, normalized)))
    }

    private data class TranscriptChunk(
        val text: String,
        val segments: List<TranscriptSegment>
    )

    companion object {
        private const val TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL = "whisper-1"
        private const val MAX_FILE_BYTES = 25L * 1024L * 1024L
        private const val CHUNK_DURATION_MS = 10L * 60L * 1_000L
        private const val MIN_CHUNK_DURATION_MS = 60L * 1_000L
    }
}

@Serializable
data class OpenAiTranscriptionResponse(
    val text: String = "",
    val segments: List<OpenAiTranscriptionSegment> = emptyList()
)

@Serializable
data class OpenAiTranscriptionSegment(
    val start: Double? = null,
    val end: Double? = null,
    val text: String = ""
)

object OpenAiTranscriptionParser {
    fun parse(json: Json, body: String): OpenAiTranscriptionResponse =
        runCatching { json.decodeFromString<OpenAiTranscriptionResponse>(body) }
            .getOrElse { throw IllegalStateException("Resposta inválida do serviço de transcrição.") }
}
