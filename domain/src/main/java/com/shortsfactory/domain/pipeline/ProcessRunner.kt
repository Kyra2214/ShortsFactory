package com.shortsfactory.domain.pipeline

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executa um processo externo (ffmpeg/ffprobe) preso ao ciclo de vida do coroutine chamador.
 *
 * Garantias:
 * - O processo pertence ao coroutine que chamou [run]: cancelar esse coroutine (ou o `Job` pai) mata
 *   ESSE processo (`destroyForcibly`) e nenhum outro. Não há estado global nem `cancel()` compartilhado.
 * - Cancelamento propaga como `CancellationException`; nunca vira [FfmpegFailedException].
 * - Falha/tempo limite viram [FfmpegFailedException] com o final da saída (≤ ~2 KB).
 * - Em qualquer saída (sucesso, erro, tempo limite, cancelamento) o processo não fica vivo.
 */
object ProcessRunner {

    /** Resultado de um processo que terminou por conta própria. */
    class Result(val exitCode: Int, val tail: String)

    /**
     * @param onStart chamado logo após o processo iniciar (permite observar o PID/vida do processo).
     * @param tailFilter linhas para as quais retorna `false` não entram no [Result.tail]
     *   (ex.: linhas de progresso do `-stats`).
     * @param onLine chamado para cada linha de saída (stdout+stderr), numa thread de IO.
     */
    suspend fun run(
        command: List<String>,
        timeoutMs: Long,
        directory: File? = null,
        onStart: (Process) -> Unit = {},
        tailFilter: (String) -> Boolean = { true },
        onLine: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw FfmpegFailedException(-1, e.message.orEmpty().take(FfmpegFailedException.MAX_TAIL_CHARS))
        }
        val tail = OutputTail(FfmpegFailedException.MAX_TAIL_CHARS)
        try {
            onStart(process)
            coroutineScope {
                val reader = launch {
                    try {
                        process.inputStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (tailFilter(line)) tail.add(line)
                                onLine(line)
                            }
                        }
                    } catch (e: IOException) {
                        // O fluxo é fechado quando o processo é encerrado (cancelamento/tempo limite).
                        // Não deve virar falha: uma exceção real aqui substituiria o CancellationException.
                    }
                }
                try {
                    val exit = withTimeoutOrNull(timeoutMs) { runInterruptible { process.waitFor() } }
                    if (exit == null) {
                        process.destroyForcibly()
                        reader.join()
                        throw FfmpegFailedException(-1, tail.text(), timedOut = true)
                    }
                    reader.join()
                    Result(exit, tail.text())
                } finally {
                    // Cancelamento, erro ou tempo limite: o processo nunca sobrevive ao coroutine dono.
                    // Precisa ocorrer antes de o coroutineScope esperar o reader (que só termina com o EOF).
                    if (process.isAlive) process.destroyForcibly()
                }
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

/** Mantém apenas o final da saída, limitado a [maxChars] caracteres. */
internal class OutputTail(private val maxChars: Int) {
    private val lines = ArrayDeque<String>()
    private var size = 0

    @Synchronized
    fun add(line: String) {
        val trimmed = if (line.length > maxChars) line.takeLast(maxChars) else line
        lines.addLast(trimmed)
        size += trimmed.length + 1
        while (size > maxChars && lines.size > 1) {
            size -= lines.removeFirst().length + 1
        }
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")
}
