package com.toimg.app.core.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** 云端识别重试策略。NVIDIA 429 缺少 Retry-After 时使用更保守的冷却。 */
object RetryPolicy {
    const val MAX_HTTP_RETRIES = 2
    const val MAX_AUTO_RETRY_ROUNDS = 1
    const val BASE_DELAY_MS = 15_000L
    const val MAX_DELAY_MS = 120_000L
    const val RATE_LIMIT_FALLBACK_DELAY_MS = 60_000L
    const val ROUND_COOLDOWN_MS = 25_000L

    fun isRetryableHttp(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    /** Retry-After 可为秒数或 RFC 1123 日期；返回值不做上限截断，必须尊重服务端冷却。 */
    fun parseRetryAfter(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        raw.toLongOrNull()?.let { seconds ->
            val safeSeconds = seconds.coerceAtLeast(0L)
            return if (safeSeconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else safeSeconds * 1000L
        }
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }
            (format.parse(raw)?.time?.minus(nowMillis))?.coerceAtLeast(0L)
        } catch (_: Exception) { null }
    }

    fun backoffMs(attempt: Int, retryAfterMs: Long? = null, random: Random = Random.Default): Long {
        val exp = BASE_DELAY_MS * (1L shl attempt.coerceIn(0, 3))
        val base = minOf(exp, MAX_DELAY_MS)
        val jitter = random.nextLong(0L, 1_000L)
        return maxOf(base + jitter, retryAfterMs ?: 0L)
    }
}
