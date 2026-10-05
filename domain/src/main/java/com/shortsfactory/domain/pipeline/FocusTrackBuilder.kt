package com.shortsfactory.domain.pipeline

/** Monta a [FocusTrack] a partir das detecções por amostra. Kotlin puro (testável na JVM). */
object FocusTrackBuilder {

    const val SAMPLE_STEP_MS = 1000L
    const val MIN_FACE_DETECTIONS = 1
    private const val SMOOTHING_ALPHA = 0.65f

    private const val DEFAULT_CENTER_X = 0.5f
    private const val DEFAULT_CENTER_Y = 0.42f
    private const val DEFAULT_WIDTH = 0.6f
    private const val DEFAULT_HEIGHT = 0.7f

    /**
     * [detections] tem uma entrada por amostra (índice `i` ↔ tempo `startMs + i * stepMs`, absoluto); `null` = sem rosto.
     * Amostras sem rosto usam o foco central seguro com o tempo da própria amostra.
     * O método é [TrackingMethod.FACE_TRACKING] somente com ao menos [MIN_FACE_DETECTIONS] rostos detectados.
     */
    fun build(startMs: Long, detections: List<FocusPoint?>, stepMs: Long = SAMPLE_STEP_MS): FocusTrack {
        require(stepMs > 0L) { "O passo de amostragem deve ser positivo." }
        if (detections.isEmpty()) {
            return FocusTrack(listOf(defaultPoint(startMs)), TrackingMethod.STATIC_CENTER)
        }
        val points = detections.mapIndexed { index, detected ->
            val timeMs = startMs + index * stepMs
            detected?.copy(timeMs = timeMs) ?: defaultPoint(timeMs)
        }
        val method = if (detections.count { it != null } >= MIN_FACE_DETECTIONS) {
            TrackingMethod.FACE_TRACKING
        } else {
            TrackingMethod.STATIC_CENTER
        }
        return FocusTrack(smooth(points), method)
    }

    fun defaultPoint(timeMs: Long) =
        FocusPoint(timeMs, DEFAULT_CENTER_X, DEFAULT_CENTER_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT)

    private fun smooth(points: List<FocusPoint>): List<FocusPoint> {
        if (points.size < 2) return points
        val smoothed = mutableListOf(points.first())
        for (point in points.drop(1)) {
            val previous = smoothed.last()
            smoothed += point.copy(
                centerX = blend(previous.centerX, point.centerX),
                centerY = blend(previous.centerY, point.centerY),
                width = blend(previous.width, point.width),
                height = blend(previous.height, point.height)
            )
        }
        return smoothed
    }

    private fun blend(previous: Float, current: Float): Float =
        (previous * (1f - SMOOTHING_ALPHA) + current * SMOOTHING_ALPHA).coerceIn(0f, 1f)
}
