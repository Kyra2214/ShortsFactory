package com.shortsfactory.app

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.shortsfactory.domain.pipeline.ProcessRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Executa os binários embutidos. Exige aparelho arm64: `./gradlew :app:connectedDebugAndroidTest`. */
class FfmpegBinaryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nativeDir get() = context.applicationInfo.nativeLibraryDir

    @Before
    fun requireArm64() {
        assumeTrue("Requer arm64-v8a", Build.SUPPORTED_ABIS.contains("arm64-v8a"))
    }

    private fun run(binary: String, vararg args: String, timeoutSec: Long = 60): Pair<Int, String> {
        val file = File(nativeDir, binary)
        assertTrue("$binary ausente em nativeLibraryDir", file.exists())
        val process = ProcessBuilder(listOf(file.absolutePath) + args)
            .directory(context.cacheDir)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("$binary excedeu ${timeoutSec}s")
        }
        return process.exitValue() to output
    }

    @Test
    fun ffmpegVersionRuns() {
        val (code, out) = run("libffmpeg.so", "-version")
        assertEquals(out, 0, code)
        assertTrue(out, out.startsWith("ffmpeg version"))
    }

    @Test
    fun ffprobeVersionRuns() {
        val (code, out) = run("libffprobe.so", "-version")
        assertEquals(out, 0, code)
        assertTrue(out, out.startsWith("ffprobe version"))
    }

    @Test
    fun encodersUsedByCodeArePresent() {
        val (code, out) = run("libffmpeg.so", "-hide_banner", "-encoders")
        assertEquals(out, 0, code)
        listOf("h264_mediacodec", "aac").forEach { name ->
            assertTrue("Encoder ausente: $name", Regex("""^\s*[AV]\S*\s+$name\s""", RegexOption.MULTILINE).containsMatchIn(out))
        }
    }

    @Test
    fun filtersUsedByCodeArePresent() {
        val (code, out) = run("libffmpeg.so", "-hide_banner", "-filters")
        assertEquals(out, 0, code)
        listOf("crop", "scale", "fps").forEach { name ->
            assertTrue("Filtro ausente: $name", Regex("""^\s*\S+\s+$name\s""", RegexOption.MULTILINE).containsMatchIn(out))
        }
    }

    @Test
    fun generatesTwoSecondClip() {
        val outFile = File(context.cacheDir, "fase1_clip.mp4").apply { delete() }
        val (code, out) = run(
            "libffmpeg.so", "-y", "-loglevel", "error", "-nostdin",
            "-f", "lavfi", "-i", "testsrc=duration=2:size=720x1280:rate=30",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "h264_mediacodec", "-b:v", "2000000",
            "-c:a", "aac", "-b:a", "128k", "-shortest",
            outFile.absolutePath
        )
        assertEquals(out, 0, code)
        assertTrue(out, outFile.length() > 0L)

        val (probeCode, probeOut) = run(
            "libffprobe.so", "-v", "error",
            "-show_entries", "stream=codec_type,codec_name,width,height:format=duration",
            "-of", "default=nw=1", outFile.absolutePath
        )
        assertEquals(probeOut, 0, probeCode)
        assertTrue(probeOut, probeOut.contains("codec_name=h264"))
        assertTrue(probeOut, probeOut.contains("codec_name=aac"))
        assertTrue(probeOut, probeOut.contains("width=720") && probeOut.contains("height=1280"))
        val duration = Regex("""duration=([0-9.]+)""").find(probeOut)!!.groupValues[1].toDouble()
        assertTrue("Duração fora de 2s: $duration", duration in 1.8..2.3)
        outFile.delete()
    }

    /** Fase 3: cancelar o coroutine dono mata o ffmpeg real (PID some em < 2 s) e lança cancelamento. */
    @Test
    fun cancellingTheOwnerKillsTheRealFfmpegProcessWithinTwoSeconds() = runBlocking {
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        assertTrue("libffmpeg.so ausente em nativeLibraryDir", ffmpeg.exists())
        val started = CompletableDeferred<Process>()
        val job = launch(Dispatchers.Default) {
            // Fonte infinita (sem duration): só termina se for morto.
            ProcessRunner.run(
                command = listOf(
                    ffmpeg.absolutePath, "-y", "-loglevel", "error", "-nostdin",
                    "-re", "-f", "lavfi", "-i", "testsrc=size=640x360:rate=30",
                    "-f", "null", "-"
                ),
                timeoutMs = 120_000L,
                directory = context.cacheDir,
                onStart = { started.complete(it) }
            )
        }
        val process = withTimeout(10_000L) { started.await() }
        assertTrue("o ffmpeg deveria estar rodando", process.isAlive)
        Thread.sleep(500)

        job.cancel()
        withTimeout(2_000L) { job.join() }

        assertTrue("o PID do ffmpeg deveria sumir em < 2 s", process.waitFor(2, TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
    }
}
