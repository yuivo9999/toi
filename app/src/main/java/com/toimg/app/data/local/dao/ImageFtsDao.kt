package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.ImageSearchFtsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageFtsDao {

    @Query("""
        SELECT images.* FROM images
        JOIN image_search_fts ON images.id = image_search_fts.imageId
        WHERE image_search_fts MATCH :ftsQuery
        ORDER BY images.dateModified DESC
    """)
    fun searchByFts(ftsQuery: String): Flow<List<ImageEntity>>

    @Query("""
        SELECT images.* FROM images
        JOIN image_search_fts ON images.id = image_search_fts.imageId
        WHERE image_search_fts MATCH :ftsQuery
        ORDER BY images.dateModified DESC
    """)
    suspend fun searchByFtsSync(ftsQuery: String): List<ImageEntity>

    /**
     * 联合多维过滤查询：结合 FTS 全文索引、文件夹、日期区间、文件尺寸、状态与敏感属性
     */
    @Query("""
        SELECT DISTINCT images.* FROM images
        LEFT JOIN image_search_fts ON images.id = image_search_fts.imageId
        LEFT JOIN recognition_results ON images.id = recognition_results.imageId
        WHERE (:useFts = 0 OR image_search_fts MATCH :ftsQuery OR images.name LIKE '%' || :rawKeyword || '%' OR images.folderName LIKE '%' || :rawKeyword || '%')
          AND (:folderName = '' OR images.folderName = :folderName)
          AND (:startDate <= 0 OR images.dateModified >= :startDate)
          AND (:endDate <= 0 OR images.dateModified <= :endDate)
          AND (:minSize <= 0 OR images.size >= :minSize)
          AND (:maxSize <= 0 OR images.size <= :maxSize)
          AND (:status = '' OR images.status = :status)
          AND (:onlySensitive = 0 OR (recognition_results.isSensitive IS NOT NULL AND recognition_results.isSensitive = 1))
        ORDER BY images.dateModified DESC
    """)
    fun searchWithFilters(
        useFts: Int,
        ftsQuery: String,
        rawKeyword: String,
        folderName: String,
        startDate: Long,
        endDate: Long,
        minSize: Long,
        maxSize: Long,
        status: String,
        onlySensitive: Int
    ): Flow<List<ImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(entry: ImageSearchFtsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFts(entries: List<ImageSearchFtsEntity>): List<Long>

    @Query("DELETE FROM image_search_fts WHERE imageId = :imageId")
    suspend fun deleteFtsByImageId(imageId: String): Int

    @Query("DELETE FROM image_search_fts WHERE imageId IN (:imageIds)")
    suspend fun deleteFtsByImageIds(imageIds: List<String>): Int

    @Query("DELETE FROM image_search_fts")
    suspend fun clearAllFts(): Int

    @Query("SELECT COUNT(*) FROM image_search_fts f LEFT JOIN images i ON i.id = f.imageId WHERE i.id IS NULL")
    suspend fun countOrphanFtsRows(): Int

    @Query("SELECT COUNT(*) FROM images i LEFT JOIN image_search_fts f ON f.imageId = i.id WHERE f.imageId IS NULL")
    suspend fun countImagesMissingFtsRows(): Int

    @Query("""
        INSERT INTO image_search_fts (imageId, keywords, description, ocrText, name, folderName)
        SELECT i.id, COALESCE(r.keywords, ''), COALESCE(r.description, ''), COALESCE(r.ocrText, ''), i.name, i.folderName
        FROM images i
        LEFT JOIN recognition_results r ON i.id = r.imageId
        WHERE i.id = :imageId
    """)
    suspend fun insertFtsForImage(imageId: String): Long

    suspend fun syncFtsForImage(imageId: String) {
        deleteFtsByImageId(imageId)
        insertFtsForImage(imageId)
    }

    @Query("""
        INSERT INTO image_search_fts (imageId, keywords, description, ocrText, name, folderName)
        SELECT i.id, COALESCE(r.keywords, ''), COALESCE(r.description, ''), COALESCE(r.ocrText, ''), i.name, i.folderName
        FROM images i
        LEFT JOIN recognition_results r ON i.id = r.imageId
    """)
    suspend fun insertAllFromImagesAndRecognitions(): Long

    suspend fun rebuildAllFts() {
        clearAllFts()
        insertAllFromImagesAndRecognitions()
    }
}

