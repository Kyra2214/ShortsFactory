package com.shortsfactory.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImportPoliciesTest {

    @Test
    fun `espaco - limite e margem`() {
        val mib = 1024L * 1024L
        assertNotNull(ImportSpacePolicy.check(1L, ImportSpacePolicy.MAX_IMPORT_BYTES + 1, Long.MAX_VALUE))
        assertNull(ImportSpacePolicy.check(1_000 * mib, 1_000 * mib, 2_000 * mib))
        assertNotNull(ImportSpacePolicy.check(1_000 * mib, 1_000 * mib, 1_050 * mib))
        assertNull(ImportSpacePolicy.check(-1L, -1L, 0L))
    }

    @Test
    fun `content-range valido e invalido`() {
        assertEquals(ContentRange(100, 199, 1000), HttpResume.parseContentRange("bytes 100-199/1000"))
        assertEquals(ContentRange(100, 199, null), HttpResume.parseContentRange("bytes 100-199/*"))
        assertNull(HttpResume.parseContentRange(null))
        assertNull(HttpResume.parseContentRange("bytes 200-100/1000"))
        assertNull(HttpResume.parseContentRange("bytes 0-1000/1000"))
        assertNull(HttpResume.parseContentRange("items 0-1/2"))
    }

    @Test
    fun `validador if-range ignora etag fraco`() {
        assertEquals("\"abc\"", HttpResume.ifRangeValidator("\"abc\"", "Mon"))
        assertEquals("Mon", HttpResume.ifRangeValidator("W/\"abc\"", "Mon"))
        assertNull(HttpResume.ifRangeValidator(null, null))
        assertNull(HttpResume.ifRangeValidator(" ", ""))
    }

    @Test
    fun `validateResume exige inicio igual ao offset`() {
        assertNull(HttpResume.validateResume(ContentRange(500, 999, 1000), 500))
        assertNotNull(HttpResume.validateResume(ContentRange(0, 999, 1000), 500))
        assertNotNull(HttpResume.validateResume(null, 500))
        assertNotNull(HttpResume.validateResume(ContentRange(500, 999, ImportSpacePolicy.MAX_IMPORT_BYTES + 1), 500))
    }

    @Test
    fun `extensao prioriza mime sobre url`() {
        assertEquals("webm", ImportExtension.resolve("video/webm; codecs=vp9", "x.mp4"))
        assertEquals("mov", ImportExtension.resolve(null, "/a/b.MOV?token=1"))
        assertEquals("mp4", ImportExtension.resolve("application/octet-stream", "/watch"))
        assertEquals("mp4", ImportExtension.resolve(null, null))
        assertEquals("mkv", ImportExtension.resolve("video/x-matroska", null))
    }
}
