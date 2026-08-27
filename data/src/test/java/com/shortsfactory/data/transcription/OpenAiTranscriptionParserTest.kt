package com.shortsfactory.data.transcription

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiTranscriptionParserTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses verbose segments`() {
        val response = OpenAiTranscriptionParser.parse(
            json,
            """
            {
              "text": "Olá mundo",
              "segments": [
                {"start": 0.5, "end": 2.25, "text": " Olá mundo "}
              ]
            }
            """.trimIndent()
        )

        assertEquals("Olá mundo", response.text)
        assertEquals(1, response.segments.size)
        assertEquals(0.5, response.segments.first().start!!, 0.001)
        assertEquals(" Olá mundo ", response.segments.first().text)
    }

    @Test
    fun `unknown fields are ignored`() {
        val response = OpenAiTranscriptionParser.parse(json, "{\"text\":\"ok\",\"language\":\"pt\"}")

        assertEquals("ok", response.text)
        assertEquals(0, response.segments.size)
    }

    @Test
    fun `invalid response throws safe error`() {
        val error = assertThrows(IllegalStateException::class.java) {
            OpenAiTranscriptionParser.parse(json, "not-json")
        }

        assertEquals("Resposta inválida do serviço de transcrição.", error.message)
    }
}
