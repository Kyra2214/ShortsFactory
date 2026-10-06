package com.shortsfactory.domain.pipeline

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Processos REAIS (`sh`/`sleep`): é o que prova que cancelar mata o processo do dono.
 * Usamos `exec` para o `sh` ser substituído pelo programa (sem processo filho órfão), como o ffmpeg.
 */
class ProcessRunnerTest {

    @Before
    fun requiresPosix() {
        assumeTrue(!System.getProperty("os.name").orEmpty().startsWith("Windows"))
    }

    private fun sh(script: String) = listOf("sh", "-c", script)

    @Test
    fun `cancelar o coroutine mata o processo em menos de 2 segundos`() = runBlocking {
        val started = CompletableDeferred<Process>()
        val job = launch(Dispatchers.Default) {
            ProcessRunner.run(sh("exec sleep 30"), timeoutMs = 60_000, onStart = { started.complete(it) })
        }
        val process = withTimeout(5_000) { started.await() }
        assertTrue(process.isAlive)

        val began = System.nanoTime()
        job.cancel()
        withTimeout(2_000) { job.join() }

        assertTrue("o processo deveria estar morto", process.waitFor(2, TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
        assertTrue("levou ${(System.nanoTime() - began) / 1_000_000} ms", System.nanoTime() - began < 2_000_000_000L)
    }

    @Test
    fun `cancelar um processo nao mata o de outro dono`() = runBlocking {
        val startedA = CompletableDeferred<Process>()
        val startedB = CompletableDeferred<Process>()
        val jobA = launch(Dispatchers.Default) {
            ProcessRunner.run(sh("exec sleep 30"), 60_000, onStart = { startedA.complete(it) })
        }
        val jobB = launch(Dispatchers.Default) {
            ProcessRunner.run(sh("exec sleep 30"), 60_000, onStart = { startedB.complete(it) })
        }
        val a = withTimeout(5_000) { startedA.await() }
        val b = withTimeout(5_000) { startedB.await() }

        jobA.cancel()
        withTimeout(2_000) { jobA.join() }

        assertTrue("o processo cancelado deveria terminar", a.waitFor(2, TimeUnit.SECONDS))
        assertTrue("o processo do outro dono deve continuar vivo", !b.waitFor(250, TimeUnit.MILLISECONDS))
        jobB.cancel()
        withTimeout(2_000) { jobB.join() }
        assertTrue(b.waitFor(2, TimeUnit.SECONDS))
    }

    @Test
    fun `cancelamento lanca CancellationException e nunca FfmpegFailedException`() = runBlocking {
        val started = CompletableDeferred<Process>()
        var thrown: Throwable? = null
        val job = launch(Dispatchers.Default) {
            try {
                ProcessRunner.run(sh("exec sleep 30"), 60_000, onStart = { started.complete(it) })
            } catch (e: Throwable) {
                thrown = e
                throw e
            }
        }
        withTimeout(5_000) { started.await() }
        job.cancel()
        withTimeout(2_000) { job.join() }

        assertTrue("era ${thrown?.javaClass}", thrown is CancellationException)
        assertFalse(thrown is FfmpegFailedException)
    }

    @Test
    fun `tempo limite mata o processo e lanca FfmpegFailedException com timedOut`() = runBlocking {
        val started = CompletableDeferred<Process>()
        try {
            ProcessRunner.run(sh("echo preparando; exec sleep 30"), timeoutMs = 2_000, onStart = { started.complete(it) })
            fail("deveria ter estourado o tempo")
        } catch (e: FfmpegFailedException) {
            assertTrue(e.timedOut)
            assertEquals(-1, e.exitCode)
            assertTrue("tail de timeout não deveria ser vazio", e.stderrTail.isNotEmpty())
        }
        assertTrue("o processo em timeout deveria terminar", started.await().waitFor(2, TimeUnit.SECONDS))
    }

    @Test
    fun `saida diferente de zero devolve codigo e final do stderr sem lancar`() = runBlocking {
        val result = ProcessRunner.run(sh("echo aviso; echo erro-fatal >&2; exit 3"), timeoutMs = 5_000)
        assertEquals(3, result.exitCode)
        assertTrue(result.tail.contains("erro-fatal"))
    }

    @Test
    fun `o final da saida e limitado e mantem as ultimas linhas`() = runBlocking {
        val result = ProcessRunner.run(
            sh("i=1; while [ \$i -le 500 ]; do echo linha-numero-\$i-xxxxxxxxxxxxxxxxxxxxxxxx; i=\$((i+1)); done"),
            timeoutMs = 10_000
        )
        assertEquals(0, result.exitCode)
        assertTrue("tail com ${result.tail.length} chars", result.tail.length <= FfmpegFailedException.MAX_TAIL_CHARS)
        assertTrue(result.tail.contains("linha-numero-500-"))
        assertFalse(result.tail.contains("linha-numero-1-"))
    }

    @Test
    fun `tailFilter exclui linhas de progresso do final mas onLine as recebe`() = runBlocking {
        val lines = mutableListOf<String>()
        val result = ProcessRunner.run(
            sh("echo 'time=00:00:01.00'; echo erro-real >&2"),
            timeoutMs = 5_000,
            tailFilter = { !it.contains("time=") }
        ) { lines += it }
        assertEquals(listOf("time=00:00:01.00", "erro-real"), lines)
        assertEquals("erro-real", result.tail)
    }

    @Test
    fun `binario inexistente vira FfmpegFailedException`() = runBlocking {
        try {
            ProcessRunner.run(listOf("/definitivamente/inexistente"), timeoutMs = 1_000)
            fail("deveria falhar ao iniciar")
        } catch (e: FfmpegFailedException) {
            assertEquals(-1, e.exitCode)
            assertFalse(e.timedOut)
        }
    }
}
