package com.toimg.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.toimg.app.core.di.AppContainer
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.feature.history.TaskHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 用内存数据库经 [AppContainer] 注入，验证 ViewModel / 批处理管理器的数据库相关逻辑。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DbBackedLogicTest {

    private lateinit var context: Context
    private lateinit var db: ToImgDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ToImgDatabase::class.java).allowMainThreadQueries().build()
        AppContainer.overrideDatabaseForTesting(db)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BackgroundBatchManager.dismissFinished()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppContainer.overrideDatabaseForTesting(null)
        db.close()
    }

    private fun img(id: String, status: String) = ImageEntity(
        id = id, uriString = "content://media/$id", name = "$id.jpg", path = "",
        size = 1L, dateModified = 0L, mimeType = "image/jpeg", folderName = "Camera", status = status
    )

    private fun task(type: String) = TaskRecordEntity(
        type = type, status = "SUCCESS", title = "t", totalCount = 1, successCount = 1,
        failedCount = 0, skippedCount = 0, detail = "", startedAt = 0, finishedAt = 1
    )

    @Test
    fun checkAndResumeInterrupted_countsOnlyProcessingImages() = runBlocking {
        db.imageDao().insertImages(listOf(img("a", "PROCESSING"), img("b", "PROCESSING"), img("c", "SCANNED")))
        assertEquals(2, BackgroundBatchManager.checkAndResumeInterrupted(context))
    }

    @Test
    fun checkAndResumeInterrupted_isZeroWhenNothingInterrupted() = runBlocking {
        db.imageDao().insertImages(listOf(img("a", "RECOGNIZED")))
        assertEquals(0, BackgroundBatchManager.checkAndResumeInterrupted(context))
    }

    @Test
    fun startBatchRecognition_ignoresEmptyInput() {
        BackgroundBatchManager.startBatchRecognition(context, emptyList())
        assertEquals(false, BackgroundBatchManager.isRunning())
    }

    @Test
    fun taskHistoryViewModel_splitsRecordsByType() = runBlocking {
        db.taskRecordDao().insert(task(TaskRecordEntity.TYPE_SCAN))
        db.taskRecordDao().insert(task(TaskRecordEntity.TYPE_RECOGNITION))
        db.taskRecordDao().insert(task(TaskRecordEntity.TYPE_RECOGNITION))

        val vm = TaskHistoryViewModel()
        vm.init(context)
        val state = withTimeout(5_000) {
            vm.uiState.first { it.scanRecords.size == 1 && it.recognitionRecords.size == 2 }
        }
        assertEquals(1, state.scanRecords.size)
        assertEquals(2, state.recognitionRecords.size)
    }

    @Test
    fun taskHistoryViewModel_resumeWithNothingInterruptedShowsMessage() = runBlocking {
        val vm = TaskHistoryViewModel()
        vm.resumeInterrupted(context)
        val state = withTimeout(5_000) { vm.uiState.first { it.message != null } }
        assertEquals("没有可恢复的任务。", state.message)
    }
}
