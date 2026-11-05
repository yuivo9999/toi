package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.toimg.app.data.local.entity.SmartRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartRuleDao {

    @Query("SELECT * FROM smart_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<SmartRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SmartRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<SmartRuleEntity>): List<Long>

    @Query("DELETE FROM smart_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long): Int

    @Query("UPDATE smart_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateRuleStatus(id: Long, isEnabled: Boolean): Int

    @Query("UPDATE smart_rules SET targetTreeUri = :treeUri, targetFolderName = :folderName WHERE id = :id")
    suspend fun updateTarget(id: Long, treeUri: String, folderName: String): Int
}
