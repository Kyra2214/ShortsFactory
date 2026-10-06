package com.shortsfactory.domain.ai

import java.io.IOException

/**
 * Falha de IA/transcrição com classificação explícita. O retry NUNCA é decidido por trecho de mensagem:
 * quem lança diz se a falha é transitória ([transient]).
 */
open class AiException(
    message: String,
    val transient: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/** Resposta HTTP de erro de um serviço de IA/transcrição. 408, 429 e 5xx são transitórios. */
class AiHttpException(val code: Int, detail: String = "") : AiException(
    "Serviço de IA retornou HTTP $code" + (if (detail.isBlank()) "." else ": ${detail.take(200)}"),
    transient = code == 408 || code == 429 || code in 500..599
)

/**
 * `true` quando repetir a operação pode dar certo: [AiException] transitória ou [IOException]
 * (rede/timeout) em qualquer ponto da cadeia de causas. Falha de parse, 401/403/404 e validação não são.
 */
fun Throwable.isTransientFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current is AiException && current.transient) return true
        if (current is IOException) return true
        current = current.cause
        depth++
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 8
