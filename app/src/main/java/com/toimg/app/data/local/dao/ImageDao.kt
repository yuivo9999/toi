package com.toimg.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.toimg.app.data.local.entity.FolderEntity
import com.toimg.app.data.local.entity.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Query("SELECT * FROM images ORDER BY dateModified DESC")
    fun getAllImages(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images ORDER BY dateModified DESC")
    fun getPagedImages(): PagingSource<Int, ImageEntity>

    @Query("SELECT * FROM images WHERE folderName = :folderName ORDER BY dateModified DESC")
    fun getImagesByFolder(folderName: String): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE folderName = :folderName ORDER BY dateModified DESC")
    fun getPagedImagesByFolder(folderName: String): PagingSource<Int, ImageEntity>

    @Query("SELECT * FROM images")
    suspend fun getAllImagesOnce(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE sha256 IS NULL")
    suspend fun getImagesWithoutHash(): List<ImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>): List<Long>

    @Query("UPDATE images SET sha256 = :sha256 WHERE id = :id")
    suspend fun updateSha256(id: String, sha256: String): Int

    @Query("UPDATE images SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String): Int

    @Query("UPDATE images SET uriString = :uriString, name = :name, path = :path, folderName = :folderName, folderPath = :folderPath, status = :status WHERE id = :id")
    suspend fun updateImageLocation(
        id: String,
        uriString: String,
        name: String,
        path: String,
        folderName: String,
        folderPath: String,
        status: String
    ): Int

    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun getImagesByIds(ids: List<String>): List<ImageEntity>

    /** SAF 子文档 URI 通常以所选 tree URI + /document/ 开头，用于旧索引来源关联回填。 */
    @Query("SELECT * FROM images WHERE substr(uriString, 1, length(:prefix)) = :prefix ORDER BY dateModified DESC")
    suspend fun getImagesByUriPrefix(prefix: String): List<ImageEntity>

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteImageById(id: String): Int

    @Query("DELETE FROM images")
    suspend fun clearAllImages(): Int

    @Query("SELECT * FROM images WHERE id = :id LIMIT 1")
    suspend fun getImageById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE uriString = :uri LIMIT 1")
    suspend fun getImageByUri(uri: String): ImageEntity?

    /**
     * 严格跨入口身份匹配候选。
     * 不得 LIMIT 截断：若同元数据候选超过上限，截断后可能把“实际多候选”误判为唯一候选，
     * 进而错误合并两张不同图片。先用完整元数据缩小范围，再由 resolver 做 SHA-256 校验。
     */
    @Query("SELECT * FROM images WHERE name = :name AND size = :size AND dateModified = :dateModified AND mimeType = :mimeType ORDER BY id")
    suspend fun findIdentityCandidates(name: String, size: Long, dateModified: Long, mimeType: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE status = :status")
    suspend fun getImagesByStatus(status: String): List<ImageEntity>

    @Query("SELECT COUNT(id) FROM images WHERE status = :status")
    fun countImagesByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(id) FROM images WHERE name = :name AND size = :size AND status IN ('MOVED', 'ROLLED_BACK')")
    suspend fun countMovedLike(name: String, size: Long): Int

    @Query("SELECT COUNT(id) FROM images")
    fun getImageCount(): Flow<Int>

    @Query("SELECT COUNT(id) FROM images WHERE sha256 IS NOT NULL")
    fun getHashedCount(): Flow<Int>

    /** 历史版本误索引的缩略图/缓存目录候选，仅按完整目录段匹配。 */
    @Query("""
        SELECT * FROM images WHERE
        lower(path) LIKE '%/.thumbnails/%' OR lower(path) LIKE '%/.thumbnails' OR
        lower(path) LIKE '%/thumbnails/%' OR lower(path) LIKE '%/thumbnails' OR
        lower(path) LIKE '%/.cache/%' OR lower(path) LIKE '%/.cache' OR
        lower(path) LIKE '%/cache/%' OR lower(path) LIKE '%/cache' OR
        lower(path) LIKE '%/.trash/%' OR lower(path) LIKE '%/.trash' OR
        lower(path) LIKE '%/trash/%' OR lower(path) LIKE '%/trash' OR
        lower(path) LIKE '%/temp/%' OR lower(path) LIKE '%/temp' OR
        lower(path) LIKE '%/tmp/%' OR lower(path) LIKE '%/tmp' OR
        lower(folderPath) LIKE '%/.thumbnails/%' OR lower(folderPath) LIKE '%/.thumbnails' OR
        lower(folderPath) LIKE '%/thumbnails/%' OR lower(folderPath) LIKE '%/thumbnails' OR
        lower(folderPath) LIKE '%/.cache/%' OR lower(folderPath) LIKE '%/.cache' OR
        lower(folderPath) LIKE '%/cache/%' OR lower(folderPath) LIKE '%/cache' OR
        lower(folderPath) LIKE '%/.trash/%' OR lower(folderPath) LIKE '%/.trash' OR
        lower(folderPath) LIKE '%/trash/%' OR lower(folderPath) LIKE '%/trash' OR
        lower(folderPath) LIKE '%/temp/%' OR lower(folderPath) LIKE '%/temp' OR
        lower(folderPath) LIKE '%/tmp/%' OR lower(folderPath) LIKE '%/tmp'
        ORDER BY folderPath, name
    """)
    suspend fun getLegacyExcludedCandidates(): List<ImageEntity>

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteImagesByIds(ids: List<String>): Int
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>): List<Long>

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String): Int
}
