package com.shortsfactory.video

import com.shortsfactory.domain.pipeline.ClipSpec
import com.shortsfactory.domain.pipeline.FfmpegFilterBuilder
import com.shortsfactory.domain.pipeline.FfmpegCancelledException
import com.shortsfactory.domain.pipeline.FfmpegFailedException
import com.shortsfactory.domain.pipeline.FocusTrack
import com.shortsfactory.domain.pipeline.FocusTrackBuilder
import com.shortsfactory.domain.pipeline.FocusPoint
import com.shortsfactory.domain.pipeline.InputVideoInfo
import com.shortsfactory.domain.pipeline.ProcessRunner
import com.shortsfactory.domain.pipeline.VideoEngine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementação do VideoEngine usando o binário FFmpeg embutido no APK
 * (arm64-v8a, LGPL, com decodificadores MediaCodec/hardware).
 * Todo o processamento ocorre localmente no dispositivo.
 */
class FfmpegVideoEngine constructor(private val appContext: Context) : VideoEngine {

    /** Saída do ffmpeg só é logada em build debuggable; em release nada da saída vai para o logcat. */
    private val logFfmpegOutput: Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val workDir: File by lazy {
        File(appContext.cacheDir, "ffmpeg_work").apply { mkdirs() }
    }

    /** Serializa as execuções de ffmpeg (CPU/memória). Não guarda processo: o dono é o coroutine. */
    private val mutex = Mutex()

    // Não existe cancel() global: cancelar o Job do chamador encerra o processo dele (ver ProcessRunner).

    override suspend fun probe(path: String): InputVideoInfo =
        parseProbeOutput(path, runFfprobe(path))

    override suspend fun extractAudio(videoPath: String, outputPath: String) {
        val args = listOf(
            "-y", "-loglevel", "warning",
            "-i", videoPath,
            "-vn", "-ac", "1", "-c:a", FfmpegCodecs.AUDIO_ENCODER, "-b:a", FfmpegCodecs.ANALYSIS_AUDIO_BITRATE,
            outputPath
        )
        runFfmpeg(args)
    }

    override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long): List<String> {
        require(chunkDurationMs > 0L) { "A duração do fragmento de áudio deve ser positiva." }
        val dir = File(outputDir).apply { mkdirs() }
        dir.listFiles { file -> file.getName().startsWith("audio_chunk_") }?.forEach(File::delete)
        val pattern = File(dir, "audio_chunk_%03d.${FfmpegCodecs.AUDIO_EXTENSION}").absolutePath
        runFfmpeg(
            listOf(
                "-y", "-loglevel", "warning",
                "-i", audioPath,
                "-f", "segment",
                "-segment_time", (chunkDurationMs / 1_000.0).toString(),
                "-reset_timestamps", "1",
                "-c:a", FfmpegCodecs.AUDIO_ENCODER, "-b:a", FfmpegCodecs.ANALYSIS_AUDIO_BITRATE,
                pattern
            )
        )
        return dir.listFiles { file ->
            file.getName().startsWith("audio_chunk_") && file.getName().endsWith(".${FfmpegCodecs.AUDIO_EXTENSION}")
        }
            ?.sortedBy { it.getName() }
            ?.map { it.getAbsolutePath() }
            .orEmpty()
    }

    override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) {
        val outDir = File(spec.outputPath).parentFile ?: workDir
        outDir.mkdirs()

        val durationMs = spec.endMs - spec.startMs
        require(durationMs > 0L) { "O intervalo do clipe deve ser positivo." }
        val graph = FfmpegFilterBuilder.filterGraph(spec)
        if (graph == null) {
            runFfmpeg(
                clipArgs(spec, durationMs, emptyList(), listOf("-vf", FfmpegFilterBuilder.videoFilter(spec))),
                onProgress, progressDurationMs = durationMs
            )
            return
        }
        val subtitleDir = File(workDir, "subtitles_" + System.nanoTime())
        try {
            val images = SubtitleBitmapRenderer.render(graph.subtitles, spec.subtitleStyle, spec.targetWidth, subtitleDir)
            runFfmpeg(
                clipArgs(
                    spec, durationMs, images,
                    listOf("-filter_complex", graph.filterComplex, "-map", "[${graph.outputLabel}]", "-map", "0:a?")
                ),
                onProgress, progressDurationMs = durationMs
            )
        } finally {
            subtitleDir.deleteRecursively()
        }
    }

    private fun clipArgs(
        spec: ClipSpec,
        durationMs: Long,
        extraInputs: List<File>,
        filterArgs: List<String>
    ): List<String> =
        listOf("-y", "-loglevel", "warning", "-stats", "-ss", (spec.startMs / 1000.0).toString(), "-i", spec.inputPath) +
            extraInputs.flatMap { listOf("-i", it.absolutePath) } +
            listOf("-t", (durationMs / 1000.0).toString(), "-map_metadata", "-1") +
            filterArgs +
            listOf(
                "-c:v", FfmpegCodecs.VIDEO_ENCODER,
                "-b:v", spec.bitrateBps.toString(),
                "-c:a", FfmpegCodecs.AUDIO_ENCODER, "-b:a", FfmpegCodecs.AUDIO_BITRATE,
                "-movflags", "+faststart",
                "-f", "mp4",
                spec.outputPath
            )

    override suspend fun detectFocusTrack(
        videoPath: String,
        startMs: Long,
        endMs: Long
    ): FocusTrack {
        val durationMs = endMs - startMs
        if (durationMs <= 0L) return FocusTrackBuilder.build(startMs, emptyList())

        val frameDir = File(workDir, "focus_" + System.nanoTime()).apply { mkdirs() }
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
        )
        try {
            val extracted = try {
                // Uma única chamada: um frame por segundo; o frame de índice i corresponde a startMs + i * 1000.
                runFfmpeg(
                    listOf(
                        "-y", "-loglevel", "error",
                        "-ss", (startMs / 1000.0).toString(),
                        "-i", videoPath,
                        "-t", (durationMs / 1000.0).toString(),
                        "-vf", "fps=1,scale=640:-2",
                        "-q:v", "3",
                        "-start_number", "0",
                        File(frameDir, "frame_%04d.jpg").absolutePath
                    ),
                    timeoutMs = FRAME_SAMPLING_TIMEOUT_MS
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao amostrar frames, usando foco central", e)
                false
            }
            val sampleCount = ((durationMs + FocusTrackBuilder.SAMPLE_STEP_MS - 1) / FocusTrackBuilder.SAMPLE_STEP_MS).toInt()
            val detections: List<FocusPoint?> = if (!extracted) emptyList() else List(sampleCount) { index ->
                val frame = File(frameDir, String.format(java.util.Locale.ROOT, "frame_%04d.jpg", index))
                try {
                    analyzeFrameCenter(detector, frame, startMs + index * FocusTrackBuilder.SAMPLE_STEP_MS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao detectar rosto no frame $index", e)
                    null
                }
            }
            return FocusTrackBuilder.build(startMs, detections)
        } finally {
            detector.close()
            frameDir.deleteRecursively()
        }
    }

    override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) {
        runFfmpeg(
            listOf(
                "-y", "-loglevel", "error",
                "-ss", (timeMs / 1000.0).toString(),
                "-i", videoPath,
                "-frames:v", "1",
                "-q:v", "3",
                outputPath
            ),
            timeoutMs = 30_000L
        )
    }

    override suspend fun isAlreadyTargetFormat(
        path: String,
        target: com.shortsfactory.domain.model.ResolutionPreset,
        fps: Int
    ): Boolean {
        if (target.width == 0) return false // "original adaptada" sempre converte
        return try {
            val info = probe(path)
            info.width == target.width && info.height == target.height && info.fps.toInt() == fps
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // ---- Internos ----

    private fun parseProbeOutput(path: String, output: String): InputVideoInfo =
        FfprobeParser.parse(path, output)

    private suspend fun analyzeFrameCenter(detector: FaceDetector, frameFile: File, timeMs: Long): FocusPoint? =
        suspendCancellableCoroutine { continuation ->
            if (!frameFile.exists()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                bitmap?.recycle()
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    val result = face?.let {
                        val bounds = it.boundingBox
                        FocusPoint(
                            timeMs = timeMs,
                            centerX = (bounds.exactCenterX() / bitmap.width.toFloat()).coerceIn(0f, 1f),
                            centerY = (bounds.exactCenterY() / bitmap.height.toFloat()).coerceIn(0f, 1f),
                            width = (bounds.width() / bitmap.width.toFloat()).coerceIn(0f, 1f),
                            height = (bounds.height() / bitmap.height.toFloat()).coerceIn(0f, 1f)
                        )
                    }
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    private fun ffmpegPath(): String = nativeBinary("libffmpeg.so")

    private fun ffprobePath(): String = nativeBinary("libffprobe.so")

    private fun nativeBinary(fileName: String): String {
        val file = File(appContext.applicationInfo.nativeLibraryDir, fileName)
        check(file.exists()) { "Binário nativo ausente em nativeLibraryDir: $fileName" }
        return file.absolutePath
    }

    /**
     * Executa o ffmpeg preso ao coroutine chamador: cancelar o chamador mata ESTE processo e lança
     * [CancellationException]. Falha de processo vira [FfmpegFailedException] (exit + final do stderr).
     */
    private suspend fun runFfmpeg(
        args: List<String>,
        onProgress: (Float) -> Unit = {},
        timeoutMs: Long = TIMEOUT_MS,
        progressDurationMs: Long? = null
    ) {
        val command = listOf(ffmpegPath()) + args
        val durationSeconds = progressDurationMs?.takeIf { it > 0L }?.div(1000.0)
        val result = mutex.withLock {
            ProcessRunner.run(
                command = command,
                timeoutMs = timeoutMs,
                directory = workDir,
                tailFilter = { line -> !REGEX_TIME.containsMatchIn(line) }
            ) { line ->
                REGEX_TIME.find(line)?.groupValues?.get(1)?.let { time ->
                    val progress = if (durationSeconds != null) (parseHms(time) / durationSeconds).toFloat() else 0f
                    onProgress(progress.coerceIn(0f, 1f))
                }
                if (logFfmpegOutput) Log.d(TAG, line.take(200))
            }
        }
        if (result.exitCode != 0) {
            // O processo pode ter sido morto porque o dono foi cancelado entre o término e o retorno.
            if (!currentCoroutineContext().isActive) throw FfmpegCancelledException()
            throw FfmpegFailedException(result.exitCode, result.tail)
        }
    }

    private suspend fun runFfprobe(path: String): String {
        val output = StringBuilder()
        val result = ProcessRunner.run(
            command = listOf(
                ffprobePath(),
                "-v", "quiet",
                "-print_format", "flat",
                "-show_format", "-show_streams",
                path
            ),
            timeoutMs = PROBE_TIMEOUT_MS
        ) { line -> output.append(line).append('\n') }
        if (result.exitCode != 0) {
            if (!currentCoroutineContext().isActive) throw FfmpegCancelledException()
            throw FfmpegFailedException(result.exitCode, result.tail)
        }
        return output.toString()
    }

    private fun parseHms(hms: String): Double {
        val parts = hms.split(":").mapNotNull { it.toDoubleOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0.0
        }
    }

    companion object {
        private const val TAG = "FfmpegVideoEngine"
        private const val FRAME_SAMPLING_TIMEOUT_MS = 5L * 60 * 1000
        private const val TIMEOUT_MS = 30L * 60 * 1000 // 30 min por operação longa
        private const val PROBE_TIMEOUT_MS = 30L * 1000 // ffprobe não deve levar mais que 30 s

        private val REGEX_TIME = Regex("time=([\\d:.]+)")
    }
}


internal object FfprobeParser {
    private val resolutionRegex = Regex("(\\d{2,5})x(\\d{2,5})")
    private val fpsRegex = Regex("([\\d.]+)\\s*fps")

    fun parse(path: String, output: String): InputVideoInfo {
        val fields = output.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator)
                val value = line.substring(separator + 1).trim().trim('"')
                key to value
            }
            .toMap()

        val durationSeconds = sequenceOf("format.duration", "duration")
            .mapNotNull { fields[it]?.toDoubleOrNull() }
            .firstOrNull() ?: 0.0
        val resolution = fields.entries.asSequence()
            .filter { it.key.endsWith(".width") || it.key.endsWith(".height") }
            .mapNotNull { entry ->
                val width = fields[entry.key.substringBeforeLast('.') + ".width"]?.toIntOrNull()
                val height = fields[entry.key.substringBeforeLast('.') + ".height"]?.toIntOrNull()
                if (width != null && height != null) width to height else null
            }
            .firstOrNull()
            ?: resolutionRegex.find(output)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            ?: (0 to 0)
        val frameRate = sequenceOf("streams.stream.0.avg_frame_rate", "streams.stream.0.r_frame_rate")
            .mapNotNull { fields[it]?.let(::parseRate) }
            .firstOrNull { it > 0.0 }
            ?: fpsRegex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 30.0
        val hasAudio = fields.entries.any { (key, value) ->
            key.endsWith(".codec_type") && value == "audio"
        } || output.contains("codec_type=audio")

        return InputVideoInfo(
            path = path,
            durationMs = (durationSeconds * 1000.0).toLong().coerceAtLeast(0L),
            width = resolution.first,
            height = resolution.second,
            fps = frameRate,
            hasAudio = hasAudio
        )
    }

    private fun parseRate(value: String): Double {
        val parts = value.split('/')
        if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull() ?: return 0.0
            val denominator = parts[1].toDoubleOrNull() ?: return 0.0
            return if (denominator == 0.0) 0.0 else numerator / denominator
        }
        return value.toDoubleOrNull() ?: 0.0
    }
}
