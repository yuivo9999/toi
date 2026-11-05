package com.toimg.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageSourceLinkEntity
import com.toimg.app.data.local.entity.RecognitionEntity
import com.toimg.app.data.local.entity.SourceScanStatEntity
import com.toimg.app.data.repository.ImageSourceRepositoryImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** F5 实际 Room + Repository 行为测试：验证移出来源不会误删其他来源仍引用的图片。 */
class ImageSourceRepositoryBehaviorTest {
    private lateinit var context: Context
    private lateinit var db: ToImgDatabase
    private lateinit var repository: ImageSourceRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ToImgDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ImageSourceRepositoryImpl(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun image(id: String) = ImageEntity(
        id = id,
        uriString = "content://provider/document/$id.jpg",
        name = "$id.jpg",
        path = "",
        size = 100L,
        dateModified = 100L,
        mimeType = "image/jpeg",
        folderName = "DCIM",
        folderPath = "DCIM"
    )

    private fun source(id: String) = ImageSourceEntity(
        id = id,
        displayName = id,
        uriString = "content://provider/tree/$id",
        treeUriString = "content://provider/tree/$id"
    )

    @Test
    fun removeSource_deletesExclusiveIndexButRetainsSharedImageAndRecognition() = runBlocking {
        db.imageSourceDao().insert(source("source-a"))
        db.imageSourceDao().insert(source("source-b"))
        db.imageDao().insertImages(listOf(image("exclusive"), image("shared")))
        db.recognitionDao().insertRecognitions(listOf(
            RecognitionEntity("exclusive", "uniquetag", "exclusive description", "test-model"),
            RecognitionEntity("shared", "sharedtag", "shared description", "test-model")
        ))
        db.imageFtsDao().insertFtsForImage("exclusive")
        db.imageFtsDao().insertFtsForImage("shared")
        db.imageSourceDao().addImageSourceLinks(listOf(
            ImageSourceLinkEntity("exclusive", "source-a"),
            ImageSourceLinkEntity("shared", "source-a"),
            ImageSourceLinkEntity("shared", "source-b")
        ))

        val result = repository.removeSourceFromProject("source-a")

        assertFalse(result.blockedByActiveScan)
        assertEquals(1, result.removedImageCount)
        assertEquals(1, result.retainedSharedImageCount)
        assertNull(db.imageDao().getImageById("exclusive"))
        assertNull(db.recognitionDao().getRecognitionByImageId("exclusive"))
        assertTrue(db.imageFtsDao().searchByFtsSync("uniquetag").isEmpty())
        assertNotNull(db.imageDao().getImageById("shared"))
        assertNotNull(db.recognitionDao().getRecognitionByImageId("shared"))
        assertEquals(1, db.imageSourceDao().countLinksForSource("source-b"))
        assertNull(db.imageSourceDao().getById("source-a"))
        assertNotNull(db.imageSourceDao().getById("source-b"))
        val audit = db.sourceLifecycleEventDao().observeRecent(10)
        val events = audit.first()
        assertEquals(1, events.size)
        assertEquals("source-a", events.first().sourceId)
        assertEquals(2, events.first().linkedImageCount)
        assertEquals(1, events.first().deletedImageCount)
        assertEquals(1, events.first().retainedSharedImageCount)
        assertEquals(0, events.first().deviceFilesDeleted)
    }

    @Test
    fun removeSource_isBlockedWhileSourceHasActiveScan() = runBlocking {
        db.imageSourceDao().insert(source("source-a"))
        db.imageDao().insertImages(listOf(image("still-here")))
        db.imageSourceDao().addImageSourceLinks(listOf(ImageSourceLinkEntity("still-here", "source-a")))
        db.imageSourceDao().upsertScanStat(SourceScanStatEntity(
            scanSessionId = "session-running",
            sourceId = "source-a",
            status = "RUNNING",
            startedAt = System.currentTimeMillis()
        ))

        val result = repository.removeSourceFromProject("source-a")

        assertTrue(result.blockedByActiveScan)
        assertNotNull(db.imageSourceDao().getById("source-a"))
        assertNotNull(db.imageDao().getImageById("still-here"))
        assertEquals(1, db.imageSourceDao().countLinksForSource("source-a"))
        assertEquals(0, db.sourceLifecycleEventDao().countAll())
    }
}
