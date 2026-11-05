package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.toimg.app.data.local.entity.RecognitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionDao {

    @Query("SELECT * FROM recognition_results ORDER BY recognizedAt DESC")
    fun getAllRecognitions(): Flow<List<RecognitionEntity>>

    @Query("SELECT * FROM recognition_results ORDER BY recognizedAt DESC")
    suspend fun getAllRecognitionsSync(): List<RecognitionEntity>

    @Query("SELECT * FROM recognition_results WHERE imageId = :imageId LIMIT 1")
    suspend fun getRecognitionByImageId(imageId: String): RecognitionEntity?

    @Query("SELECT * FROM recognition_results WHERE status = 'SUCCESS' ORDER BY recognizedAt DESC")
    fun getSuccessfulRecognitions(): Flow<List<RecognitionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecognitions(items: List<RecognitionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecognition(item: RecognitionEntity): Long

    @Query("UPDATE recognition_results SET keywords = :keywords WHERE imageId = :imageId")
    suspend fun updateKeywords(imageId: String, keywords: String): Int

    @Query("SELECT * FROM recognition_results WHERE keywords LIKE '%' || :keyword || '%'")
    suspend fun getRecognitionsWithKeyword(keyword: String): List<RecognitionEntity>

    @Query("DELETE FROM recognition_results WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: String): Int

    @Query("DELETE FROM recognition_results WHERE imageId IN (:imageIds)")
    suspend fun deleteByImageIds(imageIds: List<String>): Int

    @Query("DELETE FROM recognition_results")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(imageId) FROM recognition_results WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(imageId) FROM recognition_results WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recognition_results r LEFT JOIN images i ON i.id = r.imageId WHERE i.id IS NULL")
    suspend fun countOrphanRecognitions(): Int

    @Query("SELECT COUNT(*) FROM images i WHERE i.status = 'RECOGNIZED' AND NOT EXISTS (SELECT 1 FROM recognition_results r WHERE r.imageId = i.id AND r.status = 'SUCCESS')")
    suspend fun countRecognizedImagesMissingSuccess(): Int

    @Query("SELECT COUNT(*) FROM recognition_results r INNER JOIN images i ON i.id = r.imageId WHERE r.status = 'SUCCESS' AND i.status IN ('SCANNED','FAILED')")
    suspend fun countSuccessResultsWithStaleImageStatus(): Int

    /** Explicit repair only: restore image status from durable recognition result rows. */
    @Query("UPDATE images SET status = 'RECOGNIZED' WHERE id IN (SELECT imageId FROM recognition_results WHERE status = 'SUCCESS') AND status IN ('SCANNED','FAILED')")
    suspend fun restoreRecognizedImageStatuses(): Int

    /** A RECOGNIZED flag without a successful result is downgraded; failed result rows remain FAILED. */
    @Query("UPDATE images SET status = CASE WHEN EXISTS (SELECT 1 FROM recognition_results r WHERE r.imageId = images.id AND r.status = 'FAILED') THEN 'FAILED' ELSE 'SCANNED' END WHERE status = 'RECOGNIZED' AND NOT EXISTS (SELECT 1 FROM recognition_results r WHERE r.imageId = images.id AND r.status = 'SUCCESS')")
    suspend fun downgradeImagesMissingRecognition(): Int
}
