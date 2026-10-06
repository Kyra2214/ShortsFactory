package com.shortsfactory.data.repository

import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.pipeline.FocusPoint
import com.shortsfactory.domain.pipeline.FocusTrack
import com.shortsfactory.domain.pipeline.TrackingMethod
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Serializa as legendas e a trilha de foco de um candidato (colunas `subtitlesJson` / `focusTrackJson`).
 * Os tempos já são relativos ao clipe. JSON ausente ou corrompido decodifica para `null`
 * ("recalcular"), nunca para lista/trilha vazia que pareceria um resultado válido.
 */
object CandidateArtifactsCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Serializable
    private data class Sub(val startMs: Long, val endMs: Long, val words: List<String>)

    @Serializable
    private data class Point(val timeMs: Long, val centerX: Float, val centerY: Float, val width: Float, val height: Float)

    @Serializable
    private data class Track(val method: String, val points: List<Point>)

    fun encodeSubtitles(subtitles: List<SubtitleSegment>): String =
        json.encodeToString(ListSerializer(Sub.serializer()), subtitles.map { Sub(it.startMs, it.endMs, it.words) })

    fun decodeSubtitles(raw: String?): List<SubtitleSegment>? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(ListSerializer(Sub.serializer()), raw).map { SubtitleSegment(it.startMs, it.endMs, it.words) }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun encodeFocusTrack(track: FocusTrack): String =
        json.encodeToString(
            Track.serializer(),
            Track(track.method.name, track.points.map { Point(it.timeMs, it.centerX, it.centerY, it.width, it.height) })
        )

    fun decodeFocusTrack(raw: String?): FocusTrack? {
        if (raw.isNullOrBlank()) return null
        return try {
            val track = json.decodeFromString(Track.serializer(), raw)
            val method = TrackingMethod.entries.firstOrNull { it.name == track.method } ?: return null
            if (track.points.isEmpty()) return null
            FocusTrack(track.points.map { FocusPoint(it.timeMs, it.centerX, it.centerY, it.width, it.height) }, method)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
