package com.toimg.app.domain.model

data class AiConfig(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_NVIDIA_BASE_URL,
    val modelName: String = DEFAULT_NVIDIA_MODEL,
    val providerType: String = PROVIDER_NVIDIA,
    val engineMode: String = MODE_HYBRID,
    val backupApiKey: String = "",
    val backupBaseUrl: String = DEFAULT_BASE_URL,
    val backupModelName: String = "",
    val backupProviderType: String = PROVIDER_GEMINI,
    val temperature: Double = 0.6,
    val topP: Double = 0.95,
    val maxTokens: Int = 16384,
    val seed: Int? = 42,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val reasoningEffort: String? = null,
    val enableThinking: Boolean? = true,
    val parallelToolCalls: Boolean? = true
) {
    val maskedApiKey: String
        get() {
            if (apiKey.isBlank()) return "未设置 API Key"
            return if (apiKey.length > 8) {
                "${apiKey.take(4)}...${apiKey.takeLast(4)}"
            } else {
                "********"
            }
        }

    val isOfflineMode: Boolean
        get() = engineMode == MODE_OFFLINE_ONLY

    val isHybridMode: Boolean
        get() = engineMode == MODE_HYBRID

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val DEFAULT_NVIDIA_MODEL = "nvidia/nemotron-3.5-super-vl-120b-a12b"
        const val PROVIDER_GEMINI = "GEMINI_DIRECT"
        const val PROVIDER_OPENAI = "OPENAI_COMPATIBLE"
        const val PROVIDER_NVIDIA = "NVIDIA_BUILD"

        const val MODE_HYBRID = "HYBRID"          // 云端优先 + 离线兜底 (推荐)
        const val MODE_OFFLINE_ONLY = "OFFLINE"   // 纯本地端侧离线 (免 Key、零费用、防隐私泄露)
        const val MODE_CLOUD_ONLY = "CLOUD"       // 仅云端大模型
    }
}


data class TestConnectionResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0L,
    val modelName: String = "",
    val message: String = "",
    val rawResponse: String = ""
)
