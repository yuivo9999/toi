package com.toimg.app.data.remote

import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.model.TestConnectionResult
import com.toimg.app.core.util.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ImagePayload(
    val imageId: String,
    val base64Jpeg: String,
    val fileName: String
)

internal data class BatchIdValidation(
    val missingIds: Set<String>,
    val duplicateIds: Set<String>,
    val unknownIds: Set<String>
)

class AiVisionClient {

    /**
     * Clipboard/API-key fields may contain CR/LF (or other whitespace) from wrapped
     * copy operations. HTTP Authorization headers reject control characters.
     * API tokens are opaque and do not contain meaningful whitespace.
     */
    internal fun normalizeApiKey(value: String): String =
        value.filterNot { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }

    internal val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
        .build()

    // 1x1 transparent PNG base64 for vision API test payload
    private val sampleBase64Png = "iVBORw0KGgoAAAANSU2EUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    suspend fun testConnection(config: AiConfig): TestConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (normalizeApiKey(config.apiKey).isBlank()) {
            return@withContext TestConnectionResult(
                isSuccess = false,
                statusCode = 0,
                latencyMs = 0,
                modelName = config.modelName,
                message = "API Key 不能为空，请配置有效 Key。"
            )
        }

        try {
            val request = if (config.providerType == AiConfig.PROVIDER_GEMINI) {
                buildGeminiTestRequest(config)
            } else {
                buildOpenAiTestRequest(config)
            }

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val code = response.code
                val responseBodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    TestConnectionResult(
                        isSuccess = true,
                        statusCode = code,
                        latencyMs = latency,
                        modelName = config.modelName,
                        message = "API 认证与模型端点连接成功！响应延迟 ${latency}ms（未测试图片审核/视觉输入）",
                        rawResponse = responseBodyStr.take(300)
                    )
                } else {
                    val errMsg = when (code) {
                        401, 403 -> "权限验证失败 (HTTP $code)。请检查 API Key 是否有效。"
                        404 -> "模型端点不存在 (HTTP 404)。请校验 Base URL 或模型名称。"
                        429 -> "请求频率受限 (HTTP 429)。额度不足或触发 API Rate Limit。"
                        else -> "请求返回 HTTP $code 错误。$responseBodyStr"
                    }
                    TestConnectionResult(
                        isSuccess = false,
                        statusCode = code,
                        latencyMs = latency,
                        modelName = config.modelName,
                        message = errMsg,
                        rawResponse = responseBodyStr.take(300)
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TestConnectionResult(
                isSuccess = false,
                statusCode = 0,
                latencyMs = latency,
                modelName = config.modelName,
                message = "网络连接异常: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /** 单次 HTTP 尝试的结果：results 非空表示成功解析；否则说明是否值得重试。 */
    private class BatchAttempt(
        val results: List<RecognitionResult>? = null,
        val retryable: Boolean = false,
        val errorMessage: String = "",
        val retryAfterMs: Long? = null,
        val httpCode: Int? = null
    )

    /**
     * 识别一批图片。遇到 429 / 5xx / 超时 / 网络抖动时按指数退避自动重试
     * （最多 [RetryPolicy.MAX_HTTP_RETRIES] 次，并尊重服务端 Retry-After），等待期间可被协程取消。
     * 重试用尽仍失败时，结果带 retryable 标记，交给上层决定是否再次排队。
     */
    suspend fun recognizeBatch(
        config: AiConfig,
        items: List<ImagePayload>,
        onRetry: ((attempt: Int, maxAttempts: Int, waitMs: Long, reason: String) -> Unit)? = null
    ): List<RecognitionResult> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()

        val request = try {
            if (config.providerType == AiConfig.PROVIDER_GEMINI) {
                buildGeminiBatchRequest(config, items)
            } else {
                buildOpenAiBatchRequest(config, items)
            }
        } catch (e: Exception) {
            return@withContext failAll(items, config, "请求构建失败：${e.localizedMessage ?: "未知错误"}", false)
        }

        var lastFailure = BatchAttempt()
        for (attempt in 0..RetryPolicy.MAX_HTTP_RETRIES) {
            val outcome = executeBatchOnce(request, config, items)
            val ok = outcome.results
            if (ok != null) return@withContext ok
            lastFailure = outcome
            if (!outcome.retryable || attempt >= RetryPolicy.MAX_HTTP_RETRIES) break
            val wait = if (outcome.httpCode == 429 && outcome.retryAfterMs == null) {
                maxOf(RetryPolicy.backoffMs(attempt, null), RetryPolicy.RATE_LIMIT_FALLBACK_DELAY_MS)
            } else {
                RetryPolicy.backoffMs(attempt, outcome.retryAfterMs)
            }
            onRetry?.invoke(attempt + 1, RetryPolicy.MAX_HTTP_RETRIES, wait, outcome.errorMessage)
            delay(wait)
        }
        // 429 已在单次请求内部严格遵循 Retry-After 重试；不把它放入短间隔任务级队列，避免再次过早触发冷却。
        failAll(
            items, config, lastFailure.errorMessage,
            retryable = lastFailure.retryable && lastFailure.httpCode != 429,
            rateLimited = lastFailure.httpCode == 429
        )
    }

    private fun executeBatchOnce(
        request: Request,
        config: AiConfig,
        items: List<ImagePayload>
    ): BatchAttempt {
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val code = response.code
                    val retryAfterMs = RetryPolicy.parseRetryAfter(response.header("Retry-After"))
                    BatchAttempt(
                        retryable = RetryPolicy.isRetryableHttp(code),
                        errorMessage = "HTTP $code: ${bodyStr.take(300)}",
                        retryAfterMs = retryAfterMs,
                        httpCode = code
                    )
                } else {
                    val parsed = parseVisionResponseText(bodyStr, config.providerType)
                    if (parsed.errorMessage != null) {
                        return@use BatchAttempt(
                            retryable = true,
                            errorMessage = "云端返回内容不符合 ToImg JSON 协议：${parsed.errorMessage}"
                        )
                    }
                    val expectedIds = items.map { it.imageId }.toSet()
                    val validation = validateBatchIds(expectedIds, parsed.results.keys.toList(), parsed.duplicateIds)
                    if (validation.missingIds.isNotEmpty() || validation.duplicateIds.isNotEmpty() || validation.unknownIds.isNotEmpty()) {
                        return@use BatchAttempt(
                            retryable = true,
                            errorMessage = buildString {
                                append("云端返回图片 ID 校验失败")
                                if (validation.missingIds.isNotEmpty()) append("；缺失：${validation.missingIds.take(3).joinToString()}")
                                if (validation.duplicateIds.isNotEmpty()) append("；重复：${validation.duplicateIds.take(3).joinToString()}")
                                if (validation.unknownIds.isNotEmpty()) append("；未知：${validation.unknownIds.take(3).joinToString()}")
                            }
                        )
                    }
                    val results = items.map { payload -> parsed.results.getValue(payload.imageId).copy(modelName = config.modelName) }
                    BatchAttempt(results = results)
                }
            }
        } catch (e: java.io.IOException) {
            BatchAttempt(retryable = true, errorMessage = e.localizedMessage ?: "网络或超时异常")
        } catch (e: Exception) {
            BatchAttempt(retryable = false, errorMessage = e.localizedMessage ?: "请求异常")
        }
    }

    private fun failAll(
        items: List<ImagePayload>,
        config: AiConfig,
        error: String,
        retryable: Boolean,
        rateLimited: Boolean = false
    ): List<RecognitionResult> = items.map {
        RecognitionResult(
            imageId = it.imageId,
            keywords = emptyList(),
            description = "识别失败",
            modelName = config.modelName,
            isSuccess = false,
            errorMessage = error,
            retryable = retryable,
            rateLimited = rateLimited
        )
    }

    internal data class ParsedVisionResponse(
        val results: Map<String, RecognitionResult>,
        val duplicateIds: Set<String>,
        val errorMessage: String? = null
    )

    /** 解析并严格校验模型内容；任何结构/字段错误都不能被当成成功批次。 */
    internal fun parseVisionResponseText(
        rawHttpResponseBody: String,
        providerType: String
    ): ParsedVisionResponse {
        val map = linkedMapOf<String, RecognitionResult>()
        val duplicates = mutableSetOf<String>()
        return try {
            val root = JSONObject(rawHttpResponseBody)
            val contentText = if (providerType == AiConfig.PROVIDER_GEMINI) {
                root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.let { parts ->
                        (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }
                            .joinToString("\n")
                    }.orEmpty()
            } else {
                val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                val content = message?.opt("content")
                when (content) {
                    is String -> content
                    is JSONArray -> (0 until content.length()).mapNotNull { i ->
                        content.optJSONObject(i)?.optString("text")?.takeIf(String::isNotBlank)
                    }.joinToString("\n")
                    else -> ""
                }
            }
            val jsonText = extractJsonDocument(contentText)
                ?: return ParsedVisionResponse(emptyMap(), emptySet(), "模型内容中没有找到完整 JSON 数组或对象")
            val entries = when (val document = jsonText.trim()) {
                else -> if (document.startsWith("[")) {
                    val arr = JSONArray(document)
                    (0 until arr.length()).map { index ->
                        arr.optJSONObject(index) ?: throw IllegalArgumentException("第 ${index + 1} 项不是 JSON 对象")
                    }
                } else {
                    val obj = JSONObject(document)
                    val wrapperKey = when {
                        obj.has("results") -> "results"
                        obj.has("images") -> "images"
                        else -> null
                    }
                    if (wrapperKey != null) {
                        val wrapped = obj.optJSONArray(wrapperKey)
                            ?: throw IllegalArgumentException("$wrapperKey 必须是 JSON 数组")
                        (0 until wrapped.length()).map { index ->
                            wrapped.optJSONObject(index) ?: throw IllegalArgumentException("$wrapperKey 第 ${index + 1} 项不是 JSON 对象")
                        }
                    } else listOf(obj)
                }
            }
            if (entries.isEmpty()) return ParsedVisionResponse(emptyMap(), emptySet(), "JSON 中没有图片结果项")
            val allowedFields = setOf("image_id", "keywords", "description", "ocr_text", "is_sensitive")
            for (obj in entries) {
                val unexpected = obj.keys().asSequence().filter { it !in allowedFields }.toList()
                if (unexpected.isNotEmpty()) return ParsedVisionResponse(emptyMap(), duplicates, "存在未定义字段：${unexpected.joinToString()}")
                val id = obj.opt("image_id") as? String
                    ?: return ParsedVisionResponse(emptyMap(), duplicates, "image_id 缺失或不是字符串")
                if (id.isBlank()) return ParsedVisionResponse(emptyMap(), duplicates, "image_id 不能为空")
                val keywordsValue = obj.opt("keywords")
                if (keywordsValue !is JSONArray) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 keywords 必须是字符串数组")
                val keywords = mutableListOf<String>()
                for (i in 0 until keywordsValue.length()) {
                    val value = keywordsValue.opt(i)
                    if (value !is String || value.isBlank()) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 keywords 含有非字符串或空标签")
                    val normalized = value.trim()
                    if (normalized !in keywords) keywords.add(normalized)
                }
                if (keywords.isEmpty() || keywords.size > 10) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 keywords 数量必须为 1–10 个")
                val description = obj.opt("description") as? String
                if (description.isNullOrBlank()) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 description 缺失或为空")
                val ocr = obj.opt("ocr_text")
                if (ocr !is String) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 ocr_text 必须是字符串")
                val sensitive = obj.opt("is_sensitive")
                if (sensitive !is Boolean) return ParsedVisionResponse(emptyMap(), duplicates, "图片 $id 的 is_sensitive 必须是布尔值")
                if (map.containsKey(id)) duplicates.add(id)
                map[id] = RecognitionResult(
                    imageId = id, keywords = keywords, description = description.trim(), modelName = "",
                    isSuccess = true, ocrText = ocr.ifBlank { null }, isSensitive = sensitive
                )
            }
            ParsedVisionResponse(map, duplicates)
        } catch (e: Exception) {
            ParsedVisionResponse(emptyMap(), duplicates, "JSON 解析失败：${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /** 从围栏或说明文字中提取第一个完整 JSON 文档，避免简单截取造成嵌套 JSON 损坏。 */
    internal fun extractJsonDocument(raw: String): String? {
        val text = raw.trim().replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "").trim()
        val start = text.indexOfFirst { it == '[' || it == '{' }
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '[', '{' -> depth++
                    ']', '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                        if (depth < 0) return null
                    }
                }
            }
        }
        return null
    }

    internal fun cleanJsonString(raw: String): String = extractJsonDocument(raw) ?: raw.trim()

    private fun buildGeminiBatchRequest(config: AiConfig, items: List<ImagePayload>): Request {
        val cleanBaseUrl = config.baseUrl.trimEnd('/')
        val url = if (cleanBaseUrl.contains("key=")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/models/${config.modelName}:generateContent?key=${normalizeApiKey(config.apiKey)}"
        }

        val partsArray = JSONArray()
        val instructionPrompt = StringBuilder().apply {
            append("你是一个专业的高精度图像理解与分类助手。\n")
            append("请仔细分析传入的每张图片，为每张图片返回一个 JSON 数组。数组中的每个元素包含：\n")
            append("1. image_id: 必须与每张图片标注的ID完全严格一致；\n")
            append("2. keywords: 3到10个精准中文关键词标签（如主体、场景、色调、分类）。建议优先选用常用规范主标签（如：猫咪/宠物、狗狗/宠物、代码与截图、美食/餐品、自然/风景、文档/票据、发票收据、证件证书、人物/合照、生活日常等），再辅以具体特异性标签；\n")
            append("3. description: 一句简明扼要的中文总结描述；\n")
            append("4. ocr_text: 提取图片中显著可见的文字（如发票金额单号、证件文字、代码截图、招牌等，无文字则填空字符串）；\n")
            append("5. is_sensitive: 布尔值，判断是否包含身份证、银行卡、护照等敏感证件信息。\n\n")
            append("必须严格遵循 ToImg JSON 协议：一张图片对应且仅对应一项；image_id 必须逐字复制且不得遗漏、重复或虚构；keywords 必须为 1–10 个非空字符串组成的数组；description 必须为非空的一句简体中文描述；ocr_text 必须为字符串，无文字填空字符串；is_sensitive 必须为 true/false 布尔值。所有字段必填，不得为 null，不得添加额外字段。必须输出纯 JSON 数组，禁止解释文字和 Markdown。\n格式如下：\n")
            append("[{\"image_id\":\"xxx\",\"keywords\":[\"标签1\",\"标签2\"],\"description\":\"总结\",\"ocr_text\":\"...\",\"is_sensitive\":false}]")
        }.toString()

        partsArray.put(JSONObject().put("text", instructionPrompt))

        items.forEach { item ->
            partsArray.put(JSONObject().put("text", "【目标图片识别项】image_id: \"${item.imageId}\", 文件名: \"${item.fileName}\""))
            partsArray.put(
                JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", item.base64Jpeg)
                    })
                }
            )
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().put("parts", partsArray))
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .post(body)
            .build()
    }

    internal fun buildOpenAiBatchRequest(config: AiConfig, items: List<ImagePayload>): Request {
        val cleanBaseUrl = config.baseUrl.trimEnd('/')
        val url = if (cleanBaseUrl.endsWith("/chat/completions")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/chat/completions"
        }

        val contentArray = JSONArray()
        contentArray.put(
            JSONObject().apply {
                put("type", "text")
                put(
                    "text",
                    "你是无上下文的批量图片识别引擎。本次请求只处理当前消息中的图片，不存在历史对话，也不得假设知道此前任务。\n" +
                        "必须逐张、独立识别：每张图片只依据自身像素内容判断；严禁跨图片推断、借用其他图片信息、合并图片或复制其他图片标签。\n" +
                        "每张图片前都有独立 image_id 标记，输出必须一图一条且 image_id 原样对应，不得遗漏、重复、改写或虚构 ID。\n" +
                        "必须严格遵循 ToImg JSON 协议：输出纯 JSON 数组，一张输入图片对应且仅对应一项；image_id 必须逐字复制输入 ID，不得遗漏、重复或虚构；keywords 必须是 1–10 个非空字符串组成的数组（不得用逗号分隔字符串代替）；description 必须是非空的一句简体中文描述；ocr_text 必须是字符串，没有可见文字时填空字符串；is_sensitive 必须是 JSON 布尔值 true/false。所有字段均为必填，禁止 null、禁止额外字段、禁止解释文字和 Markdown。只根据当前图片像素判断，不确定的信息不要猜测。"
                )
            }
        )

        items.forEach { item ->
            contentArray.put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", "【待分析图片】image_id: \"${item.imageId}\"")
                }
            )
            contentArray.put(
                JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${item.base64Jpeg}"))
                }
            )
        }

        val jsonBody = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            })
            if (config.providerType == AiConfig.PROVIDER_NVIDIA) {
                put("temperature", config.temperature)
                put("top_p", config.topP)
                put("max_tokens", effectiveNvidiaMaxTokens(items.size, config.maxTokens))
                put("stream", false)
                config.seed?.let { put("seed", it) }
                config.frequencyPenalty?.let { put("frequency_penalty", it) }
                config.presencePenalty?.let { put("presence_penalty", it) }
                config.reasoningEffort?.let { put("reasoning_effort", it) }
                config.parallelToolCalls?.let { put("parallel_tool_calls", it) }
                config.enableThinking?.let { enabled ->
                    put("extra_body", JSONObject().put("chat_template_kwargs", JSONObject().put("enable_thinking", enabled)))
                }
            } else {
                put("temperature", 0.2)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${normalizeApiKey(config.apiKey)}")
            .post(body)
            .build()
    }

    /** 严格校验 AI 回传的 ID 集合，禁止未知/重复/缺失 ID 被误入库为成功。 */
    internal fun validateBatchIds(expectedIds: Set<String>, returnedIds: List<String>, duplicateIds: Set<String> = emptySet()): BatchIdValidation {
        val returned = returnedIds.toSet()
        return BatchIdValidation(
            missingIds = expectedIds - returned,
            duplicateIds = duplicateIds intersect expectedIds,
            unknownIds = returned - expectedIds
        )
    }

    /** 输出预算按本批图片数增长；profile.maxTokens 是用户/模型上限，不会被自动策略突破。 */
    internal fun effectiveNvidiaMaxTokens(batchSize: Int, configuredMaxTokens: Int): Int {
        val suggested = when {
            batchSize <= 5 -> 2048
            batchSize <= 10 -> 4096
            else -> 8192
        }
        return minOf(configuredMaxTokens.coerceAtLeast(1), suggested, NVIDIA_HOSTED_MAX_OUTPUT_TOKENS)
    }

    companion object {
        const val NVIDIA_HOSTED_MAX_OUTPUT_TOKENS = 131072
    }

    private fun buildGeminiTestRequest(config: AiConfig): Request {
        val cleanBaseUrl = config.baseUrl.trimEnd('/')
        val url = if (cleanBaseUrl.contains("key=")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/models/${config.modelName}:generateContent?key=${normalizeApiKey(config.apiKey)}"
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Respond with JSON: {\"status\":\"ok\",\"model\":\"${config.modelName}\"}"))
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/png")
                                put("data", sampleBase64Png)
                            })
                        })
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .post(body)
            .build()
    }

    internal fun buildOpenAiTestRequest(config: AiConfig): Request {
        val cleanBaseUrl = config.baseUrl.trimEnd('/')
        val url = if (cleanBaseUrl.endsWith("/chat/completions")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/chat/completions"
        }

        val jsonBody = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    // Connectivity test is deliberately text-only. NVIDIA's media
                    // screening layer may reject even a harmless synthetic image;
                    // that is not evidence of a network/authentication failure.
                    put("content", "Reply with exactly: TOIMG_API_OK")
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${normalizeApiKey(config.apiKey)}")
            .post(body)
            .build()
    }
}
