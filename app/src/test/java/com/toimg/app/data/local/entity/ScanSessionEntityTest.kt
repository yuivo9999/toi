package com.toimg.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionEntityTest {
    @Test
    fun folderUriList_preservesEachPersistedSource() {
        val session = ScanSessionEntity(
            id = "test",
            status = ScanSessionEntity.RUNNING,
            includeMediaStore = true,
            folderUris = "content://tree/one\ncontent://tree/two\n",
            includeSubfolders = false,
            startedAt = 1L,
            updatedAt = 2L
        )
        assertEquals(listOf("content://tree/one", "content://tree/two"), session.folderUriList())
    }

    @Test
    fun onlyQueuedRunningAndPausedAreActive() {
        assertTrue(ScanSessionEntity.ACTIVE.contains(ScanSessionEntity.QUEUED))
        assertTrue(ScanSessionEntity.ACTIVE.contains(ScanSessionEntity.RUNNING))
        assertTrue(ScanSessionEntity.ACTIVE.contains(ScanSessionEntity.PAUSED))
        assertFalse(ScanSessionEntity.ACTIVE.contains(ScanSessionEntity.SUCCESS))
        assertFalse(ScanSessionEntity.ACTIVE.contains(ScanSessionEntity.CANCELLED))
    }
}
