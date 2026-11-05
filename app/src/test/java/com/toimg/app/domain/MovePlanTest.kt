package com.toimg.app.domain

import com.toimg.app.domain.model.ConflictStrategy
import com.toimg.app.domain.model.MovePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MovePlanTest {

    @Test
    fun emptyPlan_cannotExecute() {
        val plan = MovePlan()
        assertEquals(0, plan.totalCount)
        assertFalse(plan.canExecute)
    }

    @Test
    fun defaultConflictStrategy_keepsBothFiles() {
        assertEquals(ConflictStrategy.RENAME_NEW, MovePlan().conflictStrategy)
    }
}
