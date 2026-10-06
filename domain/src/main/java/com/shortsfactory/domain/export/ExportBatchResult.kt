package com.shortsfactory.domain.export

/** Resultado de um lote de exportação. `error` preenchido = lote não pôde começar (falha permanente). */
data class ExportBatchResult(
    val total: Int,
    val done: Int,
    val failed: Int,
    val error: String? = null
) {
    val allFailed: Boolean get() = total > 0 && done == 0 && failed > 0
    val partial: Boolean get() = done > 0 && failed > 0
}
