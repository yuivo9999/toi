package com.toimg.app.core.util

import com.toimg.app.domain.model.CategoryItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult

data class MergeSuggestion(
    val sourceTag: String,
    val targetTag: String,
    val reason: String
)

object TagNormalizeUtil {

    private val builtInSynonymMap = mapOf(
        "cat" to "猫咪/宠物",
        "cats" to "猫咪/宠物",
        "猫" to "猫咪/宠物",
        "猫咪" to "猫咪/宠物",
        "喵星人" to "猫咪/宠物",
        "dog" to "狗狗/宠物",
        "dogs" to "狗狗/宠物",
        "狗" to "狗狗/宠物",
        "小狗" to "狗狗/宠物",
        "汪星人" to "狗狗/宠物",
        "宠物" to "猫咪/宠物",
        "code" to "代码与截图",
        "代码" to "代码与截图",
        "screenshot" to "代码与截图",
        "截图" to "代码与截图",
        "编程" to "代码与截图",
        "终端" to "代码与截图",
        "food" to "美食/餐品",
        "美食" to "美食/餐品",
        "菜肴" to "美食/餐品",
        "餐饮" to "美食/餐品",
        "甜点" to "美食/餐品",
        "风景" to "自然/风景",
        "自然" to "自然/风景",
        "landscape" to "自然/风景",
        "山水" to "自然/风景",
        "天空" to "自然/风景",
        "文档" to "文档/票据",
        "票据" to "文档/票据",
        "发票" to "文档/票据",
        "收据" to "文档/票据",
        "证件" to "文档/票据",
        "合同" to "文档/票据",
        "人像" to "人物/合照",
        "人物" to "人物/合照",
        "自拍" to "人物/合照",
        "合影" to "人物/合照"
    )

    fun getBuiltInSynonyms(): Map<String, String> = builtInSynonymMap

    fun normalizeTag(rawTag: String, customSynonyms: Map<String, String> = emptyMap()): String {
        val clean = rawTag.trim()
            .replace("#", "")
            .replace(".", "")
            .replace("，", "")
            .replace(",", "")
            .lowercase()

        if (clean.isBlank()) return "其他分类"

        // 1. 用户自定义同义词表优先匹配
        customSynonyms[clean]?.let { return it }
        val customCaseInsensitive = customSynonyms.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }
        if (customCaseInsensitive != null) return customCaseInsensitive.value

        // 2. 内置规范词表兜底
        return builtInSynonymMap[clean] ?: rawTag.trim().take(15)
    }

    fun buildCategories(
        images: List<ImageItem>,
        recognitions: Map<String, RecognitionResult>,
        customSynonyms: Map<String, String> = emptyMap()
    ): List<CategoryItem> {
        val imageMap = images.associateBy { it.id }
        val categoryToImageIds = mutableMapOf<String, MutableList<String>>()

        recognitions.forEach { (imageId, result) ->
            if (result.isSuccess && imageMap.containsKey(imageId)) {
                result.keywords.forEach { rawKw ->
                    val normTag = normalizeTag(rawKw, customSynonyms)
                    val list = categoryToImageIds.getOrPut(normTag) { mutableListOf() }
                    if (!list.contains(imageId)) {
                        list.add(imageId)
                    }
                }
            }
        }

        // Add uncategorized or un-recognized images under "未分类"
        val categorizedIds = categoryToImageIds.values.flatten().toSet()
        val uncategorizedIds = images.filter { !categorizedIds.contains(it.id) }.map { it.id }
        if (uncategorizedIds.isNotEmpty()) {
            categoryToImageIds["未分类/待处理"] = uncategorizedIds.toMutableList()
        }

        return categoryToImageIds.map { (tag, ids) ->
            val coverUri = ids.firstNotNullOfOrNull { id -> imageMap[id]?.uri }
            CategoryItem(
                tag = tag,
                displayName = tag,
                imageCount = ids.size,
                coverUri = coverUri,
                imageIds = ids
            )
        }.sortedByDescending { it.imageCount }
    }

    /**
     * 智能推断当前分类列表中的潜在相似标签，生成建议合并方案
     */
    fun detectMergeSuggestions(categories: List<CategoryItem>): List<MergeSuggestion> {
        val validCategories = categories.filter { it.tag != "未分类/待处理" && it.tag != "其他分类" }
        val suggestions = mutableListOf<MergeSuggestion>()
        val processedPairs = mutableSetOf<String>()

        for (i in validCategories.indices) {
            val catA = validCategories[i]
            for (j in (i + 1) until validCategories.size) {
                val catB = validCategories[j]
                val pairKey = if (catA.tag < catB.tag) "${catA.tag}_${catB.tag}" else "${catB.tag}_${catA.tag}"
                if (processedPairs.contains(pairKey)) continue

                val tagA = catA.tag.lowercase()
                val tagB = catB.tag.lowercase()

                var isSimilar = false
                var reason = ""

                // 规则 1: 包含子串 (例如 "猫" 和 "猫咪/宠物"，或 "发票" 和 "文档/票据")
                if (tagA.contains(tagB) || tagB.contains(tagA)) {
                    isSimilar = true
                    reason = "名称包含相同主关键词"
                }
                // 规则 2: 编辑距离很近 (例如 "自然风景" vs "自然/风景")
                else if (levenshteinDistance(tagA, tagB) <= 2 && tagA.length >= 3) {
                    isSimilar = true
                    reason = "标签文字高度接近"
                }

                if (isSimilar) {
                    processedPairs.add(pairKey)
                    // 数量少者建议合并入数量多者
                    val (source, target) = if (catA.imageCount >= catB.imageCount) {
                        Pair(catB, catA)
                    } else {
                        Pair(catA, catB)
                    }
                    suggestions.add(
                        MergeSuggestion(
                            sourceTag = source.tag,
                            targetTag = target.tag,
                            reason = reason
                        )
                    )
                }
            }
        }

        return suggestions.take(5)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
