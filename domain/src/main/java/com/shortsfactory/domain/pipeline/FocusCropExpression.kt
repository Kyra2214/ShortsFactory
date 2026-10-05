package com.shortsfactory.domain.pipeline

import java.util.Locale

/**
 * Gera o filtro `crop` do FFmpeg que recorta o vídeo no formato alvo (ex.: 9:16)
 * seguindo a trilha de foco. É Kotlin puro (sem Android) para poder ser testado na JVM.
 *
 * Convenções:
 * - [FocusPoint.timeMs] é o tempo **absoluto** no vídeo de origem (como produzido por `detectFocusTrack`).
 * - O filtro roda depois de `-ss` antes de `-i`, então a variável `t` do FFmpeg é **relativa ao início do clipe**.
 *   Por isso os pontos são convertidos com `(timeMs - clipStartMs)`.
 * - `centerX`/`centerY` são frações 0..1 do centro do rosto no quadro de origem.
 * - A posição do recorte é `centro * tamanhoDoQuadro - tamanhoDoRecorte / 2`, limitada a `[0, quadro - recorte]`,
 *   de modo que o rosto fique centralizado no recorte e o recorte nunca saia do quadro.
 */
object FocusCropExpression {

    /** Máximo de âncoras na expressão. Evita linhas de comando e aninhamento de `if` excessivos. */
    const val MAX_ANCHORS = 40

    enum class Axis { X, Y }

    /** Ponto de interpolação: tempo em segundos relativo ao clipe e valor (centro) entre 0 e 1. */
    data class Anchor(val timeSec: Double, val value: Double)

    /**
     * Monta `crop=w=..:h=..:x=..:y=..` com as vírgulas já escapadas (`\,`) para uso direto em `-vf`.
     * Sem trilha utilizável, centraliza o recorte.
     */
    fun cropFilter(
        targetWidth: Int,
        targetHeight: Int,
        track: FocusTrack?,
        clipStartMs: Long,
        clipDurationMs: Long
    ): String {
        require(targetWidth > 0 && targetHeight > 0) { "A resolução alvo deve ser positiva." }
        require(clipDurationMs > 0L) { "A duração do clipe deve ser positiva." }

        val width = "min(iw,ih*$targetWidth/$targetHeight)"
        val height = "min(ih,iw*$targetHeight/$targetWidth)"

        val points = track?.points.orEmpty()
        val xAnchors = anchors(points, clipStartMs, clipDurationMs, Axis.X)
        val yAnchors = anchors(points, clipStartMs, clipDurationMs, Axis.Y)

        val x = if (xAnchors.isEmpty()) "(iw-ow)/2" else positionExpression("iw", "ow", centerExpression(xAnchors))
        val y = if (yAnchors.isEmpty()) "(ih-oh)/2" else positionExpression("ih", "oh", centerExpression(yAnchors))

        return "crop=w=${escapeCommas(width)}:h=${escapeCommas(height)}:x=${escapeCommas(x)}:y=${escapeCommas(y)}"
    }

    /**
     * Converte os pontos em âncoras relativas ao clipe: descarta pontos fora do clipe e não finitos,
     * ordena, remove tempos repetidos, reduz para no máximo [MAX_ANCHORS] e, se o primeiro ponto vier
     * depois do início, estende o primeiro valor de volta até t=0.
     */
    fun anchors(
        points: List<FocusPoint>,
        clipStartMs: Long,
        clipDurationMs: Long,
        axis: Axis
    ): List<Anchor> {
        val durationSec = clipDurationMs / 1000.0
        val inRange = points.asSequence()
            .map { point ->
                val raw = if (axis == Axis.X) point.centerX else point.centerY
                Anchor((point.timeMs - clipStartMs) / 1000.0, raw.toDouble())
            }
            .filter { it.timeSec.isFinite() && it.value.isFinite() }
            .filter { it.timeSec >= 0.0 && it.timeSec <= durationSec }
            .map { it.copy(value = it.value.coerceIn(0.0, 1.0)) }
            .sortedBy { it.timeSec }
            .distinctBy { it.timeSec }
            .toList()
        if (inRange.isEmpty()) return emptyList()

        val reduced = decimate(inRange, MAX_ANCHORS)
        return if (reduced.first().timeSec > 0.0) {
            listOf(Anchor(0.0, reduced.first().value)) + reduced
        } else {
            reduced
        }
    }

    /** Expressão do centro (0..1) em função de `t`: interpolação linear por trechos, constante após a última âncora. */
    fun centerExpression(anchors: List<Anchor>): String {
        require(anchors.isNotEmpty()) { "São necessárias ao menos uma âncora." }
        if (anchors.size == 1 || anchors.all { kotlin.math.abs(it.value - anchors.first().value) < EPSILON }) {
            return number(anchors.first().value)
        }

        val builder = StringBuilder()
        for (i in 0 until anchors.size - 1) {
            val from = anchors[i]
            val to = anchors[i + 1]
            val slope = (to.value - from.value) / (to.timeSec - from.timeSec)
            val local = if (from.timeSec == 0.0) "t" else "(t-${number(from.timeSec)})"
            val segment = if (kotlin.math.abs(slope) < EPSILON) {
                number(from.value)
            } else {
                "${number(from.value)}+${signed(slope)}*$local"
            }
            builder.append("if(lt(t,${number(to.timeSec)}),$segment,")
        }
        builder.append(number(anchors.last().value))
        repeat(anchors.size - 1) { builder.append(')') }
        return builder.toString()
    }

    /** `max(0,min(quadro-recorte, centro*quadro - recorte/2))`. */
    private fun positionExpression(frame: String, crop: String, center: String): String =
        "max(0,min($frame-$crop,($center)*$frame-$crop/2))"

    /** Vírgulas dentro de expressões precisam de `\,` no filtergraph do FFmpeg. */
    fun escapeCommas(expression: String): String = expression.replace(",", "\\,")

    private fun decimate(list: List<Anchor>, max: Int): List<Anchor> {
        if (list.size <= max) return list
        return (0 until max).map { i ->
            val index = Math.round(i.toDouble() * (list.size - 1) / (max - 1)).toInt()
            list[index]
        }.distinctBy { it.timeSec }
    }

    /** Número decimal independente de locale, sem notação científica, sem zeros à direita. */
    internal fun number(value: Double): String {
        if (kotlin.math.abs(value) < 0.00005) return "0"
        val text = String.format(Locale.ROOT, "%.4f", value)
        return text.trimEnd('0').trimEnd('.')
    }

    /** Número com sinal seguro para somar: negativos vão entre parênteses. */
    private fun signed(value: Double): String {
        val text = number(value)
        return if (text.startsWith("-")) "($text)" else text
    }

    private const val EPSILON = 0.00005
}
