package com.toimg.app.data.repository

import android.content.Context
import com.toimg.app.domain.model.NvidiaModelProfile
import org.json.JSONArray
import org.json.JSONObject

class NvidiaModelCatalogRepository(context: Context) {
    private val prefs = context.getSharedPreferences("toimg_nvidia_models", Context.MODE_PRIVATE)

    fun getModels(legacyModelId: String? = null): List<NvidiaModelProfile> {
        val raw = prefs.getString(KEY_MODELS, null)
        if (raw.isNullOrBlank()) {
            // Upgrade migration: preserve a user-defined Model ID from the previous single-model setting.
            val legacyId = legacyModelId?.trim()?.takeIf { it.isNotBlank() }
            val legacyCustom = legacyId?.takeIf { id -> NvidiaModelProfile.BUILT_INS.none { it.id == id } }
            val seeded = NvidiaModelProfile.BUILT_INS + listOfNotNull(legacyCustom?.let { NvidiaModelProfile.custom(it, it) })
            saveModels(seeded)
            if (legacyId != null && seeded.any { it.id == legacyId }) select(legacyId)
            return seeded
        }
        return try {
            val array = JSONArray(raw)
            val loaded = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toProfile() }
                .ifEmpty { NvidiaModelProfile.BUILT_INS }
            // 0.1.7 默认策略迁移：仅迁移未被用户改动的旧 Nemotron 内置默认值。
            val migrated = loaded.map { profile ->
                if (profile.id == NvidiaModelProfile.NEMOTRON_ID && profile.isBuiltIn &&
                    profile.maxTokens == 16384 && profile.enableThinking == true && profile.reasoningEffort == null
                ) profile.copy(maxTokens = 8192, reasoningEffort = "none", enableThinking = false) else profile
            }
            if (migrated != loaded) saveModels(migrated)
            migrated
        } catch (_: Exception) { NvidiaModelProfile.BUILT_INS }
    }

    fun getSelectedId(): String = prefs.getString(KEY_SELECTED, NvidiaModelProfile.NEMOTRON_ID) ?: NvidiaModelProfile.NEMOTRON_ID
    fun select(id: String) { prefs.edit().putString(KEY_SELECTED, id).apply() }

    fun saveModels(models: List<NvidiaModelProfile>) {
        val array = JSONArray()
        models.forEach { m -> array.put(m.toJson()) }
        prefs.edit().putString(KEY_MODELS, array.toString()).apply()
    }

    fun saveModel(profile: NvidiaModelProfile, replaceId: String? = null) {
        val all = getModels().filterNot { it.id == profile.id || (replaceId != null && it.id == replaceId) } + profile
        saveModels(all)
        if (replaceId != null && getSelectedId() == replaceId) select(profile.id)
    }

    fun deleteModel(id: String): List<NvidiaModelProfile> {
        val remaining = getModels().filterNot { it.id == id }.ifEmpty { NvidiaModelProfile.BUILT_INS.take(1) }
        saveModels(remaining)
        if (getSelectedId() == id) select(remaining.first().id)
        return remaining
    }

    private fun NvidiaModelProfile.toJson() = JSONObject().apply {
        put("id", id); put("displayName", displayName); put("temperature", temperature)
        put("topP", topP); put("maxTokens", maxTokens); put("seed", seed ?: JSONObject.NULL)
        put("frequencyPenalty", frequencyPenalty ?: JSONObject.NULL); put("presencePenalty", presencePenalty ?: JSONObject.NULL)
        put("reasoningEffort", reasoningEffort ?: JSONObject.NULL); put("enableThinking", enableThinking ?: JSONObject.NULL)
        put("parallelToolCalls", parallelToolCalls ?: JSONObject.NULL); put("isBuiltIn", isBuiltIn)
    }

    private fun JSONObject.toProfile() = NvidiaModelProfile(
        id = optString("id"), displayName = optString("displayName", optString("id")),
        temperature = optDouble("temperature", 0.6), topP = optDouble("topP", 0.95), maxTokens = optInt("maxTokens", 4096),
        seed = optNullableInt("seed"), frequencyPenalty = optNullableDouble("frequencyPenalty"),
        presencePenalty = optNullableDouble("presencePenalty"), reasoningEffort = optNullableString("reasoningEffort"),
        enableThinking = optNullableBoolean("enableThinking"), parallelToolCalls = optNullableBoolean("parallelToolCalls"),
        isBuiltIn = optBoolean("isBuiltIn", false)
    )

    private fun JSONObject.optNullableInt(k: String) = if (isNull(k) || !has(k)) null else optInt(k)
    private fun JSONObject.optNullableDouble(k: String) = if (isNull(k) || !has(k)) null else optDouble(k)
    private fun JSONObject.optNullableString(k: String) = if (isNull(k) || !has(k)) null else optString(k)
    private fun JSONObject.optNullableBoolean(k: String) = if (isNull(k) || !has(k)) null else optBoolean(k)

    companion object { private const val KEY_MODELS = "model_profiles_v1"; private const val KEY_SELECTED = "selected_model_id" }
}
