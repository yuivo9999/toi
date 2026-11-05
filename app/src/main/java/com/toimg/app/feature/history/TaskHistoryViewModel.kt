package com.toimg.app.feature.history

import com.toimg.app.core.di.AppContainer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryTab(val label: String) {
    SCAN("扫描"),
    RECOGNITION("AI 识别"),
    MOVE("文件移动")
}

data class TaskHistoryUiState(
    val tab: HistoryTab = HistoryTab.SCAN,
    val scanRecords: List<TaskRecordEntity> = emptyList(),
    val scanSessions: List<ScanSessionEntity> = emptyList(),
    val recognitionRecords: List<TaskRecordEntity> = emptyList(),
    val moveRecords: List<MoveHistoryEntity> = emptyList(),
    val interruptedCount: Int = 0,
    val message: String? = null
)

class TaskHistoryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TaskHistoryUiState())
    val uiState: StateFlow<TaskHistoryUiState> = _uiState.asStateFlow()

    private var started = false

    fun init(context: Context) {
        val appContext = context.applicationContext
        refreshInterrupted(appContext)
        if (started) return
        started = true
        val db = AppContainer.database(appContext)
        viewModelScope.launch {
            db.taskRecordDao().observeByType(TaskRecordEntity.TYPE_SCAN).collect { list ->
                _uiState.update { it.copy(scanRecords = list) }
            }
        }
        viewModelScope.launch {
            db.scanSessionDao().observeHistory().collect { list ->
                _uiState.update { it.copy(scanSessions = list) }
            }
        }
        viewModelScope.launch {
            db.taskRecordDao().observeByType(TaskRecordEntity.TYPE_RECOGNITION).collect { list ->
                _uiState.update { it.copy(recognitionRecords = list) }
            }
        }
        viewModelScope.launch {
            db.moveHistoryDao().getAllHistory().collect { list ->
                _uiState.update { it.copy(moveRecords = list) }
            }
        }
    }

    fun setTab(tab: HistoryTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun refreshInterrupted(context: Context) {
        viewModelScope.launch {
            val n = BackgroundBatchManager.checkAndResumeInterrupted(context.applicationContext)
            _uiState.update { it.copy(interruptedCount = n) }
        }
    }

    /** 用户点击「恢复识别」后才会重新提交（不会自动上传）。 */
    fun resumeInterrupted(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val db = AppContainer.database(appContext)
            val interrupted = db.imageDao().getImagesByStatus("PROCESSING")
            if (interrupted.isEmpty()) {
                _uiState.update { it.copy(interruptedCount = 0, message = "没有可恢复的任务。") }
            } else if (BackgroundBatchManager.isRunning()) {
                _uiState.update { it.copy(message = "已有识别任务正在进行。") }
            } else {
                BackgroundBatchManager.startBatchRecognition(appContext, interrupted)
                _uiState.update { it.copy(interruptedCount = 0, message = "已开始恢复 ${interrupted.size} 张图片的识别。") }
            }
        }
    }

    fun rollbackMove(context: Context, history: MoveHistoryEntity) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = com.toimg.app.core.util.FileMoveUtil.executeRollback(appContext, history)
            val msg = result.getOrElse { it.localizedMessage ?: "撤回失败" }
            _uiState.update { it.copy(message = msg) }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
