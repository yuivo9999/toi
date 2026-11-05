package com.toimg.app.core.util

import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartProposalEngineTest {

    private fun img(id: String) = ImageEntity(
        id = id,
        uriString = "content://media/external/images/media/$id",
        name = "$id.jpg",
        path = "",
        size = 100L,
        dateModified = 0L,
        mimeType = "image/jpeg",
        folderName = "Camera"
    )

    private val rules = listOf(
        SmartRuleEntity(1, "财务", "发票, 收据", "Pictures/财务", "content://tree/1", true),
        SmartRuleEntity(2, "美食", "美食, 咖啡", "Pictures/美食", "", true),
        SmartRuleEntity(3, "禁用", "风景", "Pictures/风景", "", false)
    )

    @Test
    fun groupsImagesByFirstMatchingRule() {
        val groups = SmartProposalEngine.group(
            rules,
            listOf(
                img("a") to listOf("电子发票", "消费"),
                img("b") to listOf("咖啡", "桌面"),
                img("c") to listOf("发票"),
                img("d") to listOf("猫咪")
            )
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("a", "c"), groups[0].images.map { it.id })
        assertEquals(1L, groups[0].rule.id)
        assertEquals(listOf("b"), groups[1].images.map { it.id })
    }

    @Test
    fun disabledRulesAndBlankKeywordsAreIgnored() {
        val groups = SmartProposalEngine.group(
            rules,
            listOf(
                img("x") to listOf("风景"),
                img("y") to listOf("", "  ")
            )
        )
        assertTrue(groups.isEmpty())
    }
}
