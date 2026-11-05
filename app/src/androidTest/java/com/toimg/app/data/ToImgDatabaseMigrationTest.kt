package com.toimg.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.toimg.app.data.local.ToImgDatabase
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * 迁移测试。依赖 exportSchema=true 导出的 app/schemas/<db>/<version>.json
 * （首次构建后生成，请提交到版本库；以后每新增一个版本都要提交对应 JSON 并在此补一条 migrate_x_to_y）。
 */
class ToImgDatabaseMigrationTest {

    private val dbName = "migration-test"
    private val currentVersion = 10

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ToImgDatabase::class.java
    )

    private fun hasSchema(version: Int): Boolean = try {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.toimg.app.data.local.ToImgDatabase/$version.json").close()
        true
    } catch (e: Exception) {
        false
    }

    /** 当前版本的结构能被正常创建并通过 Room 校验（会带着全部迁移一起校验）。 */
    @Test
    fun latestSchema_createsAndValidates() {
        assumeTrue("缺少 $currentVersion.json，请先构建一次并提交 app/schemas", hasSchema(currentVersion))
        helper.createDatabase(dbName, currentVersion).close()
        helper.runMigrationsAndValidate(dbName, currentVersion, true, *ToImgDatabase.ALL_MIGRATIONS).close()
    }

    /** v5 -> v6：新增 FTS 表，并从已有 images / recognition_results 回填索引。 */
    @Test
    fun migrate_5_to_6_backfillsSearchIndex() {
        assumeTrue("缺少 5.json（仅当历史上导出过 v5 结构时可跑）", hasSchema(5))
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO images (id, uriString, name, path, size, dateModified, mimeType, folderName, " +
                    "folderPath, status, createdAt) VALUES ('1','content://x/1','a.jpg','',1,0,'image/jpeg','Cam','','RECOGNIZED',0)"
            )
            execSQL(
                "INSERT INTO recognition_results (imageId, keywords, description, modelName, status, isSensitive, recognizedAt) " +
                    "VALUES ('1','猫咪,宠物','一只猫','m','SUCCESS',0,0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 6, true, *ToImgDatabase.ALL_MIGRATIONS)
        db.query("SELECT COUNT(*) FROM image_search_fts WHERE image_search_fts MATCH '猫咪'").use {
            assertTrue(it.moveToFirst() && it.getInt(0) == 1)
        }
    }

    @Test
    fun migrate_6_to_7_createsPersistentScanSessionSchema() {
        assumeTrue("缺少 6.json", hasSchema(6))
        assumeTrue("缺少 7.json", hasSchema(7))
        helper.createDatabase(dbName, 6).close()
        val db = helper.runMigrationsAndValidate(dbName, 7, true, *ToImgDatabase.ALL_MIGRATIONS)
        db.query("PRAGMA table_info(scan_sessions)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertTrue(columns.contains("id"))
            assertTrue(columns.contains("status"))
            assertTrue(columns.contains("startedAt"))
        }
        db.close()
    }

    @Test
    fun migrate_7_to_8_createsSourceLinkAndPerSourceStatsSchema() {
        assumeTrue("缺少 7.json", hasSchema(7))
        assumeTrue("缺少 8.json", hasSchema(8))
        helper.createDatabase(dbName, 7).close()
        val db = helper.runMigrationsAndValidate(dbName, 8, true, *ToImgDatabase.ALL_MIGRATIONS)
        listOf("image_sources", "image_source_links", "source_scan_stats").forEach { table ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use {
                assertTrue("missing table $table", it.moveToFirst())
            }
        }
        db.close()
    }


    @Test
    fun migrate_8_to_9_addsSourceLifecycleAuditAndSourceNameSnapshot() {
        assumeTrue("缺少 8.json", hasSchema(8))
        assumeTrue("缺少 9.json；请在 Android Studio/KSP 构建后核对导出", hasSchema(9))
        helper.createDatabase(dbName, 8).close()
        val db = helper.runMigrationsAndValidate(dbName, 9, true, *ToImgDatabase.ALL_MIGRATIONS)
        db.query("PRAGMA table_info(scan_sessions)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertTrue(columns.contains("selectedSourceNames"))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='source_lifecycle_events'").use {
            assertTrue(it.moveToFirst())
        }
        db.close()
    }

    @Test
    fun migrate_9_to_10_addsPersistedScanMode() {
        assumeTrue("缺少 9.json", hasSchema(9))
        assumeTrue("缺少 10.json；请在 Android Studio/KSP 构建后核对导出", hasSchema(10))
        helper.createDatabase(dbName, 9).close()
        val db = helper.runMigrationsAndValidate(dbName, 10, true, *ToImgDatabase.ALL_MIGRATIONS)
        db.query("PRAGMA table_info(scan_sessions)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertTrue(columns.contains("scanMode"))
        }
        db.query("SELECT scanMode FROM scan_sessions LIMIT 1").use { cursor ->
            assertTrue("空会话表查询应正常执行", !cursor.moveToFirst())
        }
        db.close()
    }

    /** v6 -> v8：旧文件夹必须恢复为来源，并把旧图片索引回填到多对多关联表。 */
    @Test
    fun migrate_6_to_8_backfillsLegacyFolderImageLinks() {
        assumeTrue("缺少 6.json", hasSchema(6))
        assumeTrue("缺少 8.json；请在 Android Studio 构建一次以导出当前 Room schema", hasSchema(8))
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO folders (id,name,uriString,path,imageCount,isAuthorized,lastScannedAt) VALUES ('dcim','DCIM','content://provider/tree/primary%3ADCIM','','2',1,100)")
            execSQL("INSERT INTO images (id,uriString,name,path,size,dateModified,mimeType,folderName,folderPath,status,createdAt) VALUES ('img-1','content://provider/tree/primary%3ADCIM/document/primary%3ADCIM%2Fa.jpg','a.jpg','','10',100,'image/jpeg','DCIM','DCIM','SCANNED',100)")
            execSQL("INSERT INTO images (id,uriString,name,path,size,dateModified,mimeType,folderName,folderPath,status,createdAt) VALUES ('img-2','content://provider/tree/primary%3ADCIM/document/primary%3ADCIM%2Fb.jpg','b.jpg','','20',200,'image/jpeg','DCIM','DCIM','SCANNED',200)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 8, true, *ToImgDatabase.ALL_MIGRATIONS)
        db.query("SELECT COUNT(*) FROM image_source_links WHERE sourceId = 'dcim'").use {
            assertTrue(it.moveToFirst() && it.getInt(0) == 2)
        }
        db.query("SELECT imageCount FROM image_sources WHERE id = 'dcim'").use {
            assertTrue(it.moveToFirst() && it.getInt(0) == 2)
        }
    }

    @Test
    fun migrationChain_startsAtSupportedBoundaryAndEndsAtCurrentVersion() {
        val ms = ToImgDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertTrue("正式迁移链应从 v5 开始", ms.first().startVersion == 5)
        assertTrue("正式迁移链应到达当前版本", ms.last().endVersion == currentVersion)
        assertTrue("v4 不得同时配置破坏性回退和 Migration", ms.none {
            it.startVersion == 4 || it.endVersion == 4
        })
    }
}
