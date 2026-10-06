package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.editor.RangeError
import com.shortsfactory.domain.editor.RangeValidation
import com.shortsfactory.domain.editor.ShortRangeValidator
import com.shortsfactory.domain.editor.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortRangeValidatorTest {

    private fun error(r: RangeValidation) = (r as RangeValidation.Invalid).error

    @Test
    fun `valid range is returned as is`() {
        val r = ShortRangeValidator.validate(10_000, 40_000, 100_000)
        assertEquals(RangeValidation.Valid(TimeRange(10_000, 40_000)), r)
    }

    @Test
    fun `negative start and non increasing range are rejected`() {
        assertEquals(RangeError.START_NEGATIVE, error(ShortRangeValidator.validate(-1, 10_000, 100_000)))
        assertEquals(RangeError.END_NOT_AFTER_START, error(ShortRangeValidator.validate(5_000, 5_000, 100_000)))
        assertEquals(RangeError.END_NOT_AFTER_START, error(ShortRangeValidator.validate(9_000, 5_000, 100_000)))
    }

    @Test
    fun `end beyond video is rejected, end equal to duration is accepted`() {
        assertEquals(RangeError.BEYOND_VIDEO, error(ShortRangeValidator.validate(90_000, 100_001, 100_000)))
        assertTrue(ShortRangeValidator.validate(90_000, 100_000, 100_000) is RangeValidation.Valid)
    }

    @Test
    fun `unknown video duration does not limit the end`() {
        assertTrue(ShortRangeValidator.validate(0, 10_000, null) is RangeValidation.Valid)
        assertTrue(ShortRangeValidator.validate(0, 10_000, 0L) is RangeValidation.Valid)
    }

    @Test
    fun `minimum and maximum duration use the selection rules`() {
        assertEquals(RangeError.TOO_SHORT, error(ShortRangeValidator.validate(0, 2_999, 100_000)))
        assertTrue(ShortRangeValidator.validate(0, 3_000, 100_000) is RangeValidation.Valid)
        assertTrue(ShortRangeValidator.validate(0, 90_000, 200_000) is RangeValidation.Valid)
        assertEquals(RangeError.TOO_LONG, error(ShortRangeValidator.validate(0, 90_001, 200_000)))
    }

    @Test
    fun `overlap with another short is rejected but touching is fine`() {
        val others = listOf(TimeRange(0, 20_000), TimeRange(50_000, 70_000))
        assertEquals(RangeError.OVERLAPS_OTHER, error(ShortRangeValidator.validate(15_000, 30_000, 100_000, others)))
        assertEquals(RangeError.OVERLAPS_OTHER, error(ShortRangeValidator.validate(40_000, 55_000, 100_000, others)))
        assertTrue(ShortRangeValidator.validate(20_000, 50_000, 100_000, others) is RangeValidation.Valid)
    }

    @Test
    fun `messages are user readable`() {
        val r = ShortRangeValidator.validate(0, 120_000, 100_000) as RangeValidation.Invalid
        assertTrue(r.message, r.message.contains("100 s"))
    }
}
