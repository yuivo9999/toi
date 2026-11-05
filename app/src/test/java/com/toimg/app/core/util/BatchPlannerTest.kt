package com.toimg.app.core.util

import com.toimg.app.data.local.entity.ImageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchPlannerTest {

    private fun img(id: String) = ImageEntity(
        id = id, uriString = "content://media/$id", name = "$id.jpg", path = "",
        size = 1L, dateModified = 0L, mimeType = "image/jpeg", folderName = "Camera"
    )

    private fun images(n: Int) = (1..n).map { img("i$it") }

    @Test
    fun splitsIntoBatchesOfRequestedSize() {
        val batches = BatchPlanner.plan(images(23), 10)
        assertEquals(listOf(10, 10, 3), batches.map { it.size })
    }

    @Test
    fun exactMultipleHasNoEmptyTrailingBatch() {
        assertEquals(listOf(5, 5), BatchPlanner.plan(images(10), 5).map { it.size })
    }

    @Test
    fun duplicateIdsAreSentOnlyOnce() {
        val batches = BatchPlanner.plan(listOf(img("a"), img("b"), img("a")), 20)
        assertEquals(listOf("a", "b"), batches.single().map { it.id })
    }

    @Test
    fun preservesOrderAcrossBatches() {
        val ids = BatchPlanner.plan(images(7), 3).flatten().map { it.id }
        assertEquals((1..7).map { "i$it" }, ids)
    }

    @Test
    fun nonPositiveBatchSizeFallsBackToOne() {
        assertEquals(3, BatchPlanner.plan(images(3), 0).size)
        assertEquals(3, BatchPlanner.plan(images(3), -5).size)
    }

    @Test
    fun emptyInputGivesNoBatches() {
        assertTrue(BatchPlanner.plan(emptyList(), 10).isEmpty())
    }
}
