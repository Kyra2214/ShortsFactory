package com.shortsfactory.domain.export

import com.shortsfactory.domain.model.ExportPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlannerTest {
    private fun plan(w: Int, h: Int, dur: Double = 30.0, fps: Int? = null, vararg p: ExportPlatform) =
        ExportPlanner.plan(ExportPlanRequest(w, h, fps, dur, p.toList()))

    @Test fun `origem 4K nao reduz e vai ao tamanho do perfil`() {
        val item = plan(3840, 2160, 30.0, null, ExportPlatform.YOUTUBE).items.single()
        assertEquals(1080, item.width)
        assertEquals(1920, item.height)
        assertFalse(item.reducedBySource)
    }

    @Test fun `origem paisagem 1080p nao e ampliada`() {
        val item = plan(1920, 1080, 30.0, null, ExportPlatform.YOUTUBE).items.single()
        assertEquals(1080, item.height)
        assertEquals(606, item.width)
        assertTrue(item.reducedBySource)
    }

    @Test fun `origem vertical pequena usa a altura util da origem`() {
        val item = plan(720, 1280, 30.0, null, ExportPlatform.TIKTOK).items.single()
        assertEquals(1280, item.height)
        assertEquals(720, item.width)
        assertTrue(item.reducedBySource)
    }

    @Test fun `fps e limitado ao da origem`() {
        assertEquals(24, plan(3840, 2160, 30.0, 24, ExportPlatform.YOUTUBE).items.single().fps)
        assertEquals(30, plan(3840, 2160, 30.0, 60, ExportPlatform.YOUTUBE).items.single().fps)
    }

    @Test fun `aviso de duracao nao bloqueia e aponta a plataforma`() {
        val result = plan(3840, 2160, 100.0, null, ExportPlatform.YOUTUBE, ExportPlatform.INSTAGRAM)
        assertEquals(listOf(ExportPlatform.INSTAGRAM), result.durationWarnings)
        assertEquals(2, result.items.size)
    }

    @Test fun `perfis iguais compartilham um unico arquivo`() {
        val result = plan(3840, 2160, 30.0, null, *ExportPlatform.entries.toTypedArray())
        assertEquals(1, result.fileGroups.size)
        assertEquals(ExportPlatform.entries.toSet(), result.fileGroups.single().platforms.toSet())
    }

    @Test fun `plataformas repetidas sao unificadas`() {
        assertEquals(1, plan(3840, 2160, 30.0, null, ExportPlatform.YOUTUBE, ExportPlatform.YOUTUBE).items.size)
    }

    @Test fun `entrada invalida e rejeitada`() {
        assertThrows(IllegalArgumentException::class.java) { plan(0, 1080, 30.0, null, ExportPlatform.YOUTUBE) }
        assertThrows(IllegalArgumentException::class.java) { plan(1920, 1080, 0.0, null, ExportPlatform.YOUTUBE) }
    }
}
