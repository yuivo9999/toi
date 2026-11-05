package com.toimg.app.data.repository

import android.content.Context
import com.toimg.app.BuildConfig
import com.toimg.app.domain.model.AiConfig

class AiConfigRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("toimg_ai_settings", Context.MODE_PRIVATE)

    fun getAiConfig(): AiConfig {
        val savedKey = prefs.getString("api_key", null)
        val apiKey = if (!savedKey.isNullOrBlank()) {
            savedKey
        } else {
            try {
                val buildConfigField = BuildConfig::class.java.getField("GEMINI_API_KEY")
                (buildConfigField.get(null) as? String) ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        val baseUrl = prefs.getString("base_url", AiConfig.DEFAULT_NVIDIA_BASE_URL) ?: AiConfig.DEFAULT_NVIDIA_BASE_URL
        val modelName = prefs.getString("model_name", AiConfig.DEFAULT_NVIDIA_MODEL) ?: AiConfig.DEFAULT_NVIDIA_MODEL
        val providerType = prefs.getString("provider_type", AiConfig.PROVIDER_NVIDIA) ?: AiConfig.PROVIDER_NVIDIA
        val engineMode = prefs.getString("engine_mode", AiConfig.MODE_HYBRID) ?: AiConfig.MODE_HYBRID
        val backupApiKey = prefs.getString("backup_api_key", "") ?: ""
        val backupBaseUrl = prefs.getString("backup_base_url", AiConfig.DEFAULT_BASE_URL) ?: AiConfig.DEFAULT_BASE_URL
        val backupModelName = prefs.getString("backup_model_name", "") ?: ""
        val backupProviderType = prefs.getString("backup_provider_type", AiConfig.PROVIDER_GEMINI) ?: AiConfig.PROVIDER_GEMINI

        val catalog = NvidiaModelCatalogRepository(context)
        val profiles = catalog.getModels(legacyModelId = modelName)
        val selectedId = catalog.getSelectedId().takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val profile = profiles.first { it.id == selectedId }
        return AiConfig(
            apiKey = apiKey,
            baseUrl = baseUrl,
            providerType = providerType,
            engineMode = engineMode,
            backupApiKey = backupApiKey,
            backupBaseUrl = backupBaseUrl,
            backupModelName = backupModelName,
            backupProviderType = backupProviderType,
            modelName = if (providerType == AiConfig.PROVIDER_NVIDIA) profile.id else modelName,
            temperature = profile.temperature,
            topP = profile.topP,
            maxTokens = profile.maxTokens,
            seed = profile.seed,
            frequencyPenalty = profile.frequencyPenalty,
            presencePenalty = profile.presencePenalty,
            reasoningEffort = profile.reasoningEffort,
            enableThinking = profile.enableThinking,
            parallelToolCalls = profile.parallelToolCalls
        )
    }

    fun saveAiConfig(config: AiConfig) {
        if (config.providerType == AiConfig.PROVIDER_NVIDIA) {
            val catalog = NvidiaModelCatalogRepository(context)
            catalog.select(config.modelName)
        }
        prefs.edit()
            .putString("api_key", config.apiKey)
            .putString("base_url", config.baseUrl)
            .putString("model_name", config.modelName)
            .putString("provider_type", config.providerType)
            .putString("engine_mode", config.engineMode)
            .putString("backup_api_key", config.backupApiKey)
            .putString("backup_base_url", config.backupBaseUrl)
            .putString("backup_model_name", config.backupModelName)
            .putString("backup_provider_type", config.backupProviderType)
            .apply()
    }


    /** 识别批次大小（5/10/20），整个 App 共用同一个设置，避免多处各自维护。 */
    fun getBatchSize(): Int {
        val saved = prefs.getInt("batch_size", DEFAULT_BATCH_SIZE)
        return if (saved in BATCH_SIZE_OPTIONS) saved else DEFAULT_BATCH_SIZE
    }

    fun saveBatchSize(size: Int) {
        if (size in BATCH_SIZE_OPTIONS) prefs.edit().putInt("batch_size", size).apply()
    }

    /** NVIDIA 专属批次间隔；仅在本批结果已落库后等待。 */
    fun getNvidiaRequestIntervalSeconds(): Int {
        val saved = prefs.getInt(KEY_NVIDIA_INTERVAL_SECONDS, DEFAULT_NVIDIA_INTERVAL_SECONDS)
        return if (saved in NVIDIA_INTERVAL_OPTIONS || saved in 5..600) saved else DEFAULT_NVIDIA_INTERVAL_SECONDS
    }

    fun saveNvidiaRequestIntervalSeconds(seconds: Int) {
        if (seconds in 5..600) prefs.edit().putInt(KEY_NVIDIA_INTERVAL_SECONDS, seconds).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove("api_key").apply()
    }

    companion object {
        val BATCH_SIZE_OPTIONS = listOf(5, 10, 20)
        val NVIDIA_INTERVAL_OPTIONS = listOf(15, 25, 35)
        const val DEFAULT_BATCH_SIZE = 10
        const val DEFAULT_NVIDIA_INTERVAL_SECONDS = 25
        private const val KEY_NVIDIA_INTERVAL_SECONDS = "nvidia_request_interval_seconds"
    }
}
