package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateScorerTest {
    @Test
    fun `score stays bounded and rewards observable hook and speech density`() {
        val candidate = ShortCandidate(
            score = 0.8f,
            startMs = 0L,
            endMs = 10_000L,
            title = "title",
            hook = "Isso é inacreditável!",
            topic = "topic",
            reason = "reason"
        )
        val transcript = Transcript(listOf(TranscriptSegment(0L, 10_000L, "fala completa")))
        val scored = CandidateScorer().score(candidate, transcript, 30_000L)
        assertTrue(scored.score in 0f..1f)
        assertTrue(scored.score > 0.5f)
    }
}
