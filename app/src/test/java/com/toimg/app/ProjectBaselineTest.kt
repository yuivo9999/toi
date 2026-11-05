package com.toimg.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectBaselineTest {
    @Test
    fun initialVersionIsZeroPointOne() {
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }
}
