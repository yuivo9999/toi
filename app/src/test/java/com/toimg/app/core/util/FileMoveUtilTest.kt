package com.toimg.app.core.util

import com.toimg.app.data.local.entity.SmartRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileMoveUtilTest {

    @Test
    fun matchSmartRule_matchesKeywordTriggers() {
        val rules = listOf(
            SmartRuleEntity(1, "发票归档", "发票, 报销, 收据", "财务票据", "content://tree/1", true),
            SmartRuleEntity(2, "宠物相册", "猫咪, 狗狗", "可爱宠物", "content://tree/2", true),
            SmartRuleEntity(3, "禁用规则", "风景", "自然风景", "content://tree/3", false)
        )

        val matchReceipt = FileMoveUtil.matchSmartRule(listOf("电子发票", "消费"), rules)
        assertNotNull(matchReceipt)
        assertEquals("发票归档", matchReceipt?.name)

        val matchPet = FileMoveUtil.matchSmartRule(listOf("可爱猫咪"), rules)
        assertNotNull(matchPet)
        assertEquals("宠物相册", matchPet?.name)

        val matchDisabled = FileMoveUtil.matchSmartRule(listOf("风景"), rules)
        assertNull(matchDisabled)
    }

    @Test
    fun matchSmartRule_returnsNullWhenNoKeywordsMatch() {
        val rules = listOf(
            SmartRuleEntity(1, "发票归档", "发票, 报销", "财务票据", "content://tree/1", true)
        )
        val match = FileMoveUtil.matchSmartRule(listOf("美食", "火锅"), rules)
        assertNull(match)
    }
}
