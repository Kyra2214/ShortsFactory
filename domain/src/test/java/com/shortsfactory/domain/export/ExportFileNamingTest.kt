package com.shortsfactory.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExportFileNamingTest {

    private fun fp(
        start: Long = 1000, end: Long = 31000, v: Int = 0, p: String = "yt", q: String = "Normal",
        r: String = "1080 × 1920", fps: Int = 30, style: String = "creator"
    ) = ExportFileNaming.fingerprint(start, end, v, p, q, r, fps, style)

    @Test
    fun fingerprintIsStableAndShort() {
        assertEquals(fp(), fp())
        assertEquals(10, fp().length)
    }

    @Test
    fun fingerprintChangesWithEachInput() {
        val base = fp()
        assertNotEquals(base, fp(start = 2000))
        assertNotEquals(base, fp(end = 32000))
        assertNotEquals(base, fp(v = 1))
        assertNotEquals(base, fp(p = "tt"))
        assertNotEquals(base, fp(q = "Alta"))
        assertNotEquals(base, fp(r = "720 × 1280"))
        assertNotEquals(base, fp(fps = 60))
        assertNotEquals(base, fp(style = "minimal"))
    }

    @Test
    fun fileNameFollowsPattern() {
        val name = ExportFileNaming.fileName(7, "yt,tt", "Normal", "1080 × 1920", 30, "ab12cd34ef")
        assertEquals("7_yt-tt_normal_1080-x-1920_30_ab12cd34ef.mp4", name)
    }

    @Test
    fun partNameAppendsSuffix() {
        assertEquals("a.mp4.part", ExportFileNaming.partName("a.mp4"))
    }

    @Test
    fun directoryIsPerProject() {
        assertEquals("exports/42", ExportFileNaming.directory(42))
    }
}
