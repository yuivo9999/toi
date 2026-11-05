package com.toimg.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.toimg.app.core.util.TagNormalizeUtil
import org.json.JSONObject

class TagSynonymRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("toimg_tag_synonyms", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_SYNONYMS = "custom_synonyms_json"
    }

    fun getCustomSynonyms(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_CUSTOM_SYNONYMS, null) ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.getString(key)
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    fun saveCustomSynonym(sourceWord: String, targetTag: String) {
        val current = getCustomSynonyms().toMutableMap()
        current[sourceWord.trim().lowercase()] = targetTag.trim()
        val json = JSONObject(current as Map<*, *>)
        prefs.edit().putString(KEY_CUSTOM_SYNONYMS, json.toString()).apply()
    }

    fun removeCustomSynonym(sourceWord: String) {
        val current = getCustomSynonyms().toMutableMap()
        current.remove(sourceWord.trim().lowercase())
        val json = JSONObject(current as Map<*, *>)
        prefs.edit().putString(KEY_CUSTOM_SYNONYMS, json.toString()).apply()
    }

    fun getAllSynonymsCombined(): Map<String, String> {
        val builtIn = TagNormalizeUtil.getBuiltInSynonyms()
        val custom = getCustomSynonyms()
        return builtIn + custom
    }
}
