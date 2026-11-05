package com.toimg.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import okio.Buffer
import com.toimg.app.domain.model.AiConfig
import org.junit.Test

class AiVisionClientTest {

    private val client = AiVisionClient()

    @Test
    fun normalizeApiKey_removesClipboardWhitespaceAndLineBreaks() {
        assertEquals("nvapi-abcdef123", client.normalizeApiKey("\\n nvapi-abc\\r\\ndef123 \\t"))
    }

    @Test
    fun normalizeApiKey_preservesOrdinaryTokenCharacters() {
        assertEquals("nvapi_ABC-123.xyz", client.normalizeApiKey("nvapi_ABC-123.xyz"))
    }

    @Test
    fun openAiConnectivityProbe_isTextOnlyAndDoesNotSendMedia() {
        val config = AiConfig(
            apiKey = "nvapi-test-token",
            baseUrl = AiConfig.DEFAULT_NVIDIA_BASE_URL,
            modelName = AiConfig.DEFAULT_NVIDIA_MODEL,
            providerType = AiConfig.PROVIDER_NVIDIA
        )
        val request = client.buildOpenAiTestRequest(config)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val body = buffer.readUtf8()

        assertTrue(body.contains("TOIMG_API_OK"))
        assertFalse(body.contains("image_url"))
        assertFalse(body.contains("data:image"))
        assertFalse(body.contains("base64"))
        assertEquals("Bearer nvapi-test-token", request.header("Authorization"))
    }

    @Test
    fun cleanJsonString_stripsMarkdownCodeFences() {
        val markdownJson = """
            ```json
            [{"image_id":"1","keywords":["猫咪"],"description":"一只可爱的猫"}]
            ```
        """.trimIndent()

        val cleaned = client.cleanJsonString(markdownJson)
        assertEquals("[{\"image_id\":\"1\",\"keywords\":[\"猫咪\"],\"description\":\"一只可爱的猫\"}]", cleaned)
    }

    @Test
    fun cleanJsonString_handlesPlainJsonWithoutFences() {
        val plainJson = "[{\"image_id\":\"2\",\"keywords\":[\"发票\"]}]"
        val cleaned = client.cleanJsonString(plainJson)
        assertEquals(plainJson, cleaned)
    }
    private fun response(content: String): String = org.json.JSONObject().apply {
        put("choices", org.json.JSONArray().put(org.json.JSONObject().apply {
            put("message", org.json.JSONObject().put("content", content))
        }))
    }.toString()

    private fun validItem(id: String = "img-1"): String =
        """{"image_id":"$id","keywords":["猫咪","宠物"],"description":"一只猫在沙发上。","ocr_text":"","is_sensitive":false}"""

    @Test
    fun parseVisionResponse_acceptsStrictProtocolAndMapsImageId() {
        val parsed = client.parseVisionResponseText(response("[${validItem()}]"), AiConfig.PROVIDER_NVIDIA)
        assertEquals(null, parsed.errorMessage)
        assertEquals(listOf("猫咪", "宠物"), parsed.results["img-1"]?.keywords)
        assertEquals("一只猫在沙发上。", parsed.results["img-1"]?.description)
        assertEquals(false, parsed.results["img-1"]?.isSensitive)
    }

    @Test
    fun parseVisionResponse_extractsJsonFromExplanationAndMarkdownFence() {
        val content = "模型分析完成：\n```json\n[${validItem()}]\n```\n以上是结果。"
        val parsed = client.parseVisionResponseText(response(content), AiConfig.PROVIDER_NVIDIA)
        assertEquals(null, parsed.errorMessage)
        assertTrue(parsed.results.containsKey("img-1"))
    }

    @Test
    fun parseVisionResponse_rejectsMissingOrWrongTypedFields() {
        val invalid = """{"image_id":"img-1","keywords":"猫咪,宠物","description":"猫。","ocr_text":"","is_sensitive":false}"""
        val parsed = client.parseVisionResponseText(response("[$invalid]"), AiConfig.PROVIDER_NVIDIA)
        assertTrue(parsed.errorMessage?.contains("keywords") == true)
        assertTrue(parsed.results.isEmpty())
    }

    @Test
    fun parseVisionResponse_rejectsEmptyDescriptionAndMoreThanTenKeywords() {
        val emptyDescription = """{"image_id":"img-1","keywords":["猫"],"description":" ","ocr_text":"","is_sensitive":false}"""
        val tooMany = """{"image_id":"img-1","keywords":["1","2","3","4","5","6","7","8","9","10","11"],"description":"说明","ocr_text":"","is_sensitive":false}"""
        assertTrue(client.parseVisionResponseText(response("[$emptyDescription]"), AiConfig.PROVIDER_NVIDIA).errorMessage!!.contains("description"))
        assertTrue(client.parseVisionResponseText(response("[$tooMany]"), AiConfig.PROVIDER_NVIDIA).errorMessage!!.contains("1–10"))
    }

    @Test
    fun parseVisionResponse_supportsWrappedResultsObject() {
        val content = """{"results":[${validItem()}]}"""
        val parsed = client.parseVisionResponseText(response(content), AiConfig.PROVIDER_NVIDIA)
        assertEquals(null, parsed.errorMessage)
        assertTrue(parsed.results.containsKey("img-1"))
    }

    @Test
    fun extractJsonDocument_handlesBracesInsideQuotedText() {
        val extracted = client.extractJsonDocument("prefix [{\"image_id\":\"x\",\"description\":\"{nested}\"}] suffix")
        assertEquals("[{\"image_id\":\"x\",\"description\":\"{nested}\"}]", extracted)
    }

    @Test
    fun openAiBatchRequest_includesStrictToImgResponseContract() {
        val config = AiConfig(
            apiKey = "nvapi-test-token", baseUrl = AiConfig.DEFAULT_NVIDIA_BASE_URL,
            modelName = AiConfig.DEFAULT_NVIDIA_MODEL, providerType = AiConfig.PROVIDER_NVIDIA
        )
        val request = client.buildOpenAiBatchRequest(config, listOf(ImagePayload("img-1", "ZmFrZQ==", "a.jpg")))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("ToImg JSON 协议"))
        assertTrue(body.contains("不得遗漏、重复或虚构"))
        assertTrue(body.contains("is_sensitive"))
    }

}
