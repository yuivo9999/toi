package com.toimg.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RetryPolicyTest {
    @Test fun backoffGrowsConservatively() {
        val r = Random(42)
        assertTrue(RetryPolicy.backoffMs(0, null, r) in 15_000L until 16_000L)
        assertTrue(RetryPolicy.backoffMs(1, null, r) in 30_000L until 31_000L)
        assertTrue(RetryPolicy.backoffMs(2, null, r) in 60_000L until 61_000L)
    }

    @Test fun retryAfterIsNeverCappedOrShortened() {
        assertTrue(RetryPolicy.backoffMs(0, 30_000L, Random(1)) >= 30_000L)
        assertTrue(RetryPolicy.backoffMs(0, 10 * 60_000L, Random(1)) >= 10 * 60_000L)
    }

    @Test fun retryAfterAcceptsSecondsAndRejectsInvalidValues() {
        assertEquals(45_000L, RetryPolicy.parseRetryAfter("45", 0L))
        assertEquals(null, RetryPolicy.parseRetryAfter("not-a-date", 0L))
    }

    @Test fun retryableHttpCodes() {
        assertTrue(RetryPolicy.isRetryableHttp(429))
        assertTrue(RetryPolicy.isRetryableHttp(408))
        assertTrue(RetryPolicy.isRetryableHttp(500))
        assertTrue(RetryPolicy.isRetryableHttp(503))
        assertFalse(RetryPolicy.isRetryableHttp(400))
        assertFalse(RetryPolicy.isRetryableHttp(401))
        assertFalse(RetryPolicy.isRetryableHttp(404))
        assertFalse(RetryPolicy.isRetryableHttp(200))
    }
}
