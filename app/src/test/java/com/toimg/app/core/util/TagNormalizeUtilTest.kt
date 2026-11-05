package com.toimg.app.core.util

import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNormalizeUtilTest {

    @Test
    fun normalizeTag_mapsKnownSynonymsToStandardCategories() {
        assertEquals("猫咪/宠物", TagNormalizeUtil.normalizeTag("猫咪"))
        assertEquals("猫咪/宠物", TagNormalizeUtil.normalizeTag("#cat"))
        assertEquals("狗狗/宠物", TagNormalizeUtil.normalizeTag("狗"))
        assertEquals("代码与截图", TagNormalizeUtil.normalizeTag("code"))
        assertEquals("美食/餐品", TagNormalizeUtil.normalizeTag("美食"))
        assertEquals("自然/风景", TagNormalizeUtil.normalizeTag("landscape"))
        assertEquals("文档/票据", TagNormalizeUtil.normalizeTag("发票"))
        assertEquals("文档/票据", TagNormalizeUtil.normalizeTag("收据"))
        assertEquals("人物/合照", TagNormalizeUtil.normalizeTag("自拍"))
    }

    @Test
    fun normalizeTag_handlesBlankOrUnknownTags() {
        assertEquals("其他分类", TagNormalizeUtil.normalizeTag("   "))
        assertEquals("自定义标签", TagNormalizeUtil.normalizeTag("自定义标签"))
    }

    @Test
    fun normalizeTag_cleansHashtagsAndPunctuation() {
        assertEquals("美食/餐品", TagNormalizeUtil.normalizeTag("#美食,"))
        assertEquals("代码与截图", TagNormalizeUtil.normalizeTag("CODE."))
    }

    @Test
    fun normalizeTag_withCustomSynonyms() {
        val customMap = mapOf(
            "咪咪" to "猫咪/宠物",
            "打工日常" to "职场工作"
        )
        // Custom synonym should take precedence
        assertEquals("猫咪/宠物", TagNormalizeUtil.normalizeTag("咪咪", customMap))
        assertEquals("职场工作", TagNormalizeUtil.normalizeTag("打工日常", customMap))
        // Default built-in still works
        assertEquals("自然/风景", TagNormalizeUtil.normalizeTag("风景", customMap))
    }

    @Test
    fun detectMergeSuggestions_detectsSubstringOverlap() {
        val cat1 = com.toimg.app.domain.model.CategoryItem(
            tag = "发票",
            displayName = "发票",
            imageCount = 5,
            imageIds = listOf("1", "2", "3", "4", "5")
        )
        val cat2 = com.toimg.app.domain.model.CategoryItem(
            tag = "发票/收据",
            displayName = "发票/收据",
            imageCount = 20,
            imageIds = (1..20).map { "$it" }
        )

        val suggestions = TagNormalizeUtil.detectMergeSuggestions(listOf(cat1, cat2))
        assertEquals(1, suggestions.size)
        assertEquals("发票", suggestions[0].sourceTag)
        assertEquals("发票/收据", suggestions[0].targetTag)
    }
}
