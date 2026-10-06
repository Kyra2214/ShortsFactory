package com.shortsfactory.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewClipRangeTest {

    @Test
    fun validRangeIsKept() =
        assertEquals(TimeRange(5_000, 20_000), PreviewClipRange.resolve(5_000, 20_000, 60_000))

    @Test
    fun negativeStartIsClampedToZero() =
        assertEquals(TimeRange(0, 10_000), PreviewClipRange.resolve(-500, 10_000, 60_000))

    @Test
    fun endBeyondVideoIsClampedToDuration() =
        assertEquals(TimeRange(50_000, 60_000), PreviewClipRange.resolve(50_000, 90_000, 60_000))

    @Test
    fun startNearEndKeepsMinimumDuration() =
        assertEquals(TimeRange(59_000, 60_000), PreviewClipRange.resolve(60_000, 60_000, 60_000))

    @Test
    fun endBeforeStartGetsMinimumDuration() =
        assertEquals(TimeRange(10_000, 11_000), PreviewClipRange.resolve(10_000, 4_000, 60_000))

    @Test
    fun unknownOrTooShortVideoHasNoRange() {
        assertNull(PreviewClipRange.resolve(0, 5_000, 0))
        assertNull(PreviewClipRange.resolve(0, 500, 999))
    }
}
