package com.shortsfactory.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Codec versionado e tolerante para persistir transcrições no banco local. */
object TranscriptCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(transcript: Transcript): String = json.encodeToString(
        TranscriptPayload.serializer(),
        TranscriptPayload(
            segments = transcript.segments.map { segment ->
                TranscriptSegmentPayload(segment.startMs, segment.endMs, segment.text)
            }
        )
    )

    fun decode(value: String): Transcript? = try {
        json.decodeFromString(TranscriptPayload.serializer(), value).let { payload ->
            Transcript(payload.segments.map { segment ->
                TranscriptSegment(segment.startMs, segment.endMs, segment.text)
            })
        }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    @Serializable
    private data class TranscriptPayload(
        val segments: List<TranscriptSegmentPayload> = emptyList()
    )

    @Serializable
    private data class TranscriptSegmentPayload(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )
}
