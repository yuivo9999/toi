package com.toimg.app.domain.model

enum class DateRangeFilter(val label: String) {
    ALL("全部时间"),
    TODAY("今天"),
    WEEK("最近7天"),
    MONTH("最近30天"),
    HALF_YEAR("最近半年"),
    YEAR("最近1年");

    fun getStartAndEndTimestamp(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return when (this) {
            ALL -> Pair(0L, 0L)
            TODAY -> Pair((now - oneDayMs) / 1000, now / 1000)
            WEEK -> Pair((now - 7 * oneDayMs) / 1000, now / 1000)
            MONTH -> Pair((now - 30 * oneDayMs) / 1000, now / 1000)
            HALF_YEAR -> Pair((now - 180 * oneDayMs) / 1000, now / 1000)
            YEAR -> Pair((now - 365 * oneDayMs) / 1000, now / 1000)
        }
    }
}

enum class SizeRangeFilter(val label: String) {
    ALL("全部大小"),
    SMALL("< 1 MB"),
    MEDIUM("1 MB - 5 MB"),
    LARGE("5 MB - 20 MB"),
    HUGE("> 20 MB");

    fun getMinAndMaxBytes(): Pair<Long, Long> {
        val mb = 1024 * 1024L
        return when (this) {
            ALL -> Pair(0L, 0L)
            SMALL -> Pair(0L, 1 * mb)
            MEDIUM -> Pair(1 * mb, 5 * mb)
            LARGE -> Pair(5 * mb, 20 * mb)
            HUGE -> Pair(20 * mb, Long.MAX_VALUE)
        }
    }
}

data class SearchFilterCriteria(
    val query: String = "",
    val folderName: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL,
    val sizeRange: SizeRangeFilter = SizeRangeFilter.ALL,
    val status: String? = null, // null for ALL, "RECOGNIZED", "SCANNED", "FAILED", "MOVED"
    val onlySensitive: Boolean = false,
    val selectedCategory: String? = null
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() ||
                !folderName.isNullOrBlank() ||
                dateRange != DateRangeFilter.ALL ||
                sizeRange != SizeRangeFilter.ALL ||
                !status.isNullOrBlank() ||
                onlySensitive ||
                !selectedCategory.isNullOrBlank()
}
