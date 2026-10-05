package com.shortsfactory.domain.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FocusTrackBuilderTest {

    private val delta = 1e-5f

    @Test
    fun `no samples gives a single default point at the start`() {
        val track = FocusTrackBuilder.build(5_000L, emptyList())
        assertEquals(TrackingMethod.STATIC_CENTER, track.method)
        assertEquals(listOf(FocusTrackBuilder.defaultPoint(5_000L)), track.points)
    }

    @Test
    fun `samples without faces are static center with the time of each sample`() {
        val track = FocusTrackBuilder.build(10_000L, listOf(null, null, null))
        assertEquals(TrackingMethod.STATIC_CENTER, track.method)
        assertEquals(listOf(10_000L, 11_000L, 12_000L), track.points.map { it.timeMs })
        track.points.forEach {
            assertEquals(0.5f, it.centerX, delta)
            assertEquals(0.42f, it.centerY, delta)
        }
    }

    @Test
    fun `one detection makes it face tracking and uses the sample time`() {
        val detected = FocusPoint(timeMs = 999_999L, centerX = 0.9f, centerY = 0.42f, width = 0.6f, height = 0.7f)
        val track = FocusTrackBuilder.build(0L, listOf(null, detected, null))
        assertEquals(TrackingMethod.FACE_TRACKING, track.method)
        assertEquals(listOf(0L, 1_000L, 2_000L), track.points.map { it.timeMs })
        assertEquals(0.5f, track.points[0].centerX, delta)
        assertEquals(0.5f * 0.35f + 0.9f * 0.65f, track.points[1].centerX, delta)
    }

    @Test
    fun `values stay within zero and one`() {
        val detected = FocusPoint(0L, 1.5f, -0.5f, 2f, 2f)
        val track = FocusTrackBuilder.build(0L, listOf(null, detected))
        track.points.forEach {
            assertEquals(it.centerX, it.centerX.coerceIn(0f, 1f), 0f)
            assertEquals(it.centerY, it.centerY.coerceIn(0f, 1f), 0f)
            assertEquals(it.width, it.width.coerceIn(0f, 1f), 0f)
        }
    }

    @Test
    fun `invalid step is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FocusTrackBuilder.build(0L, listOf(null), stepMs = 0L) }
    }
}
