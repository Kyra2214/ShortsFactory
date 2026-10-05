package com.shortsfactory.video

import com.shortsfactory.domain.pipeline.ClipSpec
import com.shortsfactory.domain.pipeline.FocusTrack
import com.shortsfactory.domain.pipeline.FocusPoint
import com.shortsfactory.domain.pipeline.InputVideoInfo
import com.shortsfactory.domain.pipeline.TrackingMethod
import com.shortsfactory.domain.pipeline.VideoEngine
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.SubtitleStyleConfig

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.shortsfactory.core.AssetExtractor

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementação do VideoEngine usando o binário FFmpeg embutido no APK
 * (arm64-v8a, LGPL, com decodificadores MediaCodec/hardware).
 * Todo o processamento ocorre localmente no dispositivo.
 */
class FfmpegVideoEngine constructor(private val appContext: Context) : VideoEngine {

    private val workDir: File by lazy {
        File(appContext.cacheDir, "ffmpeg_work").apply { mkdirs() }
    }

    @Volatile private var runningProcess: Process? = null
    private val mutex = Mutex()
    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
        )
    }

    override fun cancel() {
        runningProcess?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    override fun probe(path: String): InputVideoInfo {
        val output = runBlocking(Dispatchers.IO) { runFfprobe(path) }
        return parseProbeOutput(path, output)
    }

    override suspend fun extractAudio(videoPath: String, outputPath: String) {
        val args = listOf(
            "-y", "-loglevel", "warning",
            "-i", videoPath,
            "-vn", "-acodec", "libmp3lame", "-q:a", "4",
            outputPath
        )
        run(args)
    }

    override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long): List<String> {
        require(chunkDurationMs > 0L) { "A duração do fragmento de áudio deve ser positiva." }
        val dir = File(outputDir).apply { mkdirs() }
        dir.listFiles { file -> file.getName().startsWith("audio_chunk_") }?.forEach(File::delete)
        val pattern = File(dir, "audio_chunk_%03d.mp3").absolutePath
        run(
            listOf(
                "-y", "-loglevel", "warning",
                "-i", audioPath,
                "-f", "segment",
                "-segment_time", (chunkDurationMs / 1_000.0).toString(),
                "-reset_timestamps", "1",
                "-acodec", "libmp3lame", "-q:a", "4",
                pattern
            )
        )
        return dir.listFiles { file ->
            file.getName().startsWith("audio_chunk_") && file.getName().endsWith(".mp3")
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
        require(spec.targetWidth > 0 && spec.targetHeight > 0) { "A resolução alvo deve ser positiva." }
        require(spec.fps in 1..120) { "O FPS deve estar entre 1 e 120." }
        val track = spec.focusTrack

        // crop dinâmico com expressão baseada no tempo (x varia conforme foco)
        val (cropExpr, scaleExpr) = buildCropAndScale(spec.targetWidth, spec.targetHeight, track, durationMs)

        val subtitleFilter = if (spec.subtitles.isNotEmpty()) {
            buildSubtitleDraw(spec.subtitles, spec.subtitleStyle, spec.startMs, durationMs)
        } else null

        val vfParts = mutableListOf(cropExpr, scaleExpr, "fps=${spec.fps}")
        if (subtitleFilter != null) vfParts += subtitleFilter

        val args = listOf(
            "-y", "-loglevel", "warning", "-stats",
            "-ss", (spec.startMs / 1000.0).toString(),
            "-i", spec.inputPath,
            "-t", (durationMs / 1000.0).toString(),
            "-map_metadata", "-1",
            "-vf", vfParts.joinToString(","),
            "-c:v", "libx264", "-preset", "fast",
            "-b:v", spec.bitrateBps.toString(),
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            spec.outputPath
        )
        run(args, onProgress, progressDurationMs = durationMs)
    }

    override suspend fun detectFocusTrack(
        videoPath: String,
        startMs: Long,
        endMs: Long
    ): FocusTrack {
        val points = mutableListOf<FocusPoint>()
        var timeMs = startMs
        val stepMs = 1000L // amostra a cada 1s
        val default = FocusPoint(timeMs = timeMs, centerX = 0.5f, centerY = 0.5f, width = 1f, height = 1f)

        while (timeMs < endMs) {
            currentCoroutineContext().ensureActive()
            val frameFile = File(workDir, "focus_${timeMs}.jpg")
            try {
                run(
                    listOf(
                        "-y", "-loglevel", "error",
                        "-ss", (timeMs / 1000.0).toString(),
                        "-i", videoPath,
                        "-frames:v", "1",
                        frameFile.absolutePath
                    ),
                    timeoutMs = 20_000L
                )
                val center = analyzeFrameCenter(frameFile, timeMs) ?: default.copy(timeMs = timeMs)
                points += center
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao amostrar frame em ${timeMs}ms, usando centro", e)
                points += default
            } finally {
                frameFile.delete()
            }
            timeMs += stepMs
        }
        val finalPoints = smoothFocusPoints(points.ifEmpty { listOf(default) })
        val hasDetectedFace = points.any { it.width < 0.999f || it.height < 0.999f }
        return FocusTrack(
            finalPoints,
            if (hasDetectedFace) TrackingMethod.FACE_TRACKING else TrackingMethod.STATIC_CENTER
        )
    }

    override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) {
        run(
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

    override fun isAlreadyTargetFormat(
        path: String,
        target: com.shortsfactory.domain.model.ResolutionPreset,
        fps: Int
    ): Boolean {
        if (target.width == 0) return false // "original adaptada" sempre converte
        return try {
            val info = probe(path)
            info.width == target.width && info.height == target.height && info.fps.toInt() == fps
        } catch (e: Exception) {
            false
        }
    }

    // ---- Internos ----

    private fun parseProbeOutput(path: String, output: String): InputVideoInfo =
        FfprobeParser.parse(path, output)

    private fun buildCropAndScale(
        targetWidth: Int,
        targetHeight: Int,
        track: FocusTrack?,
        durationMs: Long
    ): Pair<String, String> {
        if (track == null) {
            return Pair(
                "crop=min(iw\\,ih*$targetWidth/$targetHeight):min(ih\\,iw*$targetHeight/$targetWidth):(iw-min(iw\\,ih*$targetWidth/$targetHeight))/2:(ih-min(ih\\,iw*$targetHeight/$targetWidth))/2",
                "scale=$targetWidth:$targetHeight"
            )
        }
        val cropHeightExpr = "min(ih\\,iw*$targetHeight/$targetWidth)"
        val cropWidthExpr = "min(iw\\,ih*$targetWidth/$targetHeight)"
        val durationSec = durationMs / 1000.0
        val points = track.points
        val xExpr = buildInterpolatedXExpression(points, durationSec, cropWidthExpr, horizontal = true)
        val yExpr = buildInterpolatedYExpression(points, durationSec, cropHeightExpr)
        return Pair(
            "crop=$cropWidthExpr:$cropHeightExpr:$xExpr:$yExpr",
            "scale=$targetWidth:$targetHeight"
        )
    }

    private fun buildInterpolatedXExpression(
        points: List<FocusPoint>, durationSec: Double, cropSizeExpr: String, horizontal: Boolean = true
    ): String {
        if (points.isEmpty()) {
            val axis = if (horizontal) "iw" else "ih"
            return "($axis-$cropSizeExpr)*0.5"
        }
        val firstTimeMs = points.first().timeMs
        val axis = if (horizontal) "iw" else "ih"
        val anchors = points.map { point ->
            val center = if (horizontal) point.centerX else point.centerY
            val relativeSec = ((point.timeMs - firstTimeMs).coerceAtLeast(0L) / 1000.0).coerceIn(0.0, durationSec)
            relativeSec to center.coerceIn(0f, 1f)
        }.distinctBy { it.first }
        if (anchors.size == 1) return "($axis-$cropSizeExpr)*" + anchors.first().second
        val expr = StringBuilder("($axis-$cropSizeExpr)*")
        for (i in 1 until anchors.size) {
            val (time, center) = anchors[i]
            val previous = anchors[i - 1]
            val rate = "(" + center + "-" + previous.second + ")/max(0.001\\,(" + time + "-" + previous.first + "))"
            expr.append("if(lt(t\\," + time + ")\\," + previous.second + "+" + rate + "*(t-" + previous.first + ")\\,")
        }
        expr.append(anchors.last().second)
        repeat(anchors.size - 1) { expr.append(")") }
        return expr.toString()
    }

    private fun buildSubtitleDraw(segments: List<SubtitleSegment>, style: SubtitleStyleConfig, clipStartMs: Long, clipDurationMs: Long): String {
        val drawtexts = segments.map { seg ->
            val text = seg.words.joinToString(" ")
                .replace("'", "\\\\'")
                .replace(":", "\\:")
                .replace(",", "\\,")
            val start = ((seg.startMs - clipStartMs).coerceAtLeast(0L) / 1000.0).coerceAtMost(clipDurationMs / 1000.0)
            val end = ((seg.endMs - clipStartMs).coerceAtLeast(0L) / 1000.0).coerceAtMost(clipDurationMs / 1000.0)
            val size = style.fontSizePx
            val y = "h*${style.positionPercent}/100"
            val color = "white@0.95"
            val border = if (style.styleKey == "minimal") "0" else "2"
            "drawtext=text='$text':x=(w-text_w)/2:y=$y:fontsize=$size:fontcolor=$color:borderw=$border:bordercolor=black@0.8:enable='between(t\\,$start\\,$end)'"
        }
        return drawtexts.joinToString(",")
    }

    private fun smoothFocusPoints(points: List<FocusPoint>): List<FocusPoint> {
        if (points.size < 2) return points
        val smoothed = mutableListOf(points.first())
        for (point in points.drop(1)) {
            val previous = smoothed.last()
            val alpha = 0.65f
            smoothed += point.copy(
                centerX = (previous.centerX * (1f - alpha) + point.centerX * alpha).coerceIn(0f, 1f),
                centerY = (previous.centerY * (1f - alpha) + point.centerY * alpha).coerceIn(0f, 1f),
                width = (previous.width * (1f - alpha) + point.width * alpha).coerceIn(0f, 1f),
                height = (previous.height * (1f - alpha) + point.height * alpha).coerceIn(0f, 1f)
            )
        }
        return smoothed
    }

    private suspend fun analyzeFrameCenter(frameFile: File, timeMs: Long): FocusPoint? =
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
            faceDetector.process(image)
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

    private fun ffmpegPath(): String {
        // Binário embutido no APK (jniLibs/arm64-v8a/ffmpeg).
        val bundled = File(appContext.applicationInfo.nativeLibraryDir, "ffmpeg")
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath
        return AssetExtractor.extractIfNeeded(appContext)?.absolutePath
            ?: bundled.absolutePath
    }

    private fun ffprobePath(): String {
        val bundled = File(appContext.applicationInfo.nativeLibraryDir, "ffprobe")
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath
        return AssetExtractor.extractIfNeeded(appContext, "ffprobe")?.absolutePath
            ?: bundled.absolutePath
    }

    private suspend fun run(
        args: List<String>,
        onProgress: (Float) -> Unit = {},
        timeoutMs: Long = TIMEOUT_MS,
        progressDurationMs: Long? = null
    ) = suspendCancellableCoroutine { cont ->
        var process: Process? = null
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                mutex.withLock {
                    val cmd = (listOf(ffmpegPath()) + args).toTypedArray()
                    process = ProcessBuilder(*cmd)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start()
                    runningProcess = process
                    val activeProcess = process ?: error("FFmpeg não foi iniciado.")
                    val reader = async {
                        activeProcess.inputStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val t = REGEX_TIME.find(line)?.groupValues?.get(1)
                                if (t != null) {
                                    val secs = parseHms(t)
                                    val durationSeconds = progressDurationMs?.div(1000.0)
                                    val p = if (durationSeconds != null && durationSeconds > 0.0) {
                                        (secs / durationSeconds).toFloat()
                                    } else {
                                        0f
                                    }.coerceIn(0f, 1f)
                                    onProgress(p)
                                }
                                Log.d(TAG, line.take(200))
                            }
                        }
                    }
                    val waitJob = async(Dispatchers.IO) { activeProcess.waitFor() }
                    val finished = withTimeoutOrNull(timeoutMs) { waitJob.await() }
                    val exit = finished ?: run {
                        activeProcess.destroyForcibly()
                        waitJob.join()
                        -1
                    }
                    reader.await()
                    if (exit == 0) {
                        if (cont.isActive) cont.resume(Unit)
                    } else if (cont.isActive) {
                        cont.resumeWithException(
                            RuntimeException("FFmpeg falhou (exit $exit). Veja os logs do engine.")
                        )
                    }
                }
            } catch (ce: CancellationException) {
                process?.destroy()
                process?.destroyForcibly()
                throw ce
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            } finally {
                if (runningProcess === process) runningProcess = null
            }
        }
        cont.invokeOnCancellation {
            process?.destroy()
            process?.destroyForcibly()
            job.cancel()
        }
    }

    private suspend fun runFfprobe(path: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            ffprobePath(),
            "-v", "quiet",
            "-print_format", "flat",
            "-show_format", "-show_streams",
            path
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "ffprobe falhou (exit $exit)." }
        output
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
        private const val TIMEOUT_MS = 30L * 60 * 1000 // 30 min por operação longa

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
