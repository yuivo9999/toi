package com.toimg.app

import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.DateRangeFilter
import com.toimg.app.domain.model.SearchFilterCriteria
import com.toimg.app.domain.model.SizeRangeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    @Test
    fun testDateRangeFilterCalculations() {
        val (allStart, allEnd) = DateRangeFilter.ALL.getStartAndEndTimestamp()
        assertEquals(0L, allStart)
        assertEquals(0L, allEnd)

        val (weekStart, weekEnd) = DateRangeFilter.WEEK.getStartAndEndTimestamp()
        assertTrue(weekEnd > weekStart)
        // 7 days in seconds = 7 * 86400 = 604800
        val diffSeconds = weekEnd - weekStart
        assertEquals(7 * 24 * 3600L, diffSeconds)

        val (monthStart, monthEnd) = DateRangeFilter.MONTH.getStartAndEndTimestamp()
        assertTrue(monthEnd - monthStart >= 30 * 24 * 3600L - 5)
    }

    @Test
    fun testSizeRangeFilterCalculations() {
        val (allMin, allMax) = SizeRangeFilter.ALL.getMinAndMaxBytes()
        assertEquals(0L, allMin)
        assertEquals(0L, allMax)

        val (smallMin, smallMax) = SizeRangeFilter.SMALL.getMinAndMaxBytes()
        assertEquals(0L, smallMin)
        assertEquals(1024 * 1024L, smallMax)

        val (medMin, medMax) = SizeRangeFilter.MEDIUM.getMinAndMaxBytes()
        assertEquals(1024 * 1024L, medMin)
        assertEquals(5 * 1024 * 1024L, medMax)

        val (hugeMin, hugeMax) = SizeRangeFilter.HUGE.getMinAndMaxBytes()
        assertEquals(20 * 1024 * 1024L, hugeMin)
        assertEquals(Long.MAX_VALUE, hugeMax)
    }

    @Test
    fun testSearchFilterCriteriaHasActive() {
        val emptyCriteria = SearchFilterCriteria()
        assertFalse(emptyCriteria.hasActiveFilters)

        val withQuery = SearchFilterCriteria(query = "猫咪")
        assertTrue(withQuery.hasActiveFilters)

        val withDate = SearchFilterCriteria(dateRange = DateRangeFilter.WEEK)
        assertTrue(withDate.hasActiveFilters)

        val withSize = SearchFilterCriteria(sizeRange = SizeRangeFilter.LARGE)
        assertTrue(withSize.hasActiveFilters)

        val withFolder = SearchFilterCriteria(folderName = "Screenshots")
        assertTrue(withFolder.hasActiveFilters)

        val withSensitive = SearchFilterCriteria(onlySensitive = true)
        assertTrue(withSensitive.hasActiveFilters)

        val withStatus = SearchFilterCriteria(status = "RECOGNIZED")
        assertTrue(withStatus.hasActiveFilters)
    }

    @Test
    fun testAiConfigEngineModes() {
        val defaultConfig = AiConfig()
        assertEquals(AiConfig.MODE_HYBRID, defaultConfig.engineMode)
        assertTrue(defaultConfig.isHybridMode)
        assertFalse(defaultConfig.isOfflineMode)

        val offlineConfig = AiConfig(engineMode = AiConfig.MODE_OFFLINE_ONLY)
        assertTrue(offlineConfig.isOfflineMode)
        assertFalse(offlineConfig.isHybridMode)

        val cloudConfig = AiConfig(engineMode = AiConfig.MODE_CLOUD_ONLY)
        assertFalse(cloudConfig.isOfflineMode)
        assertFalse(cloudConfig.isHybridMode)
    }
}
