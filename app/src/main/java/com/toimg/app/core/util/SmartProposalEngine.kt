package com.toimg.app.core.util

import com.toimg.app.core.di.AppContainer

import android.content.Context
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SmartProposalGroup(
    val rule: SmartRuleEntity,
    val images: List<ImageEntity>
)

/**
 * 识别完成后，用智能规则（SmartRule）为「已识别、尚未移动」的图片生成归档提案。
 * 只生成提案，不移动任何文件；真正的移动仍需用户在「移动预览」里确认。
 */
object SmartProposalEngine {

    /** 纯函数：每张图片只归入第一个命中的已启用规则；规则顺序即分组顺序。 */
    fun group(
        rules: List<SmartRuleEntity>,
        entries: List<Pair<ImageEntity, List<String>>>
    ): List<SmartProposalGroup> {
        val enabled = rules.filter { it.isEnabled }
        if (enabled.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<Long, MutableList<ImageEntity>>()
        for ((image, keywords) in entries) {
            val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) continue
            val rule = FileMoveUtil.matchSmartRule(cleaned, enabled) ?: continue
            buckets.getOrPut(rule.id) { mutableListOf() }.add(image)
        }
        return enabled.mapNotNull { rule ->
            buckets[rule.id]?.let { SmartProposalGroup(rule, it.toList()) }
        }
    }

    suspend fun build(context: Context): List<SmartProposalGroup> = withContext(Dispatchers.IO) {
        try {
            val db = AppContainer.database(context.applicationContext)
            val rules = db.smartRuleDao().getAllRules().first()
            if (rules.none { it.isEnabled }) return@withContext emptyList()

            val images = db.imageDao().getImagesByStatus("RECOGNIZED")
            if (images.isEmpty()) return@withContext emptyList()

            val keywordMap = db.recognitionDao().getAllRecognitionsSync()
                .filter { it.status == "SUCCESS" }
                .associate { it.imageId to it.keywords.split(",", "，") }

            val entries = images.mapNotNull { img -> keywordMap[img.id]?.let { img to it } }
            group(rules, entries)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun count(context: Context): Int = try {
        build(context).sumOf { it.images.size }
    } catch (e: Throwable) {
        0
    }
}
