package com.shortsfactory.domain.export

import com.shortsfactory.domain.model.ExportPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformProfilesTest {
    @Test fun `todas as plataformas tem perfil 9 por 16`() {
        ExportPlatform.entries.forEach { platform ->
            val p = PlatformProfiles.forPlatform(platform)
            assertEquals(platform, p.platform)
            assertEquals(p.width * 16, p.height * 9)
        }
        assertEquals(ExportPlatform.entries.size, PlatformProfiles.all().size)
    }

    @Test fun `perfil nao verificado declara a suposicao na nota de origem`() {
        PlatformProfiles.all().filter { !it.verified }.forEach { assertTrue(it.sourceNote.contains("ASSUMINDO")) }
    }

    @Test fun `forKey resolve chave conhecida e ignora desconhecida`() {
        assertEquals(ExportPlatform.YOUTUBE, PlatformProfiles.forKey("yt")?.platform)
        assertNull(PlatformProfiles.forKey("zz"))
    }

    @Test fun `perfis com a mesma codificacao tem a mesma chave`() {
        val yt = PlatformProfiles.forPlatform(ExportPlatform.YOUTUBE)
        val ig = PlatformProfiles.forPlatform(ExportPlatform.INSTAGRAM)
        assertEquals(yt.encodingKey, ig.encodingKey)
    }

    @Test fun `limites de texto respeitam plataformas sem titulo`() {
        assertEquals(0, PlatformProfiles.forPlatform(ExportPlatform.TIKTOK).textLimits.titleMaxChars)
        assertTrue(PlatformProfiles.forPlatform(ExportPlatform.YOUTUBE).textLimits.titleMaxChars > 0)
    }
}
