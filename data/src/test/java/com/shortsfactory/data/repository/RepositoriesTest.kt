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
    fun `metadata update persists only editable fields and interval`() = runTest {
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
        ShortRepository(dao).updateMetadata(7L, "new", "description", "#tag", "cta", 100L, 900L)

        assertEquals("new", dao.updated.title)
        assertEquals("original hook", dao.updated.hook)
        assertEquals("description", dao.updated.description)
        assertEquals("#tag", dao.updated.hashtags)
        assertEquals("cta", dao.updated.cta)
        assertEquals(100L, dao.updated.startMs)
        assertEquals(900L, dao.updated.endMs)
        assertEquals("/exports/old.mp4", dao.updated.localPath)
        assertEquals("done", dao.updated.status)
    }

    private class FakeTranscriptDao(
        private var value: TranscriptEntity? = null
    ) : TranscriptDao {
        override suspend fun insert(entity: TranscriptEntity): Long {
            value = entity
            return 1L
        }
        override suspend fun getByProject(projectId: Long): TranscriptEntity? = value?.takeIf { it.projectId == projectId }
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
            outputPath: String?
        ) {
            value = value.copy(
                status = status,
                progress = progress,
                attemptCount = attemptCount,
                errorMessage = if (replaceError) errorMessage else value.errorMessage,
                startedAtMs = startedAtMs ?: value.startedAtMs,
                completedAtMs = completedAtMs ?: value.completedAtMs,
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
        override suspend fun updateMetadata(id: Long, title: String, description: String, hashtags: String, cta: String, startMs: Long, endMs: Long, updatedAtMs: Long) {
            updated = updated.copy(
                title = title,
                description = description,
                hashtags = hashtags,
                cta = cta,
                startMs = startMs,
                endMs = endMs,
                updatedAtMs = updatedAtMs
            )
        }
        override suspend fun updateExportState(id: Long, status: String, progress: Float, error: String?, localPath: String?, updatedAtMs: Long) {
            updated = updated.copy(
                localPath = localPath ?: updated.localPath,
                status = status,
                exportProgress = progress,
                exportError = error,
                updatedAtMs = updatedAtMs
            )
        }
    }
}
