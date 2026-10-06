package com.shortsfactory.export

import android.content.Context
import android.content.ContextWrapper
import com.shortsfactory.data.local.dao.ExportBatchDao
import com.shortsfactory.data.local.dao.ExportDao
import com.shortsfactory.data.local.dao.ProjectDao
import com.shortsfactory.data.local.dao.ShortDao
import com.shortsfactory.data.local.dao.TranscriptDao
import com.shortsfactory.data.local.entity.ExportBatchEntity
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.TranscriptEntity
import com.shortsfactory.data.repository.ExportBatchRepository
import com.shortsfactory.data.repository.ExportRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.TranscriptRepository
import com.shortsfactory.domain.export.ExportBatchState
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.pipeline.ClipSpec
import com.shortsfactory.domain.pipeline.FocusTrack
import com.shortsfactory.domain.pipeline.InputVideoInfo
import com.shortsfactory.domain.pipeline.TrackingMethod
import com.shortsfactory.domain.pipeline.VideoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsProcessingManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var shortDao: FakeShortDao
    private lateinit var exportDao: FakeExportDao
    private lateinit var batchDao: FakeBatchDao
    private lateinit var engine: FakeEngine
    private lateinit var manager: ShortsProcessingManager
    private lateinit var shortRepo: ShortRepository
    private var projectId = 1L

    private class FakeContext(private val files: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = files
    }

    @Before fun setUp() {
        val projectDao = FakeProjectDao(
            ProjectEntity(id = 1, name = "p", videoUri = "/v.mp4", videoName = "v", videoDurationMs = 120_000,
                videoWidth = 1920, videoHeight = 1080, videoSizeBytes = 1, sourceType = "local")
        )
        shortDao = FakeShortDao()
        exportDao = FakeExportDao()
        batchDao = FakeBatchDao()
        engine = FakeEngine()
        shortRepo = ShortRepository(shortDao)
        manager = ShortsProcessingManager(
            engine, ProjectRepository(projectDao), shortRepo, TranscriptRepository(FakeTranscriptDao()),
            ExportRepository(exportDao), ExportBatchRepository(batchDao), FakeContext(tmp.newFolder("files")) as Context
        )
        (0 until 3).forEach { i ->
            shortDao.insertSync(
                ShortEntity(projectId = 1, startMs = i * 20_000L, endMs = i * 20_000L + 10_000L, score = 1f,
                    title = "t$i", hook = "h", topic = "x", reason = "r", subtitlesJson = "[]")
            )
        }
    }

    private suspend fun run() = manager.exportBatch(
        projectId, listOf("yt"), "Normal", ResolutionPreset.FULL_HD.label, 30, onProgress = {}
    )

    private fun statuses() = shortDao.items.sortedBy { it.id }.map { it.status }

    @Test fun `segundo item falha - primeiro e terceiro done e lote partial`() = runTest {
        engine.failAtStartMs = 20_000L
        val r = run()
        assertEquals(3, r.total); assertEquals(2, r.done); assertEquals(1, r.failed)
        assertTrue(r.partial)
        assertEquals(listOf("done", "failed", "done"), statuses())
        assertEquals(ExportBatchState.PARTIAL, batchDao.items.single().state)
        assertTrue(shortDao.items.single { it.startMs == 20_000L }.exportError!!.isNotEmpty())
        assertEquals(0, partFiles().size)
    }

    @Test fun `reexecucao com mesmos parametros reaproveita os exports validos`() = runTest {
        run()
        val calls = engine.processCalls
        run()
        assertEquals(calls, engine.processCalls)
        assertEquals(listOf("done", "done", "done"), statuses())
    }

    @Test fun `editar o intervalo gera novo export em vez de reaproveitar`() = runTest {
        run()
        val calls = engine.processCalls
        val edited = shortDao.items.first()
        shortDao.items.replaceAll { if (it.id == edited.id) it.copy(endMs = it.endMs - 2_000L, intervalVersion = 1) else it }
        run()
        assertEquals(calls + 1, engine.processCalls)
    }

    @Test fun `cancelar no meio preserva done e marca o item como cancelled`() = runTest {
        engine.blockAtStartMs = 20_000L
        val job = launch {
            try { run() } catch (_: CancellationException) { }
        }
        advanceUntilIdle()
        assertEquals("done", shortDao.items.single { it.startMs == 0L }.status)
        job.cancelAndJoin()
        assertEquals(listOf("done", "cancelled", "pending"), statuses())
        assertEquals(ExportBatchState.CANCELLED, batchDao.items.single().state)
        assertEquals(0, partFiles().size)
    }

    private fun partFiles() = tmp.root.walkTopDown().filter { it.name.endsWith(".part") }.toList()

    // ---- fakes ----

    private class FakeEngine : VideoEngine {
        var failAtStartMs: Long? = null
        var blockAtStartMs: Long? = null
        var processCalls = 0
        private val durations = HashMap<String, Long>()

        override suspend fun probe(path: String) =
            InputVideoInfo(path, durations[path.removeSuffix(".part")] ?: 120_000L, 1080, 1920, 30.0, true)

        override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) {
            processCalls++
            if (spec.startMs == blockAtStartMs) CompletableDeferred<Unit>().await()
            if (spec.startMs == failAtStartMs) throw IllegalStateException("falha simulada")
            File(spec.outputPath).writeBytes(ByteArray(16) { 1 })
            durations[spec.outputPath.removeSuffix(".part")] = spec.endMs - spec.startMs
            onProgress(1f)
        }

        override suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long) =
            FocusTrack(emptyList(), TrackingMethod.STATIC_CENTER)

        override suspend fun extractAudio(videoPath: String, outputPath: String) = error("n/a")
        override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long): List<String> = error("n/a")
        override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) = error("n/a")
        override suspend fun isAlreadyTargetFormat(path: String, target: ResolutionPreset, fps: Int): Boolean = error("n/a")
    }

    private class FakeProjectDao(private val project: ProjectEntity) : ProjectDao {
        override suspend fun insert(entity: ProjectEntity): Long = error("n/a")
        override suspend fun update(entity: ProjectEntity) = error("n/a")
        override suspend fun getById(id: Long): ProjectEntity? = project.takeIf { it.id == id }
        override fun observeAll(): Flow<List<ProjectEntity>> = MutableStateFlow(listOf(project))
        override suspend fun updateAnalysisState(id: Long, status: String, progress: Float, error: String?, updatedAtMs: Long) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeTranscriptDao : TranscriptDao {
        override suspend fun insert(entity: TranscriptEntity): Long = error("n/a")
        override suspend fun getByProject(projectId: Long): TranscriptEntity? = null
        override suspend fun deleteByProject(projectId: Long) = Unit
    }

    private class FakeShortDao : ShortDao {
        val items = mutableListOf<ShortEntity>()
        private var next = 1L
        fun insertSync(e: ShortEntity) { items += e.copy(id = next++) }
        override suspend fun insert(entity: ShortEntity): Long { insertSync(entity); return next - 1 }
        override fun observeByProject(projectId: Long): Flow<List<ShortEntity>> = MutableStateFlow(items.toList())
        override suspend fun getByProject(projectId: Long) = items.filter { it.projectId == projectId }.sortedBy { it.id }
        override suspend fun getById(id: Long) = items.firstOrNull { it.id == id }
        override suspend fun update(entity: ShortEntity) { items.replaceAll { if (it.id == entity.id) entity else it } }
        override suspend fun updateMetadata(
            id: Long, title: String, hook: String, description: String, hashtags: String, cta: String,
            startMs: Long, endMs: Long, updatedAtMs: Long
        ) = error("n/a")
        override suspend fun updateExportState(
            id: Long, status: String, progress: Float, error: String?, replaceError: Boolean, localPath: String?, updatedAtMs: Long
        ) {
            items.replaceAll {
                if (it.id != id) it else it.copy(
                    status = status, exportProgress = progress,
                    exportError = if (replaceError) error else it.exportError,
                    localPath = localPath ?: it.localPath
                )
            }
        }
        override suspend fun deleteByProject(projectId: Long) = Unit
    }

    private class FakeExportDao : ExportDao {
        val items = mutableListOf<ExportEntity>()
        private var next = 1L
        override suspend fun insert(entity: ExportEntity): Long { items += entity.copy(id = next); return next++ }
        override fun observeByProject(projectId: Long): Flow<List<ExportEntity>> = MutableStateFlow(items.toList())
        override suspend fun getById(id: Long) = items.firstOrNull { it.id == id }
        override suspend fun getLatestForShort(
            projectId: Long, shortId: Long, platform: String, quality: String, resolution: String, fps: Int
        ) = items.filter {
            it.projectId == projectId && it.shortId == shortId && it.platform == platform &&
                it.quality == quality && it.resolution == resolution && it.fps == fps
        }.maxByOrNull { it.id }
        override suspend fun getResumableByProject(projectId: Long) =
            items.filter { it.projectId == projectId && it.status in setOf("pending", "queued", "running", "failed") }
        override suspend fun updateState(
            id: Long, status: String, progress: Float, attemptCount: Int, errorMessage: String?, replaceError: Boolean,
            startedAtMs: Long?, completedAtMs: Long?, resetCompleted: Boolean, outputPath: String?
        ) {
            items.replaceAll {
                if (it.id != id) it else it.copy(
                    status = status, progress = progress, attemptCount = attemptCount,
                    errorMessage = if (replaceError) errorMessage else it.errorMessage,
                    startedAtMs = startedAtMs ?: it.startedAtMs,
                    completedAtMs = if (resetCompleted) null else (completedAtMs ?: it.completedAtMs),
                    outputPath = outputPath ?: it.outputPath
                )
            }
        }
        override suspend fun deleteByShort(shortId: Long) = Unit
    }

    private class FakeBatchDao : ExportBatchDao {
        val items = mutableListOf<ExportBatchEntity>()
        private var next = 1L
        override suspend fun insert(entity: ExportBatchEntity): Long { items += entity.copy(id = next); return next++ }
        override suspend fun update(entity: ExportBatchEntity) { items.replaceAll { if (it.id == entity.id) entity else it } }
        override suspend fun getLatest(projectId: Long) = items.filter { it.projectId == projectId }.maxByOrNull { it.id }
        override fun observeLatest(projectId: Long): Flow<ExportBatchEntity?> = MutableStateFlow(items.lastOrNull())
    }
}
