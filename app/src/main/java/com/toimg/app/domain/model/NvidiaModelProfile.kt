package com.toimg.app.domain.model

/** NVIDIA Build model-specific request settings. Null optional values are intentionally omitted. */
data class NvidiaModelProfile(
    val id: String,
    val displayName: String,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val seed: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val reasoningEffort: String? = null,
    val enableThinking: Boolean? = null,
    val parallelToolCalls: Boolean? = null,
    val isBuiltIn: Boolean = true
) {
    companion object {
        const val NEMOTRON_ID = "nvidia/nemotron-3.5-super-vl-120b-a12b"
        val BUILT_INS = listOf(
            NvidiaModelProfile(NEMOTRON_ID, "Nemotron 3.5 Super VL 120B A12B", 0.6, 0.95, 8192, seed = 42, reasoningEffort = "none", enableThinking = false, parallelToolCalls = true),
            NvidiaModelProfile("minimaxai/minimax-m3.1-flash", "MiniMax M3.1 Flash", 1.0, 0.95, 16384, seed = 0),
            NvidiaModelProfile("mistralai/mistral-large-4-1t-a52b", "Mistral Large 4", 0.5, 1.0, 4096, frequencyPenalty = 0.0, presencePenalty = 0.0, reasoningEffort = "high"),
            NvidiaModelProfile("stepfun-ai/step-5-preview", "Step 5 Preview", 0.5, 1.0, 4096, frequencyPenalty = 0.0, presencePenalty = 0.0),
            NvidiaModelProfile("deepseek-ai/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", 1.0, 0.95, 262144, isBuiltIn = true)
        )

        fun custom(id: String, name: String) = NvidiaModelProfile(
            id = id.trim(), displayName = name.trim().ifBlank { id.trim() },
            temperature = 0.6, topP = 0.95, maxTokens = 4096, isBuiltIn = false
        )
    }
}
