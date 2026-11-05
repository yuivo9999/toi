package com.toimg.app.data.remote

import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.NvidiaModelProfile
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaModelProfileRequestTest {
    private val client = AiVisionClient()

    @Test fun builtInCatalogContainsFiveVisionModelsAndNemotronIsPrimary() {
        assertEquals(5, NvidiaModelProfile.BUILT_INS.size)
        assertEquals(NvidiaModelProfile.NEMOTRON_ID, NvidiaModelProfile.BUILT_INS.first().id)
        assertEquals(0.6, NvidiaModelProfile.BUILT_INS.first().temperature, 0.0001)
        assertEquals(8192, NvidiaModelProfile.BUILT_INS.first().maxTokens)
        assertEquals("none", NvidiaModelProfile.BUILT_INS.first().reasoningEffort)
        assertEquals(false, NvidiaModelProfile.BUILT_INS.first().enableThinking)
        assertTrue(NvidiaModelProfile.BUILT_INS.all { it.id.isNotBlank() && it.maxTokens > 0 })
    }

    @Test fun requestUsesSelectedProfileSamplingAndOptionalParametersOnly() {
        val profile = NvidiaModelProfile.BUILT_INS.first()
        val config = AiConfig(
            apiKey = "nvapi-test", modelName = profile.id, providerType = AiConfig.PROVIDER_NVIDIA,
            temperature = profile.temperature, topP = profile.topP, maxTokens = profile.maxTokens,
            seed = profile.seed, enableThinking = profile.enableThinking,
            parallelToolCalls = profile.parallelToolCalls
        )
        val request = client.buildOpenAiBatchRequest(config, listOf(ImagePayload("img-1", "YWJj", "a.jpg")))
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val body = buffer.readUtf8()
        assertTrue(body.contains("\"model\":\"${profile.id}\""))
        assertTrue(body.contains("\"temperature\":0.6"))
        assertTrue(body.contains("\"top_p\":0.95"))
        assertTrue(body.contains("\"max_tokens\":2048"))
        assertTrue(body.contains("\"seed\":42"))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"enable_thinking\":false"))
        assertTrue(body.contains("无上下文的批量图片识别引擎"))
        assertTrue(body.contains("严禁跨图片推断"))
        assertEquals(1, org.json.JSONObject(body).getJSONArray("messages").length())
        assertEquals(3, org.json.JSONObject(body).getJSONArray("messages").getJSONObject(0).getJSONArray("content").length())

        assertEquals("Bearer nvapi-test", request.header("Authorization"))
    }

    @Test fun maxTokensBudgetScalesWithBatchAndNeverExceedsHostedCap() {
        assertEquals(2048, client.effectiveNvidiaMaxTokens(5, 16384))
        assertEquals(4096, client.effectiveNvidiaMaxTokens(10, 16384))
        assertEquals(8192, client.effectiveNvidiaMaxTokens(20, 16384))
        assertEquals(1024, client.effectiveNvidiaMaxTokens(10, 1024))
        assertEquals(8192, client.effectiveNvidiaMaxTokens(1000, 500000))
    }

    @Test fun responseImageIdsMustMatchBatchExactly() {
        val valid = client.validateBatchIds(setOf("a", "b"), listOf("a", "b"))
        assertTrue(valid.missingIds.isEmpty())
        assertTrue(valid.duplicateIds.isEmpty())
        assertTrue(valid.unknownIds.isEmpty())

        val invalid = client.validateBatchIds(setOf("a", "b"), listOf("a", "foreign"), setOf("a"))
        assertEquals(setOf("b"), invalid.missingIds)
        assertEquals(setOf("a"), invalid.duplicateIds)
        assertEquals(setOf("foreign"), invalid.unknownIds)
    }

    @Test fun optionalParametersAreOmittedWhenNotSupportedByProfile() {
        val config = AiConfig(apiKey = "key", modelName = "deepseek-ai/deepseek-v4.1-flash", providerType = AiConfig.PROVIDER_NVIDIA,
            temperature = 1.0, topP = 0.95, maxTokens = 262144, seed = null,
            frequencyPenalty = null, presencePenalty = null, reasoningEffort = null,
            enableThinking = null, parallelToolCalls = null)
        val request = client.buildOpenAiBatchRequest(config, listOf(ImagePayload("x", "YWJj", "x.jpg")))
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val body = buffer.readUtf8()
        assertFalse(body.contains("\"seed\"")); assertFalse(body.contains("\"reasoning_effort\""))
        assertFalse(body.contains("\"extra_body\"")); assertFalse(body.contains("\"parallel_tool_calls\""))
    }
}
