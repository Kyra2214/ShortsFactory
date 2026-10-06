package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.DurationPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DurationPresetTest {
    @Test
    fun `every preset round-trips through its key`() {
        DurationPreset.ALL.forEach { assertSame(it, DurationPreset.fromKey(it.key)) }
        assertEquals(6, DurationPreset.ALL.size)
        assertEquals(DurationPreset.ALL.size, DurationPreset.ALL.map { it.key }.toSet().size)
    }

    @Test
    fun `keys map to the expected durations`() {
        assertEquals(15_000L, DurationPreset.fromKey("15s").maxMs)
        assertEquals(30_000L, DurationPreset.fromKey("30s").maxMs)
        assertEquals(45_000L, DurationPreset.fromKey("45s").maxMs)
        assertEquals(60_000L, DurationPreset.fromKey("60s").maxMs)
        assertEquals(90_000L, DurationPreset.fromKey("90s").maxMs)
        assertNull(DurationPreset.fromKey("ai").maxMs)
    }

    @Test
    fun `unknown keys`() {
        assertNull(DurationPreset.fromKeyOrNull("30"))
        assertNull(DurationPreset.fromKeyOrNull("AI"))
        assertThrows(IllegalArgumentException::class.java) { DurationPreset.fromKey("30") }
    }
}
