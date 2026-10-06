package com.shortsfactory.domain.export

/** Estados do lote de exportação (`export_batches.state`). */
object ExportBatchState {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val PARTIAL = "partial"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    /** Estado final de um lote que terminou todos os itens. */
    fun resolve(total: Int, done: Int, failed: Int): String = when {
        total <= 0 -> FAILED
        done >= total -> DONE
        done == 0 && failed > 0 -> FAILED
        done > 0 && failed > 0 -> PARTIAL
        else -> FAILED
    }

    fun isOpen(state: String): Boolean = state == QUEUED || state == RUNNING
}
