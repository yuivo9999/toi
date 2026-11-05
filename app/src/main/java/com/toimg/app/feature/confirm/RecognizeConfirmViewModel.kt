package com.toimg.app.feature.confirm

import com.toimg.app.core.di.AppContainer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.core.util.SelectionHolder
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.repository.AiConfigRepository
import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConfirmItem(
    val image: ImageItem,
    val hasSuccessResult: Boolean,
    val unavailable: Boolean
)

data class RecognizeConfirmUiState(
    val loading: Boolean = true,
    val items: List<ConfirmItem> = emptyList(),
    val includeRecognized: Boolean = false,
    val batchSize: Int = AiConfigRepository.DEFAULT_BATCH_SIZE,
    val aiConfig: AiConfig = AiConfig(),
    val networkAvailable: Boolean = true,
    val message: String? = null
) {
    val total: Int get() = items.size
    val unavailableCount: Int get() = items.count { it.unavailable }
    val alreadyRecognizedCount: Int get() = items.count { it.hasSuccessResult && !it.unavailable }
    val firstTimeCount: Int get() = items.count { !it.hasSuccessResult && !it.unavailable }
    val toProcess: List<ConfirmItem>
        get() = items.filter { !it.unavailable && (includeRecognized || !it.hasSuccessResult) }
    val totalBatches: Int
        get() = if (toProcess.isEmpty()) 0 else (toProcess.size + batchSize - 1) / batchSize
    val isKeyConfigured: Boolean get() = aiConfig.apiKey.isNotBlank()
}

class RecognizeConfirmViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecognizeConfirmUiState())
    val uiState: StateFlow<RecognizeConfirmUiState> = _uiState.asStateFlow()

    private var entityMap: Map<String, ImageEntity> = emptyMap()

    fun load(context: Context) {
        val appContext = context.applicationContext
        val configRepo = AiConfigRepository(appContext)
        _uiState.update {
            it.copy(
                loading = true,
                aiConfig = configRepo.getAiConfig(),
                batchSize = configRepo.getBatchSize(),
                includeRecognized = SelectionHolder.includeRecognized,
                networkAvailable = isNetworkAvailable(appContext)
            )
        }
        viewModelScope.launch {
            val ids = SelectionHolder.ids.value.toList()
            val items = withContext(Dispatchers.IO) {
                val db = AppContainer.database(appContext)
                val entities = ids.chunked(500).flatMap { db.imageDao().getImagesByIds(it) }
                entityMap = entities.associateBy { it.id }
                entities.map { e ->
                    val rec = db.recognitionDao().getRecognitionByImageId(e.id)
                    ConfirmItem(
                        image = ImageItem(
                            id = e.id,
                            uri = Uri.parse(e.uriString),
                            name = e.name,
                            path = e.path,
                            size = e.size,
                            dateModified = e.dateModified,
                            mimeType = e.mimeType,
                            folderName = e.folderName,
                            folderPath = e.folderPath,
                            sha256 = e.sha256,
                            status = e.status
                        ),
                        hasSuccessResult = rec?.status == "SUCCESS",
                        unavailable = e.status == "UNAVAILABLE"
                    )
                }
            }
            _uiState.update { it.copy(loading = false, items = items) }
        }
    }

    fun setBatchSize(size: Int) {
        _uiState.update { it.copy(batchSize = size) }
    }

    fun setIncludeRecognized(include: Boolean) {
        _uiState.update { it.copy(includeRecognized = include) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 用户点击「开始识别」。成功启动返回 true，由页面跳转到工作区查看进度。 */
    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        val s = _uiState.value
        if (!s.isKeyConfigured) {
            _uiState.update { it.copy(message = "尚未配置 AI API Key，请先到「设置」中填写并测试连接。") }
            return false
        }
        if (BackgroundBatchManager.isRunning()) {
            _uiState.update { it.copy(message = "已有识别任务正在进行，请等它结束或先取消。") }
            return false
        }
        if (!isNetworkAvailable(appContext)) {
            _uiState.update { it.copy(networkAvailable = false, message = "当前没有可用网络，无法发送识别请求。") }
            return false
        }
        val targets = s.toProcess.mapNotNull { entityMap[it.image.id] }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(message = "没有需要识别的图片（已识别的图片默认不会重复提交）。") }
            return false
        }
        AiConfigRepository(appContext).saveBatchSize(s.batchSize)
        BackgroundBatchManager.dismissFinished()
        BackgroundBatchManager.startBatchRecognition(appContext, targets, s.batchSize)
        SelectionHolder.clear()
        return true
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            true
        }
    }
}
