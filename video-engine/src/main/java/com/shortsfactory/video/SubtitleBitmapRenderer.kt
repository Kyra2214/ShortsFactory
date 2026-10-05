package com.shortsfactory.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.SubtitleStyleConfig
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renderiza cada legenda como PNG transparente (o binário FFmpeg não tem drawtext/libass).
 * `fontSizePx` é definido para largura 1080 e escalado por `targetWidth / 1080`.
 */
internal object SubtitleBitmapRenderer {

    private const val BASE_WIDTH = 1080f
    private const val MAX_TEXT_WIDTH_RATIO = 0.9f

    fun render(
        segments: List<SubtitleSegment>,
        style: SubtitleStyleConfig,
        targetWidth: Int,
        outputDir: File
    ): List<File> {
        outputDir.mkdirs()
        return segments.mapIndexed { index, segment ->
            File(outputDir, String.format(java.util.Locale.ROOT, "sub_%04d.png", index)).also { file ->
                renderOne(segment.words.joinToString(" "), style, targetWidth, file)
            }
        }
    }

    private fun renderOne(text: String, style: SubtitleStyleConfig, targetWidth: Int, file: File) {
        val textSize = style.fontSizePx * targetWidth / BASE_WIDTH
        val maxWidth = (targetWidth * MAX_TEXT_WIDTH_RATIO).toInt().coerceAtLeast(1)
        val outline = if (style.styleKey == "minimal") 0f else textSize / 16f
        val padding = ceil(outline * 2f).toInt() + 4

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            strokeJoin = Paint.Join.ROUND
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        var widest = 0f
        for (line in 0 until layout.lineCount) widest = max(widest, layout.getLineWidth(line))

        val width = ceil(widest).toInt() + padding * 2
        val height = layout.height + padding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.translate(padding - (layout.width - widest) / 2f, padding.toFloat())
            if (outline > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = outline * 2f
                paint.color = Color.argb(204, 0, 0, 0)
                layout.draw(canvas)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(242, 255, 255, 255)
            layout.draw(canvas)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
