package com.toimg.app.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDirectoryFilterTest {
    @Test fun excludesThumbnailAndCacheDirectoryNamesCaseInsensitively() {
        listOf(".thumbnails", "THUMBNAILS", ".cache", "Cache", ".trash", "temp", "tmp").forEach {
            assertTrue("expected excluded: $it", ScanDirectoryFilter.isExcludedDirectoryName(it))
        }
    }

    @Test fun doesNotExcludeOrdinaryUserFolders() {
        listOf("DCIM", "Pictures", "Holiday", "My thumbnails collection", "Templates").forEach {
            assertFalse("unexpected exclusion: $it", ScanDirectoryFilter.isExcludedDirectoryName(it))
        }
    }

    @Test fun excludesMediaStorePathsContainingExcludedDirectorySegment() {
        assertTrue(ScanDirectoryFilter.isExcludedPath("DCIM/.thumbnails/"))
        assertTrue(ScanDirectoryFilter.isExcludedPath("/storage/emulated/0/Pictures/cache/image.jpg"))
        assertTrue(ScanDirectoryFilter.isExcludedPath("/storage/emulated/0/Android/data/app/cache/"))
        assertFalse(ScanDirectoryFilter.isExcludedPath("DCIM/Camera/"))
    }

    @Test fun explicitlySelectedExcludedRootIsAllowedButNestedExcludedDirectoriesAreNot() {
        assertFalse(ScanDirectoryFilter.shouldSkipDirectory(".thumbnails", isSelectedRoot = true))
        assertTrue(ScanDirectoryFilter.shouldSkipDirectory(".thumbnails", isSelectedRoot = false))
        assertFalse(ScanDirectoryFilter.shouldSkipDirectory("Pictures", isSelectedRoot = false))
    }
}
