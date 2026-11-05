package com.toimg.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.toimg.app.data.local.dao.FolderDao
import com.toimg.app.data.local.dao.ImageDao
import com.toimg.app.data.local.dao.MoveHistoryDao
import com.toimg.app.data.local.dao.RecognitionDao
import com.toimg.app.data.local.dao.SmartRuleDao
import com.toimg.app.data.local.dao.TaskRecordDao
import com.toimg.app.data.local.dao.ScanSessionDao
import com.toimg.app.data.local.dao.ImageFtsDao
import com.toimg.app.data.local.entity.FolderEntity
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.ImageSearchFtsEntity
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.local.entity.RecognitionEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.data.local.entity.ScanSessionEntity
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageSourceLinkEntity
import com.toimg.app.data.local.entity.SourceScanStatEntity
import com.toimg.app.data.local.entity.SourceLifecycleEventEntity
import com.toimg.app.data.local.dao.SourceLifecycleEventDao
import com.toimg.app.data.local.dao.ImageSourceDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ImageEntity::class,
        FolderEntity::class,
        RecognitionEntity::class,
        MoveHistoryEntity::class,
        SmartRuleEntity::class,
        TaskRecordEntity::class,
        ImageSearchFtsEntity::class,
        ScanSessionEntity::class,
        ImageSourceEntity::class,
        ImageSourceLinkEntity::class,
        SourceScanStatEntity::class,
        SourceLifecycleEventEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class ToImgDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    abstract fun imageFtsDao(): ImageFtsDao
    abstract fun folderDao(): FolderDao
    abstract fun recognitionDao(): RecognitionDao
    abstract fun moveHistoryDao(): MoveHistoryDao
    abstract fun smartRuleDao(): SmartRuleDao
    abstract fun taskRecordDao(): TaskRecordDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun imageSourceDao(): ImageSourceDao
    abstract fun sourceLifecycleEventDao(): SourceLifecycleEventDao

    companion object {
        @Volatile
        private var INSTANCE: ToImgDatabase? = null

        /*
         * v1~v4 属于开发期数据库，历史结构无法可靠映射到当前实体，升级时重建本地索引。
         * 注意：这些版本号不能同时出现在任何已注册 Migration 的起始/结束版本中。
         * v5 是正式可迁移边界，保留 5->6 迁移以保护现有索引数据。
         */
        private val LEGACY_VERSIONS = intArrayOf(1, 2, 3, 4)

        /** v5 -> v6：新增 FTS4 全文检索表，自动索引 keywords、description、ocrText、name、folderName */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `image_search_fts` USING FTS4(" +
                        "`imageId` TEXT NOT NULL, " +
                        "`keywords` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`ocrText` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`folderName` TEXT NOT NULL)"
                )
                // 从现有的 images 与 recognition_results 中初始化 FTS 倒排索引
                db.execSQL(
                    "INSERT INTO `image_search_fts` (`imageId`, `keywords`, `description`, `ocrText`, `name`, `folderName`) " +
                        "SELECT i.id, COALESCE(r.keywords, ''), COALESCE(r.description, ''), COALESCE(r.ocrText, ''), i.name, i.folderName " +
                        "FROM images i LEFT JOIN recognition_results r ON i.id = r.imageId"
                )
            }
        }

        /** v6 -> v7：新增持久化扫描会话表，不触碰既有图片索引。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `scan_sessions` (
                        `id` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `includeMediaStore` INTEGER NOT NULL,
                        `folderUris` TEXT NOT NULL,
                        `includeSubfolders` INTEGER NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `phase` TEXT NOT NULL,
                        `currentSource` TEXT NOT NULL,
                        `currentDirectory` TEXT NOT NULL,
                        `discoveredCount` INTEGER NOT NULL,
                        `skippedCount` INTEGER NOT NULL,
                        `unavailableCount` INTEGER NOT NULL,
                        `sourcesDone` INTEGER NOT NULL,
                        `sourcesTotal` INTEGER NOT NULL,
                        `statusText` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        /** v7 -> v8：建立来源生命周期、多对多关联及逐来源扫描统计；保留全部旧图片数据。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `image_sources` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `uriString` TEXT NOT NULL, `treeUriString` TEXT NOT NULL, `isAuthorized` INTEGER NOT NULL, `includeSubfolders` INTEGER NOT NULL, `lifecycleStatus` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `lastScannedAt` INTEGER, `lastScanSessionId` TEXT, `imageCount` INTEGER NOT NULL, `errorMessage` TEXT, PRIMARY KEY(`id`))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `image_source_links` (`imageId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `firstSeenAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`imageId`, `sourceId`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_source_links_sourceId` ON `image_source_links` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_source_links_imageId` ON `image_source_links` (`imageId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `source_scan_stats` (`scanSessionId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `status` TEXT NOT NULL, `discoveredCount` INTEGER NOT NULL, `addedCount` INTEGER NOT NULL, `updatedCount` INTEGER NOT NULL, `existingCount` INTEGER NOT NULL, `skippedCount` INTEGER NOT NULL, `unavailableCount` INTEGER NOT NULL, `failedCount` INTEGER NOT NULL, `startedAt` INTEGER, `finishedAt` INTEGER, `errorMessage` TEXT, PRIMARY KEY(`scanSessionId`, `sourceId`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_scan_stats_sourceId` ON `source_scan_stats` (`sourceId`)")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `selectedSourceIds` TEXT NOT NULL DEFAULT ''")
                // 兼容旧 folders 表：先恢复来源主记录，再把旧图片索引回填到来源关联表。
                // 不做此回填的话，用户升级后立即移出来源会因“没有关联图片”而留下孤立索引。
                db.execSQL("""INSERT OR IGNORE INTO image_sources (id, displayName, uriString, treeUriString, isAuthorized, includeSubfolders, lifecycleStatus, addedAt, lastScannedAt, lastScanSessionId, imageCount, errorMessage) SELECT id, name, uriString, uriString, isAuthorized, 1, CASE WHEN isAuthorized = 1 THEN 'UNSCANNED' ELSE 'AUTH_EXPIRED' END, COALESCE(lastScannedAt, 0), lastScannedAt, NULL, imageCount, NULL FROM folders WHERE uriString IS NOT NULL AND uriString <> ''""")
                db.execSQL("""INSERT OR IGNORE INTO image_source_links (imageId, sourceId, firstSeenAt, lastSeenAt)
                    SELECT i.id, s.id, COALESCE(i.createdAt, s.addedAt), COALESCE(i.createdAt, s.addedAt)
                    FROM images i INNER JOIN image_sources s
                    ON s.uriString <> ''
                    AND substr(i.uriString, 1, length(rtrim(s.uriString, '/') || '/document/')) = rtrim(s.uriString, '/') || '/document/'""".trimIndent())
                db.execSQL("""UPDATE image_sources SET imageCount = (
                    SELECT COUNT(*) FROM image_source_links l WHERE l.sourceId = image_sources.id
                )""".trimIndent())
            }
        }

        /** v8 -> v9：增加来源生命周期审计表，并为扫描会话保存来源名称快照。 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `selectedSourceNames` TEXT NOT NULL DEFAULT ''")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `source_lifecycle_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `linkedImageCount` INTEGER NOT NULL,
                    `deletedImageCount` INTEGER NOT NULL,
                    `retainedSharedImageCount` INTEGER NOT NULL,
                    `deviceFilesDeleted` INTEGER NOT NULL,
                    `detail` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_lifecycle_events_sourceId` ON `source_lifecycle_events` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_lifecycle_events_createdAt` ON `source_lifecycle_events` (`createdAt`)")
            }
        }

        /** v9 -> v10：持久化用户选择的扫描策略；历史会话默认核对模式，避免恢复任务行为突变。 */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `scanMode` TEXT NOT NULL DEFAULT 'VERIFY_EXISTING'")
            }
        }

        /** 正式迁移链：v5 -> v6 -> v7 -> v8 -> v9 -> v10。v1-v4 才允许显式重建。 */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

        const val DB_NAME = "toimg_database"

        /** 仅由 [com.toimg.app.core.di.AppContainer] 调用。 */
        internal fun provide(context: Context): ToImgDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToImgDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // 仅开发期 v1-v4 明确允许重建；v5+ 缺迁移时必须失败并保留数据库，禁止静默清库。
                    .fallbackToDestructiveMigrationFrom(*LEGACY_VERSIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
