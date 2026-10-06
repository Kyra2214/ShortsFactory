package com.shortsfactory.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.SubtitleEntity
import com.shortsfactory.data.repository.ProjectStore
import com.shortsfactory.data.repository.ShortUpdateResult
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fase 6 em Room real (em memória): re-análise, edição com invalidação, cascata e atomicidade. */
@RunWith(AndroidJUnit4::class)
class ProjectStoreInstrumentedTest {

    private lateinit var db: ShortsDatabase
    private lateinit var store: ProjectStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ShortsDatabase::class.java).build()
        store = ProjectStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun candidate(start: Long, end: Long, title: String) = ShortCandidate(
        score = 0.8f, startMs = start, endMs = end, title = title, hook = "h", topic = "t", reason = "r",
        subtitles = listOf(SubtitleSegment(0L, 1_000L, listOf("oi")))
    )

    private val transcript = Transcript(listOf(TranscriptSegment(0L, 60_000L, "fala")))
    private fun analysis(vararg c: ShortCandidate) = AIAnalysisResult("t", "s", c.toList())

    private suspend fun newProject(durationMs: Long = 100_000L): Long = db.projectDao().insert(
        ProjectEntity(name = "p", videoUri = "u", videoName = "v", videoDurationMs = durationMs,
            videoWidth = 1920, videoHeight = 1080, videoSizeBytes = 1, sourceType = "gallery")
    )

    @Test
    fun reanalysisReplacesCandidatesInsteadOfDuplicating() = runBlocking {
        val pid = newProject()
        val first = listOf(candidate(0, 20_000, "A"), candidate(30_000, 50_000, "B"))
        store.saveAnalysis(pid, transcript, "x", analysis(*first.toTypedArray()), first)
        assertEquals(2, db.shortDao().getByProject(pid).size)

        val second = listOf(candidate(10_000, 30_000, "C"))
        store.saveAnalysis(pid, transcript, "y", analysis(*second.toTypedArray()), second)

        val shorts = db.shortDao().getByProject(pid)
        assertEquals(listOf("C"), shorts.map { it.title })
        assertNotNull(shorts.single().subtitlesJson)
        assertEquals("y", db.aiAnalysisDao().getByProject(pid)?.provider)
        assertEquals("done", db.projectDao().getById(pid)?.analysisStatus)
    }

    @Test
    fun reanalysisRemovesDependentSubtitlesAndExportsOfOldShorts() = runBlocking {
        val pid = newProject()
        store.saveAnalysis(pid, transcript, "x", analysis(candidate(0, 20_000, "A")), listOf(candidate(0, 20_000, "A")))
        val oldShort = db.shortDao().getByProject(pid).single().id
        db.subtitleDao().insert(SubtitleEntity(shortId = oldShort, json = "[]"))
        db.exportDao().insert(ExportEntity(projectId = pid, shortId = oldShort, platform = "yt", quality = "Normal", resolution = "r", fps = 30))

        store.saveAnalysis(pid, transcript, "x", analysis(candidate(40_000, 60_000, "B")), listOf(candidate(40_000, 60_000, "B")))

        assertNull(db.subtitleDao().getByShort(oldShort))
        assertEquals(0, db.query("SELECT COUNT(*) FROM exports WHERE shortId = $oldShort", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun failedSaveLeavesPreviousAnalysisIntact() = runBlocking {
        val pid = newProject()
        store.saveAnalysis(pid, transcript, "x", analysis(candidate(0, 20_000, "A")), listOf(candidate(0, 20_000, "A")))

        // projectId inexistente: a FK do primeiro insert falha e a transação inteira volta atrás.
        val failure = runCatching {
            store.saveAnalysis(9999L, transcript, "x", analysis(candidate(0, 20_000, "Z")), listOf(candidate(0, 20_000, "Z")))
        }
        assertTrue(failure.isFailure)
        assertEquals(listOf("A"), db.shortDao().getByProject(pid).map { it.title })
    }

    @Test
    fun deletingProjectCleansEveryTable() = runBlocking {
        val pid = newProject()
        store.saveAnalysis(pid, transcript, "x", analysis(candidate(0, 20_000, "A")), listOf(candidate(0, 20_000, "A")))
        val sid = db.shortDao().getByProject(pid).single().id
        db.subtitleDao().insert(SubtitleEntity(shortId = sid, json = "[]"))
        db.exportDao().insert(ExportEntity(projectId = pid, shortId = sid, platform = "yt", quality = "Normal", resolution = "r", fps = 30))

        db.projectDao().delete(pid)

        listOf("shorts", "transcripts", "ai_analyses", "subtitles", "exports").forEach { table ->
            assertEquals(table, 0, db.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test
    fun editingTheIntervalInvalidatesThePreviousExport() = runBlocking {
        val pid = newProject()
        store.saveAnalysis(pid, transcript, "x", analysis(candidate(0, 20_000, "A")), listOf(candidate(0, 20_000, "A")))
        val sid = db.shortDao().getByProject(pid).single().id
        db.shortDao().updateExportState(sid, "done", 1f, null, true, "/exports/a.mp4", 1L)
        db.exportDao().insert(ExportEntity(projectId = pid, shortId = sid, platform = "yt", quality = "Normal", resolution = "r", fps = 30, status = "done"))

        val result = store.updateShort(sid, "novo", "novo gancho", "d", "#h", "cta", 5_000L, 25_000L)
        assertEquals(ShortUpdateResult.Saved(intervalChanged = true), result)

        val short = db.shortDao().getById(sid)!!
        assertEquals(5_000L, short.startMs)
        assertEquals(25_000L, short.endMs)
        assertEquals("novo gancho", short.hook)
        assertNull(short.localPath)
        assertEquals("pending", short.status)
        assertEquals(0f, short.exportProgress, 0f)
        assertEquals(1, short.intervalVersion)
        assertNull(short.subtitlesJson)
        assertNull(short.focusTrackJson)
        assertEquals(0, db.query("SELECT COUNT(*) FROM exports WHERE shortId = $sid", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun editingOnlyMetadataKeepsTheExport() = runBlocking {
        val pid = newProject()
        store.saveAnalysis(pid, transcript, "x", analysis(candidate(0, 20_000, "A")), listOf(candidate(0, 20_000, "A")))
        val sid = db.shortDao().getByProject(pid).single().id
        db.shortDao().updateExportState(sid, "done", 1f, null, true, "/exports/a.mp4", 1L)

        val result = store.updateShort(sid, "só título", "h", "d", "#h", "cta", 0L, 20_000L)
        assertEquals(ShortUpdateResult.Saved(intervalChanged = false), result)

        val short = db.shortDao().getById(sid)!!
        assertEquals("só título", short.title)
        assertEquals("/exports/a.mp4", short.localPath)
        assertEquals("done", short.status)
        assertEquals(0, short.intervalVersion)
        assertNotNull(short.subtitlesJson)
    }

    @Test
    fun invalidIntervalsAreRejectedAndNothingIsWritten() = runBlocking {
        val pid = newProject(durationMs = 100_000L)
        val cands = listOf(candidate(0, 20_000, "A"), candidate(40_000, 60_000, "B"))
        store.saveAnalysis(pid, transcript, "x", analysis(*cands.toTypedArray()), cands)
        val a = db.shortDao().getByProject(pid).first { it.title == "A" }

        listOf(
            -1L to 10_000L,            // início negativo
            10_000L to 10_000L,        // fim não maior que o início
            90_000L to 100_001L,       // além do vídeo
            0L to 2_000L,              // curto demais
            0L to 95_000L,             // acima do teto
            30_000L to 50_000L         // sobrepõe B
        ).forEach { (s, e) ->
            val r = store.updateShort(a.id, "x", "h", "d", "t", "c", s, e)
            assertTrue("$s..$e deveria ser recusado: $r", r is ShortUpdateResult.Rejected)
        }
        val unchanged = db.shortDao().getById(a.id)!!
        assertEquals(0L, unchanged.startMs)
        assertEquals(20_000L, unchanged.endMs)
        assertEquals("A", unchanged.title)
        assertEquals(ShortUpdateResult.NotFound, store.updateShort(424242L, "x", "h", "d", "t", "c", 0L, 10_000L))
    }
}
