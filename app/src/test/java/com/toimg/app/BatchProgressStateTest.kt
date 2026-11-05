package com.toimg.app

import com.toimg.app.domain.model.BatchProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchProgressStateTest {
    @Test
    fun pendingCount_isTotalMinusDone() {
        val p = BatchProgressState(totalCount = 20, completedCount = 7, failedCount = 3)
        assertEquals(10, p.pendingCount)
    }

    @Test
    fun pendingCount_neverNegative() {
        val p = BatchProgressState(totalCount = 5, completedCount = 5, failedCount = 2)
        assertEquals(0, p.pendingCount)
    }
}
