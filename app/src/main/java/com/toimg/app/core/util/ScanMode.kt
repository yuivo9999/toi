package com.toimg.app.core.util

/** 用户可选的增量扫描策略。 */
enum class ScanMode(val persistedValue: String) {
    FAST_INCREMENTAL("FAST_INCREMENTAL"),
    VERIFY_EXISTING("VERIFY_EXISTING");

    companion object {
        fun fromPersisted(value: String?): ScanMode =
            entries.firstOrNull { it.persistedValue == value } ?: FAST_INCREMENTAL
    }
}
