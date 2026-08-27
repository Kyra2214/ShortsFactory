package com.shortsfactory.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptCodecTest {

    @Test
    fun `round trip preserves special characters and timings`() {
        val transcript = Transcript(
            listOf(
                TranscriptSegment(
                    startMs = 0L,
                    endMs = 2_500L,
                    text = "Aspas \"duplas\", vírgula, barra \\ e\nquebra de linha"
                )
            )
        )

        val decoded = TranscriptCodec.decode(TranscriptCodec.encode(transcript))

        assertEquals(transcript, decoded)
    }

    @Test
    fun `round trip preserves empty transcript`() {
        assertEquals(Transcript(emptyList()), TranscriptCodec.decode(TranscriptCodec.encode(Transcript(emptyList()))))
    }

    @Test
    fun `invalid json returns null instead of creating corrupt segments`() {
        assertNull(TranscriptCodec.decode("not-json"))
    }
}
