package com.toimg.app.feature.settings

import androidx.room.withTransaction

import com.toimg.app.core.di.AppContainer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.data.remote.AiVisionClient
import com.toimg.app.data.repository.AiConfigRepository
import com.toimg.app.data.repository.NvidiaModelCatalogRepository
import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.NvidiaModelProfile
import com.toimg.app.domain.model.TestConnectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.repository.ImageRepository

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = AiConfig.DEFAULT_NVIDIA_BASE_URL,
    val modelName: String = AiConfig.DEFAULT_NVIDIA_MODEL,
    val providerType: String = AiConfig.PROVIDER_NVIDIA,
    val backupApiKey: String = "",
    val backupBaseUrl: String = AiConfig.DEFAULT_BASE_URL,
    val backupModelName: String = "",
    val backupProviderType: String = AiConfig.PROVIDER_GEMINI,
    val engineMode: String = AiConfig.MODE_HYBRID,
    val isKeyVisible: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestConnectionResult? = null,
    val saveMessage: String? = null,
    val smartRules: List<SmartRuleEntity> = emptyList(),
    val customSynonyms: Map<String, String> = emptyMap(),
    val nvidiaModels: List<NvidiaModelProfile> = NvidiaModelProfile.BUILT_INS,
    val selectedNvidiaModelId: String = NvidiaModelProfile.NEMOTRON_ID
) {
    val maskedKeyDisplay: String
        get() {
            if (apiKey.isBlank()) return "未配置 API Key"
            if (isKeyVisible) return apiKey
            return if (apiKey.length > 8) "${apiKey.take(4)}...${apiKey.takeLast(4)}" else "********"
        }
}

class SettingsViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val client = AiVisionClient()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun loadConfig(context: Context) {
        try {
            val repo = AiConfigRepository(context)
            val config = repo.getAiConfig()
            _uiState.update {
                it.copy(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    modelName = config.modelName,
                    providerType = config.providerType,
                    engineMode = config.engineMode,
                    backupApiKey = config.backupApiKey,
                    backupBaseUrl = config.backupBaseUrl,
                    backupModelName = config.backupModelName,
                    backupProviderType = config.backupProviderType,
                    nvidiaModels = NvidiaModelCatalogRepository(context).getModels(),
                    selectedNvidiaModelId = NvidiaModelCatalogRepository(context).getSelectedId()
                )
            }

            loadSmartRules(context)
            loadSynonyms(context)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun loadSmartRules(context: Context) {
        viewModelScope.launch {
            try {
                repository.seedPresetSmartRulesIfEmpty(context)
                repository.observeSmartRules(context).collect { rules ->
                    _uiState.update { it.copy(smartRules = rules) }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun toggleRule(context: Context, id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateRuleStatus(context, id, isEnabled)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun addRule(context: Context, name: String, keywordTrigger: String, folderName: String) {
        viewModelScope.launch {
            try {
                val rule = SmartRuleEntity(
                    name = name,
                    keywordTrigger = keywordTrigger,
                    targetFolderName = folderName,
                    targetTreeUri = "",
                    isEnabled = true
                )
                repository.saveSmartRule(context, rule)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRule(context: Context, id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteSmartRule(context, id)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }


    fun selectNvidiaModel(context: Context, id: String) {
        val catalog = NvidiaModelCatalogRepository(context)
        val profile = catalog.getModels().firstOrNull { it.id == id } ?: return
        catalog.select(id)
        _uiState.update { it.copy(selectedNvidiaModelId = id, modelName = id, saveMessage = null) }
    }

    fun saveNvidiaModel(context: Context, profile: NvidiaModelProfile, select: Boolean = true, replaceId: String? = null) {
        val catalog = NvidiaModelCatalogRepository(context)
        catalog.saveModel(profile, replaceId)
        if (select) catalog.select(profile.id)
        val models = catalog.getModels()
        _uiState.update { it.copy(nvidiaModels = models, selectedNvidiaModelId = if (select) profile.id else it.selectedNvidiaModelId, modelName = if (select) profile.id else it.modelName, saveMessage = "模型配置已保存") }
    }

    fun deleteNvidiaModel(context: Context, id: String) {
        val catalog = NvidiaModelCatalogRepository(context)
        val remaining = catalog.getModels()
        if (remaining.size <= 1) {
            _uiState.update { it.copy(saveMessage = "至少保留一个模型") }
            return
        }
        val updated = catalog.deleteModel(id)
        val selected = catalog.getSelectedId()
        _uiState.update { it.copy(nvidiaModels = updated, selectedNvidiaModelId = selected, modelName = selected, saveMessage = "模型已删除") }
    }

    fun resetNvidiaModel(context: Context, id: String) {
        val builtIn = NvidiaModelProfile.BUILT_INS.firstOrNull { it.id == id } ?: return
        saveNvidiaModel(context, builtIn, select = false)
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key, saveMessage = null) }
    }

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url, saveMessage = null) }
    }

    fun updateModelName(model: String) {
        _uiState.update { it.copy(modelName = model, saveMessage = null) }
    }

    fun updateProviderType(type: String) {
        val defaultUrl = when (type) {
            AiConfig.PROVIDER_GEMINI -> AiConfig.DEFAULT_BASE_URL
            AiConfig.PROVIDER_NVIDIA -> AiConfig.DEFAULT_NVIDIA_BASE_URL
            else -> "https://api.openai.com/v1"
        }
        _uiState.update { current ->
            val nvidiaSelected = current.nvidiaModels.firstOrNull { it.id == current.selectedNvidiaModelId }?.id
            val defaultModel = when (type) {
                AiConfig.PROVIDER_GEMINI -> AiConfig.DEFAULT_MODEL
                AiConfig.PROVIDER_NVIDIA -> nvidiaSelected ?: AiConfig.DEFAULT_NVIDIA_MODEL
                else -> "gpt-4o-mini"
            }
            current.copy(
                providerType = type,
                baseUrl = defaultUrl,
                modelName = defaultModel,
                saveMessage = null
            )
        }
    }


    fun updateBackupApiKey(value: String) { _uiState.update { it.copy(backupApiKey = value, saveMessage = null) } }
    fun updateBackupBaseUrl(value: String) { _uiState.update { it.copy(backupBaseUrl = value, saveMessage = null) } }
    fun updateBackupModelName(value: String) { _uiState.update { it.copy(backupModelName = value, saveMessage = null) } }
    fun updateBackupProviderType(value: String) { _uiState.update { it.copy(backupProviderType = value, saveMessage = null) } }

    fun updateEngineMode(mode: String) {
        _uiState.update { it.copy(engineMode = mode, saveMessage = null) }
    }

    fun toggleKeyVisibility() {
        _uiState.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun testConnectivity(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val currentConfig = AiConfig(
                apiKey = _uiState.value.apiKey,
                baseUrl = _uiState.value.baseUrl,
                modelName = _uiState.value.modelName,
                providerType = _uiState.value.providerType,
                engineMode = _uiState.value.engineMode,
                backupApiKey = _uiState.value.backupApiKey,
                backupBaseUrl = _uiState.value.backupBaseUrl,
                backupModelName = _uiState.value.backupModelName,
                backupProviderType = _uiState.value.backupProviderType,
                temperature = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.temperature ?: 0.6,
                topP = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.topP ?: 0.95,
                maxTokens = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.maxTokens ?: 4096,
                seed = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.seed,
                frequencyPenalty = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.frequencyPenalty,
                presencePenalty = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.presencePenalty,
                reasoningEffort = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.reasoningEffort,
                enableThinking = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.enableThinking,
                parallelToolCalls = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.parallelToolCalls
            )
            val result = client.testConnection(currentConfig)
            _uiState.update { it.copy(isTesting = false, testResult = result) }
        }
    }

    fun saveSettings(context: Context) {
        val repo = AiConfigRepository(context)
        val config = AiConfig(
            apiKey = _uiState.value.apiKey,
            baseUrl = _uiState.value.baseUrl,
            modelName = _uiState.value.modelName,
            providerType = _uiState.value.providerType,
            engineMode = _uiState.value.engineMode,
            backupApiKey = _uiState.value.backupApiKey,
            backupBaseUrl = _uiState.value.backupBaseUrl,
            backupModelName = _uiState.value.backupModelName,
            backupProviderType = _uiState.value.backupProviderType,
            temperature = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.temperature ?: 0.6,
            topP = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.topP ?: 0.95,
            maxTokens = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.maxTokens ?: 4096,
            seed = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.seed,
            frequencyPenalty = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.frequencyPenalty,
            presencePenalty = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.presencePenalty,
            reasoningEffort = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.reasoningEffort,
            enableThinking = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.enableThinking,
            parallelToolCalls = _uiState.value.nvidiaModels.firstOrNull { it.id == _uiState.value.selectedNvidiaModelId }?.parallelToolCalls
        )
        repo.saveAiConfig(config)
        _uiState.update { it.copy(saveMessage = "AI 配置与模型模式已成功保存！") }
    }


    fun clearApiKey(context: Context) {
        val repo = AiConfigRepository(context)
        repo.clearApiKey()
        _uiState.update { it.copy(apiKey = "", saveMessage = "API Key 已擦除。") }
    }

    fun exportRecognitions(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val db = AppContainer.database(context)
                val recognitions = db.recognitionDao().getAllRecognitionsSync()
                val jsonArray = org.json.JSONArray()
                recognitions.forEach { r ->
                    jsonArray.put(org.json.JSONObject().apply {
                        put("imageId", r.imageId)
                        put("keywords", org.json.JSONArray(r.keywords.split(",").map { it.trim() }))
                        put("description", r.description)
                        put("modelName", r.modelName)
                        put("ocrText", r.ocrText ?: "")
                        put("isSensitive", r.isSensitive)
                    })
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                }
                _uiState.update { it.copy(saveMessage = "已成功导出 ${recognitions.size} 条 AI 识别备份记录！") }
            } catch (e: Exception) {
                _uiState.update { it.copy(saveMessage = "导出失败: ${e.localizedMessage}") }
            }
        }
    }

    fun importRecognitions(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val array = org.json.JSONArray(jsonStr)
                val db = AppContainer.database(context)
                var count = 0
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val imageId = obj.optString("imageId", "")
                    if (imageId.isBlank()) continue

                    val kwList = mutableListOf<String>()
                    val kwArr = obj.optJSONArray("keywords")
                    if (kwArr != null) {
                        for (k in 0 until kwArr.length()) {
                            kwList.add(kwArr.optString(k))
                        }
                    }

                    val entity = com.toimg.app.data.local.entity.RecognitionEntity(
                        imageId = imageId,
                        keywords = kwList.joinToString(","),
                        description = obj.optString("description", ""),
                        modelName = obj.optString("modelName", "imported"),
                        status = "SUCCESS",
                        ocrText = obj.optString("ocrText", "").ifBlank { null },
                        isSensitive = obj.optBoolean("isSensitive", false),
                        errorMessage = null
                    )
                    // Ignore stale exports whose image index no longer exists (for example,
                    // an exclusive source was removed after the export was created).
                    db.withTransaction {
                        if (db.imageDao().getImageById(imageId) != null) {
                            db.recognitionDao().insertRecognition(entity)
                            db.imageDao().updateStatus(imageId, "RECOGNIZED")
                            db.imageFtsDao().syncFtsForImage(imageId)
                            count++
                        }
                    }
                }
                _uiState.update { it.copy(saveMessage = "已成功导入 $count 条 AI 识别记录！") }
            } catch (e: Exception) {
                _uiState.update { it.copy(saveMessage = "导入失败: ${e.localizedMessage}") }
            }
        }
    }

    fun loadSynonyms(context: Context) {
        val repo = com.toimg.app.data.repository.TagSynonymRepository(context)
        _uiState.update { it.copy(customSynonyms = repo.getCustomSynonyms()) }
    }

    fun addCustomSynonym(context: Context, sourceWord: String, targetTag: String) {
        val repo = com.toimg.app.data.repository.TagSynonymRepository(context)
        repo.saveCustomSynonym(sourceWord, targetTag)
        loadSynonyms(context)
        _uiState.update { it.copy(saveMessage = "已添加同义词映射：$sourceWord ➔ $targetTag") }
    }

    fun deleteCustomSynonym(context: Context, sourceWord: String) {
        val repo = com.toimg.app.data.repository.TagSynonymRepository(context)
        repo.removeCustomSynonym(sourceWord)
        loadSynonyms(context)
        _uiState.update { it.copy(saveMessage = "已移除同义词规则：$sourceWord") }
    }
}
