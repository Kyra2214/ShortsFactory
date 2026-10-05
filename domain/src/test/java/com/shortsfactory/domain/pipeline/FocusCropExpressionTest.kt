package com.shortsfactory.domain.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Os valores esperados abaixo foram executados num FFmpeg real (6.1) sobre um vídeo de teste 1920x1080:
 * recorte 608x1080, rosto centralizado, limitado às bordas, interpolação linear e `t` relativo ao clipe.
 */
class FocusCropExpressionTest {

    private val prefix = "crop=w=min(iw\\,ih*1080/1920):h=min(ih\\,iw*1920/1080):"

    private fun point(timeMs: Long, x: Float, y: Float) = FocusPoint(timeMs, x, y, 0.2f, 0.2f)

    private fun track(vararg points: FocusPoint) = FocusTrack(points.toList(), TrackingMethod.FACE_TRACKING)

    @Test
    fun `without a track the crop is centered`() {
        val filter = FocusCropExpression.cropFilter(1080, 1920, null, 0L, 2_000L)
        assertEquals(prefix + "x=(iw-ow)/2:y=(ih-oh)/2", filter)
    }

    @Test
    fun `empty track is treated as no track`() {
        val filter = FocusCropExpression.cropFilter(1080, 1920, FocusTrack(emptyList(), TrackingMethod.STATIC_CENTER), 0L, 2_000L)
        assertEquals(prefix + "x=(iw-ow)/2:y=(ih-oh)/2", filter)
    }

    @Test
    fun `two points interpolate linearly using times relative to the clip start`() {
        // Pontos em tempo absoluto do vídeo-fonte (10s e 12s); clipe começa em 10s e dura 2s.
        val filter = FocusCropExpression.cropFilter(
            1080, 1920,
            track(point(10_000L, 0.2f, 0.4f), point(12_000L, 0.8f, 0.4f)),
            clipStartMs = 10_000L,
            clipDurationMs = 2_000L
        )
        assertEquals(
            prefix +
                "x=max(0\\,min(iw-ow\\,(if(lt(t\\,2)\\,0.2+0.3*t\\,0.8))*iw-ow/2)):" +
                "y=max(0\\,min(ih-oh\\,(0.4)*ih-oh/2))",
            filter
        )
    }

    @Test
    fun `negative slope is wrapped in parentheses and the first value is held until the first point`() {
        val filter = FocusCropExpression.cropFilter(
            1080, 1920,
            track(point(10_500L, 0.9f, 0.5f), point(11_500L, 0.1f, 0.5f)),
            clipStartMs = 10_000L,
            clipDurationMs = 2_000L
        )
        assertEquals(
            prefix +
                "x=max(0\\,min(iw-ow\\,(if(lt(t\\,0.5)\\,0.9\\,if(lt(t\\,1.5)\\,0.9+(-0.8)*(t-0.5)\\,0.1)))*iw-ow/2)):" +
                "y=max(0\\,min(ih-oh\\,(0.5)*ih-oh/2))",
            filter
        )
    }

    @Test
    fun `points outside the clip are ignored and fall back to a centered crop`() {
        val filter = FocusCropExpression.cropFilter(
            1080, 1920,
            track(point(1_000L, 0.1f, 0.1f), point(99_000L, 0.9f, 0.9f)),
            clipStartMs = 10_000L,
            clipDurationMs = 2_000L
        )
        assertEquals(prefix + "x=(iw-ow)/2:y=(ih-oh)/2", filter)
    }

    @Test
    fun `values are clamped to the 0 to 1 range and non finite points are dropped`() {
        val anchors = FocusCropExpression.anchors(
            listOf(point(0L, 1.7f, 0.5f), point(1_000L, Float.NaN, 0.5f), point(2_000L, -3f, 0.5f)),
            clipStartMs = 0L,
            clipDurationMs = 2_000L,
            axis = FocusCropExpression.Axis.X
        )
        assertEquals(listOf(0.0, 2.0), anchors.map { it.timeSec })
        assertEquals(listOf(1.0, 0.0), anchors.map { it.value })
    }

    @Test
    fun `unsorted and duplicated times are normalized`() {
        val anchors = FocusCropExpression.anchors(
            listOf(point(2_000L, 0.9f, 0.5f), point(0L, 0.1f, 0.5f), point(2_000L, 0.3f, 0.5f)),
            clipStartMs = 0L,
            clipDurationMs = 2_000L,
            axis = FocusCropExpression.Axis.X
        )
        assertEquals(2, anchors.size)
        assertEquals(0.0, anchors.first().timeSec, 0.0)
        assertEquals(2.0, anchors.last().timeSec, 0.0)
    }

    @Test
    fun `long tracks are reduced to the anchor limit keeping first and last`() {
        val points = (0..120).map { point(it * 1_000L, (it % 10) / 10f, 0.5f) }
        val anchors = FocusCropExpression.anchors(points, 0L, 120_000L, FocusCropExpression.Axis.X)
        assertTrue(anchors.size <= FocusCropExpression.MAX_ANCHORS)
        assertEquals(0.0, anchors.first().timeSec, 0.0)
        assertEquals(120.0, anchors.last().timeSec, 0.0)
        val expression = FocusCropExpression.centerExpression(anchors)
        assertEquals(anchors.size - 1, Regex("if\\(lt").findAll(expression).count())
        assertEquals(expression.count { it == '(' }, expression.count { it == ')' })
    }

    @Test
    fun `numbers do not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("pt", "BR"))
            val filter = FocusCropExpression.cropFilter(
                1080, 1920,
                track(point(0L, 0.25f, 0.5f), point(2_000L, 0.75f, 0.5f)),
                0L, 2_000L
            )
            assertTrue(filter.contains("0.25"))
            assertFalse(Regex("\\d,\\d").containsMatchIn(filter))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `every comma inside the filter is escaped`() {
        val filter = FocusCropExpression.cropFilter(
            720, 1280,
            track(point(0L, 0.3f, 0.4f), point(1_000L, 0.6f, 0.5f), point(2_000L, 0.5f, 0.6f)),
            0L, 2_000L
        )
        assertFalse(Regex("(?<!\\\\),").containsMatchIn(filter))
        assertTrue(filter.startsWith("crop=w=min(iw\\,ih*720/1280):h=min(ih\\,iw*1280/720):"))
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertThrowsIllegal { FocusCropExpression.cropFilter(0, 1920, null, 0L, 1_000L) }
        assertThrowsIllegal { FocusCropExpression.cropFilter(1080, 1920, null, 0L, 0L) }
    }

    private fun assertThrowsIllegal(block: () -> Unit) {
        try {
            block()
        } catch (expected: IllegalArgumentException) {
            return
        }
        throw AssertionError("Era esperado IllegalArgumentException.")
    }
}
