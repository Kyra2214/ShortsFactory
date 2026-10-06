package com.shortsfactory.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBatchStateTest {

    @Test
    fun allDoneIsDone() = assertEquals(ExportBatchState.DONE, ExportBatchState.resolve(3, 3, 0))

    @Test
    fun secondFailingIsPartial() = assertEquals(ExportBatchState.PARTIAL, ExportBatchState.resolve(3, 2, 1))

    @Test
    fun noneDoneIsFailed() {
        assertEquals(ExportBatchState.FAILED, ExportBatchState.resolve(3, 0, 3))
        assertEquals(ExportBatchState.FAILED, ExportBatchState.resolve(0, 0, 0))
    }

    @Test
    fun openStates() {
        assertTrue(ExportBatchState.isOpen(ExportBatchState.QUEUED))
        assertTrue(ExportBatchState.isOpen(ExportBatchState.RUNNING))
        assertFalse(ExportBatchState.isOpen(ExportBatchState.PARTIAL))
        assertFalse(ExportBatchState.isOpen(ExportBatchState.CANCELLED))
    }

    @Test
    fun resultFlags() {
        assertTrue(ExportBatchResult(3, 0, 3).allFailed)
        assertTrue(ExportBatchResult(3, 2, 1).partial)
        assertFalse(ExportBatchResult(3, 3, 0).partial)
    }
}
