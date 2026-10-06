package com.shortsfactory.data.repository

import com.shortsfactory.data.local.dao.ExportDao
import com.shortsfactory.data.local.dao.ShortDao
import com.shortsfactory.data.local.dao.TranscriptDao
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.TranscriptEntity
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoriesTest {

    @Test
    fun `transcript repository round trips special text`() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepository(dao)
        val transcript = Transcript(listOf(TranscriptSegment(0L, 1_000L, "vírgula, aspas \" e \\")))

        repository.save(42L, transcript)

        assertEquals(transcript, repository.get(42L))
    }

    @Test
    fun `transcript repository returns null for corrupt persisted payload`() = runTest {
        val dao = FakeTranscriptDao(TranscriptEntity(projectId = 42L, json = "corrupt"))

        assertNull(TranscriptRepository(dao).get(42L))
    }

    @Test
    fun `export progress preserves error until an explicit transition clears it`() = runTest {
        val dao = FakeExportDao(
            ExportEntity(
                id = 10L,
                projectId = 1L,
                platform = "youtube",
                quality = "Normal",
                resolution = "1080x1920",
                fps = 30,
                status = "failed",
                progress = 0.4f,
                attemptCount = 1,
                errorMessage = "timeout"
            )
        )
        val repository = ExportRepository(dao)

        repository.updateProgress(10L, 0.6f)
        assertEquals("timeout", dao.value.errorMessage)
        assertEquals(0.6f, dao.value.progress)

        val running = repository.markRunning(10L)
        assertEquals("running", running?.status)
        assertNull(running?.errorMessage)
        assertEquals(2, running?.attemptCount)

        repository.markFailed(10L, "http 503")
        assertEquals("http 503", dao.value.errorMessage)
        assertEquals("failed", dao.value.status)
        assertEquals(true, dao.value.completedAtMs != null)

        repository.markDone(10L, "/exports/final.mp4")
        assertEquals("done", dao.value.status)
        assertEquals(1f, dao.value.progress)
        assertNull(dao.value.errorMessage)
        assertEquals("/exports/final.mp4", dao.value.outputPath)
    }

    @Test
    fun `metadata update persists editable fields including hook, and interval`() = runTest {
        val existing = ShortEntity(
            id = 7L,
            projectId = 1L,
            startMs = 0L,
            endMs = 1_000L,
            score = 0.9f,
            title = "old",
            hook = "original hook",
            topic = "original topic",
            reason = "original reason",
            localPath = "/exports/old.mp4",
            status = "done"
        )
        val dao = FakeShortDao(existing)
        ShortRepository(dao).updateMetadata(7L, "new", " new hook ", "description", "#tag", "cta", 100L, 900L)

        assertEquals("new", dao.updated.title)
        assertEquals("new hook", dao.updated.hook)
        assertEquals("original topic", dao.updated.topic)
        assertEquals("original reason", dao.updated.reason)
        assertEquals("description", dao.updated.description)
        assertEquals("#tag", dao.updated.hashtags)
        assertEquals("cta", dao.updated.cta)
        assertEquals(100L, dao.updated.startMs)
        assertEquals(900L, dao.updated.endMs)
        assertEquals("/exports/old.mp4", dao.updated.localPath)
        assertEquals("done", dao.updated.status)
    }

    @Test
    fun `short export progress keeps the error until an explicit transition`() = runTest {
        val dao = FakeShortDao(ShortEntity(id = 7L, projectId = 1L, startMs = 0L, endMs = 5_000L, score = 1f,
            title = "t", hook = "h", topic = "x", reason = "r", status = "failed", exportError = "falhou antes"))
        val repository = ShortRepository(dao)

        repository.updateExportProgress(7L, "queued", 0f)
        assertEquals("falhou antes", dao.updated.exportError)

        repository.updateExportProgress(7L, "failed", 0f, "novo erro")
        assertEquals("novo erro", dao.updated.exportError)

        repository.updateExportState(7L, null, "cancelled")
        assertEquals("novo erro", dao.updated.exportError)

        repository.updateExportProgress(7L, "processing", 0f)
        assertNull(dao.updated.exportError)

        repository.updateExportProgress(7L, "failed", 0f, "outro erro")
        repository.updateExportState(7L, "/exports/ok.mp4", "done")
        assertNull(dao.updated.exportError)
        assertEquals("/exports/ok.mp4", dao.updated.localPath)
    }

    @Test
    fun `retry clears the completion time of the previous attempt`() = runTest {
        val dao = FakeExportDao(
            ExportEntity(id = 10L, projectId = 1L, platform = "yt", quality = "Normal", resolution = "r", fps = 30,
                status = "failed", attemptCount = 1, startedAtMs = 100L, completedAtMs = 200L, errorMessage = "x")
        )
        val repository = ExportRepository(dao)

        repository.markQueued(10L)
        assertNull(dao.value.completedAtMs)
        repository.markFailed(10L, "de novo")
        assertEquals(true, dao.value.completedAtMs != null)

        repository.markRunning(10L)
        assertNull(dao.value.completedAtMs)
        assertEquals(100L, dao.value.startedAtMs)
        assertEquals(2, dao.value.attemptCount)
    }

    @Test
    fun `candidate entity carries persisted subtitles and focus track`() {
        val candidate = com.shortsfactory.domain.model.ShortCandidate(
            score = 0.7f, startMs = 1_000L, endMs = 9_000L, title = "t", hook = "h", topic = "x", reason = "r",
            focusTrack = com.shortsfactory.domain.pipeline.FocusTrack(
                listOf(com.shortsfactory.domain.pipeline.FocusPoint(0L, 0.4f, 0.5f, 0.2f, 0.3f)),
                com.shortsfactory.domain.pipeline.TrackingMethod.FACE_TRACKING
            ),
            subtitles = listOf(com.shortsfactory.domain.model.SubtitleSegment(0L, 1_500L, listOf("olá", "mundo")))
        )
        val entity = ShortRepository.candidateToEntity(3L, candidate)

        assertEquals(3L, entity.projectId)
        assertEquals("pending", entity.status)
        assertEquals(0, entity.intervalVersion)
        assertEquals(candidate.subtitles, CandidateArtifactsCodec.decodeSubtitles(entity.subtitlesJson))
        assertEquals(candidate.focusTrack, CandidateArtifactsCodec.decodeFocusTrack(entity.focusTrackJson))
    }

    @Test
    fun `candidate without subtitles or focus stores null so it is recomputed`() {
        val entity = ShortRepository.candidateToEntity(
            1L, com.shortsfactory.domain.model.ShortCandidate(0.5f, 0L, 5_000L, "t", "h", "x", "r")
        )
        assertNull(entity.subtitlesJson)
        assertNull(entity.focusTrackJson)
    }

    @Test
    fun `artifacts codec treats missing or corrupt json as absent`() {
        assertNull(CandidateArtifactsCodec.decodeSubtitles(null))
        assertNull(CandidateArtifactsCodec.decodeSubtitles(""))
        assertNull(CandidateArtifactsCodec.decodeSubtitles("{nao e json"))
        assertNull(CandidateArtifactsCodec.decodeFocusTrack("[]"))
        assertNull(CandidateArtifactsCodec.decodeFocusTrack("""{"method":"INEXISTENTE","points":[{"timeMs":0,"centerX":0.5,"centerY":0.5,"width":1,"height":1}]}"""))
        assertNull(CandidateArtifactsCodec.decodeFocusTrack("""{"method":"STATIC_CENTER","points":[]}"""))
        assertEquals(emptyList<com.shortsfactory.domain.model.SubtitleSegment>(), CandidateArtifactsCodec.decodeSubtitles("[]"))
    }

    private class FakeTranscriptDao(
        private var value: TranscriptEntity? = null
    ) : TranscriptDao {
        override suspend fun insert(entity: TranscriptEntity): Long {
            value = entity
            return 1L
        }
        override suspend fun getByProject(projectId: Long): TranscriptEntity? = value?.takeIf { it.projectId == projectId }
        override suspend fun deleteByProject(projectId: Long) { value = value?.takeUnless { it.projectId == projectId } }
    }

    private class FakeExportDao(
        initial: ExportEntity
    ) : ExportDao {
        var value: ExportEntity = initial

        override suspend fun insert(entity: ExportEntity): Long {
            value = entity.copy(id = if (entity.id == 0L) 1L else entity.id)
            return value.id
        }

        override fun observeByProject(projectId: Long): Flow<List<ExportEntity>> =
            flowOf(listOf(value).filter { it.projectId == projectId })

        override suspend fun getById(id: Long): ExportEntity? = value.takeIf { it.id == id }

        override suspend fun deleteByShort(shortId: Long) = Unit

        override suspend fun getLatestForShort(
            projectId: Long,
            shortId: Long,
            platform: String,
            quality: String,
            resolution: String,
            fps: Int
        ): ExportEntity? = null

        override suspend fun getResumableByProject(projectId: Long): List<ExportEntity> =
            listOf(value).filter { it.projectId == projectId && it.status in setOf("pending", "queued", "running", "failed") }

        override suspend fun updateState(
            id: Long,
            status: String,
            progress: Float,
            attemptCount: Int,
            errorMessage: String?,
            replaceError: Boolean,
            startedAtMs: Long?,
            completedAtMs: Long?,
            resetCompleted: Boolean,
            outputPath: String?
        ) {
            value = value.copy(
                status = status,
                progress = progress,
                attemptCount = attemptCount,
                errorMessage = if (replaceError) errorMessage else value.errorMessage,
                startedAtMs = startedAtMs ?: value.startedAtMs,
                completedAtMs = if (resetCompleted) null else completedAtMs ?: value.completedAtMs,
                outputPath = outputPath ?: value.outputPath
            )
        }
    }

    private class FakeShortDao(
        initial: ShortEntity
    ) : ShortDao {
        var updated: ShortEntity = initial
        override suspend fun insert(entity: ShortEntity): Long = 1L
        override fun observeByProject(projectId: Long): Flow<List<ShortEntity>> = flowOf(listOf(updated))
        override suspend fun getByProject(projectId: Long): List<ShortEntity> = listOf(updated)
        override suspend fun getById(id: Long): ShortEntity? = updated.takeIf { it.id == id }
        override suspend fun update(entity: ShortEntity) { updated = entity }
        override suspend fun deleteByProject(projectId: Long) = Unit
        override suspend fun updateMetadata(id: Long, title: String, hook: String, description: String, hashtags: String, cta: String, startMs: Long, endMs: Long, updatedAtMs: Long) {
            // A invalidação por mudança de intervalo é SQL e é verificada em SQLite real
            // (ProjectStoreInstrumentedTest); este fake só registra os campos recebidos.
            updated = updated.copy(
                title = title,
                hook = hook,
                description = description,
                hashtags = hashtags,
                cta = cta,
                startMs = startMs,
                endMs = endMs,
                updatedAtMs = updatedAtMs
            )
        }
        override suspend fun updateExportState(id: Long, status: String, progress: Float, error: String?, replaceError: Boolean, localPath: String?, updatedAtMs: Long) {
            updated = updated.copy(
                localPath = localPath ?: updated.localPath,
                status = status,
                exportProgress = progress,
                exportError = if (replaceError) error else updated.exportError,
                updatedAtMs = updatedAtMs
            )
        }
    }
}
