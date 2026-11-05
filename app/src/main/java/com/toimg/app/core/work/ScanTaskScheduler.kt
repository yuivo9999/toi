package com.toimg.app.core.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.toimg.app.core.di.AppContainer
import com.toimg.app.core.util.ScanManager
import com.toimg.app.core.util.ScanMode
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ScanSessionEntity
import com.toimg.app.data.local.entity.SourceScanStatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 统一管理扫描任务；先在 Room 中预留来源与 QUEUED 统计，再提交 WorkManager。 */
object ScanTaskScheduler {
    private const val PREFS = "toimg_scan_task"
    private const val KEY_ACTIVE = "active"
    private const val KEY_SESSION = "session_id"
    private const val KEY_CANCEL_REQUESTED = "cancel_requested"
    private const val KEY_GENERATION = "generation"
    private val prefsMonitor = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    fun hasActiveTask(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun activeSessionId(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SESSION, null)

    /** 返回 true 表示任务已接受；来源/会话预留成功后才会真正入队。 */
    fun enqueue(context: Context, includeMediaStore: Boolean, folderUris: List<Uri>, includeSubfolders: Boolean, scanMode: ScanMode = ScanMode.FAST_INCREMENTAL): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sessionId = UUID.randomUUID().toString()
        var generation = 0
        synchronized(prefsMonitor) {
            if (prefs.getBoolean(KEY_ACTIVE, false)) return true
            generation = prefs.getInt(KEY_GENERATION, 0)
            if (!prefs.edit().putBoolean(KEY_ACTIVE, true).putString(KEY_SESSION, sessionId)
                    .putBoolean(KEY_CANCEL_REQUESTED, false).commit()) return false
        }
        val data = Data.Builder()
            .putString(ScanWorker.KEY_SESSION_ID, sessionId)
            .putBoolean(ScanWorker.KEY_MEDIA_STORE, includeMediaStore)
            .putStringArray(ScanWorker.KEY_FOLDER_URIS, folderUris.map(Uri::toString).toTypedArray())
            .putBoolean(ScanWorker.KEY_INCLUDE_SUBFOLDERS, includeSubfolders)
            .putString(ScanWorker.KEY_SCAN_MODE, scanMode.persistedValue)
            .build()
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(data)
            .addTag(ScanWorker.UNIQUE_WORK_NAME)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        ioScope.launch {
            lifecycleMutex.withLock {
            try {
                if (prefs.getInt(KEY_GENERATION, 0) != generation || prefs.getBoolean(KEY_CANCEL_REQUESTED, false)) {
                    markFinished(app, sessionId)
                    return@withLock
                }
                val db = AppContainer.database(app)
                val now = System.currentTimeMillis()
                val sourceIds = mutableListOf<String>()
                val sourceNames = mutableListOf<String>()
                db.withTransaction {
                    val session = ScanSessionEntity(
                        id = sessionId,
                        status = ScanSessionEntity.QUEUED,
                        includeMediaStore = includeMediaStore,
                        folderUris = folderUris.joinToString("\n") { it.toString() },
                        includeSubfolders = includeSubfolders,
                        startedAt = now,
                        updatedAt = now,
                        statusText = "扫描任务已排队，等待后台执行",
                        scanMode = scanMode.persistedValue
                    )
                    db.scanSessionDao().upsert(session)
                    val sourceDao = db.imageSourceDao()
                    if (includeMediaStore) {
                        val id = ImageSourceEntity.MEDIASTORE_SOURCE_ID
                        val old = sourceDao.getById(id)
                        val source = ImageSourceEntity(
                            id = id, displayName = "系统媒体库", uriString = ImageSourceEntity.MEDIASTORE_URI,
                            treeUriString = ImageSourceEntity.MEDIASTORE_URI, isAuthorized = true,
                            includeSubfolders = true, lifecycleStatus = ImageSourceEntity.SCANNING,
                            addedAt = old?.addedAt ?: now
                        )
                        if (old == null) sourceDao.insert(source) else sourceDao.update(source)
                        sourceIds += id
                        sourceNames += source.displayName
                        sourceDao.upsertScanStat(SourceScanStatEntity(sessionId, id, status = "QUEUED", startedAt = now))
                    }
                    folderUris.forEach { uri ->
                        val raw = uri.toString()
                        val old = sourceDao.getByUri(raw) ?: sourceDao.getById(raw)
                        val id = old?.id ?: raw
                        val name = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull()
                            ?: old?.displayName ?: "SAF 目录"
                        val source = if (old == null) ImageSourceEntity(
                            id = id, displayName = name, uriString = raw, treeUriString = raw,
                            isAuthorized = true, includeSubfolders = includeSubfolders,
                            lifecycleStatus = ImageSourceEntity.SCANNING, addedAt = now
                        ) else old.copy(
                            isAuthorized = true, includeSubfolders = includeSubfolders,
                            lifecycleStatus = ImageSourceEntity.SCANNING, errorMessage = null
                        )
                        if (old == null) sourceDao.insert(source) else sourceDao.update(source)
                        sourceIds += id
                        sourceNames += name.replace("\n", " ").replace("\r", " ")
                        sourceDao.upsertScanStat(SourceScanStatEntity(sessionId, id, status = "QUEUED", startedAt = now))
                    }
                    db.scanSessionDao().upsert(session.copy(
                        selectedSourceIds = sourceIds.joinToString("\n"),
                        selectedSourceNames = sourceNames.joinToString("\n")
                    ))
                }
                if (prefs.getInt(KEY_GENERATION, 0) != generation || prefs.getBoolean(KEY_CANCEL_REQUESTED, false)) {
                    db.withTransaction {
                        db.scanSessionDao().getById(sessionId)?.let { queued ->
                            db.scanSessionDao().upsert(queued.copy(
                                status = ScanSessionEntity.CANCELLED, updatedAt = System.currentTimeMillis(),
                                finishedAt = System.currentTimeMillis(), statusText = "任务在入队前已取消"
                            ))
                        }
                        db.imageSourceDao().cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "任务在入队前已取消")
                    }
                    markFinished(app, sessionId)
                    return@withLock
                }
                WorkManager.getInstance(app).enqueueUniqueWork(ScanWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
            } catch (error: Exception) {
                runCatching {
                    val db = AppContainer.database(app)
                    db.withTransaction {
                        val session = db.scanSessionDao().getById(sessionId)
                        if (session != null) db.scanSessionDao().upsert(session.copy(
                            status = ScanSessionEntity.FAILED, updatedAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(), statusText = "扫描任务入队失败",
                            errorMessage = error.localizedMessage ?: error.javaClass.simpleName
                        ))
                        db.imageSourceDao().cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "扫描任务入队失败")
                        db.imageSourceDao().markSessionSourcesPartial(sessionId, "扫描任务入队失败")
                    }
                }
                markFinished(app, sessionId)
            }
            }
        }
        return true
    }

    fun pause(context: Context) {
        ScanManager.pause()
        updateActiveStatus(context, ScanSessionEntity.PAUSED, "扫描已暂停")
    }

    fun resume(context: Context) {
        ScanManager.resume()
        updateActiveStatus(context, ScanSessionEntity.RUNNING, "正在继续扫描")
    }

    /** 取消完成前不释放 active 标记，避免旧任务尚未退出时新任务被 KEEP 丢弃。 */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var sessionIdToCancel: String? = null
        synchronized(prefsMonitor) {
            if (!prefs.getBoolean(KEY_ACTIVE, false)) return
            sessionIdToCancel = prefs.getString(KEY_SESSION, null)
            prefs.edit()
                .putBoolean(KEY_CANCEL_REQUESTED, true)
                .putInt(KEY_GENERATION, prefs.getInt(KEY_GENERATION, 0) + 1)
                .commit()
        }
        ScanManager.cancel()
        ioScope.launch {
            lifecycleMutex.withLock {
            val sessionId = sessionIdToCancel
            if (sessionId != null) runCatching {
                val db = AppContainer.database(app)
                db.scanSessionDao().getById(sessionId)?.let { session ->
                    db.scanSessionDao().upsert(session.copy(
                        status = ScanSessionEntity.CANCELLED, updatedAt = System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(), statusText = "扫描已取消"
                    ))
                }
            }
            val cancellationConfirmed = runCatching {
                WorkManager.getInstance(app).cancelUniqueWork(ScanWorker.UNIQUE_WORK_NAME).result.get()
                true
            }.getOrDefault(false)
            if (!cancellationConfirmed) return@withLock
            if (sessionId != null) runCatching {
                val db = AppContainer.database(app)
                db.withTransaction {
                    db.imageSourceDao().cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "扫描已取消，已写入索引予以保留")
                    db.imageSourceDao().markSessionSourcesPartial(sessionId, "扫描已取消；已建立的图片索引予以保留")
                }
            }
            markFinished(app, sessionId, cancellationCompleted = true)
            }
        }
    }

    private fun updateActiveStatus(context: Context, status: String, text: String) {
        ioScope.launch {
            runCatching {
                val sessionId = activeSessionId(context) ?: return@runCatching
                val dao = AppContainer.database(context.applicationContext).scanSessionDao()
                val session = dao.getById(sessionId) ?: return@runCatching
                dao.upsert(session.copy(status = status, updatedAt = System.currentTimeMillis(),
                    finishedAt = if (status == ScanSessionEntity.CANCELLED) System.currentTimeMillis() else null,
                    statusText = text))
            }
        }
    }

    /** 启动首页时校准本地标记；WorkManager 记录缺失但 Room 会话仍活动时，按会话快照重建请求。 */
    fun reconcile(context: Context) {
        val app = context.applicationContext
        ioScope.launch {
            lifecycleMutex.withLock {
            runCatching {
                val workManager = WorkManager.getInstance(app)
                val infos = workManager.getWorkInfosForUniqueWork(ScanWorker.UNIQUE_WORK_NAME).get(8, TimeUnit.SECONDS)
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val snapshot = synchronized(prefsMonitor) {
                    Triple(
                        prefs.getBoolean(KEY_ACTIVE, false),
                        prefs.getBoolean(KEY_CANCEL_REQUESTED, false),
                        prefs.getString(KEY_SESSION, null) to prefs.getInt(KEY_GENERATION, 0)
                    )
                }
                val storedActive = snapshot.first
                val cancelRequested = snapshot.second
                val sessionId = snapshot.third.first
                val generationSnapshot = snapshot.third.second
                val db = AppContainer.database(app)
                val session = sessionId?.let { db.scanSessionDao().getById(it) }
                val unfinishedWork = infos.any { !it.state.isFinished }

                if (cancelRequested) {
                    if (session != null && session.status in ScanSessionEntity.ACTIVE) {
                        db.scanSessionDao().upsert(session.copy(
                            status = ScanSessionEntity.CANCELLED, updatedAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(), statusText = "扫描已取消"
                        ))
                    }
                    workManager.cancelUniqueWork(ScanWorker.UNIQUE_WORK_NAME).result.get()
                    if (sessionId != null) db.withTransaction {
                        db.imageSourceDao().cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "扫描已取消")
                        db.imageSourceDao().markSessionSourcesPartial(sessionId, "扫描已取消；已建立的图片索引予以保留")
                    }
                    markFinished(app, sessionId, cancellationCompleted = true)
                    return@runCatching
                }

                if (infos.isEmpty() && storedActive && session != null && session.status in ScanSessionEntity.ACTIVE) {
                    val request = OneTimeWorkRequestBuilder<ScanWorker>()
                        .setInputData(Data.Builder()
                            .putString(ScanWorker.KEY_SESSION_ID, session.id)
                            .putBoolean(ScanWorker.KEY_MEDIA_STORE, session.includeMediaStore)
                            .putStringArray(ScanWorker.KEY_FOLDER_URIS, session.folderUriList().toTypedArray())
                            .putBoolean(ScanWorker.KEY_INCLUDE_SUBFOLDERS, session.includeSubfolders)
                            .build())
                        .addTag(ScanWorker.UNIQUE_WORK_NAME)
                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()
                    workManager.enqueueUniqueWork(ScanWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
                    synchronized(prefsMonitor) {
                        if (prefs.getInt(KEY_GENERATION, 0) == generationSnapshot && prefs.getString(KEY_SESSION, null) == sessionId) {
                            prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
                        }
                    }
                    return@runCatching
                }

                val active = unfinishedWork
                if (!active && storedActive) {
                    val stale = session ?: db.scanSessionDao().observeActive().first()
                    if (stale != null && stale.status in ScanSessionEntity.ACTIVE) {
                        db.scanSessionDao().upsert(stale.copy(
                            status = ScanSessionEntity.FAILED, updatedAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(), statusText = "扫描任务记录已丢失，可重新发起扫描",
                            errorMessage = "WorkManager 未找到对应任务，已停止自动恢复"
                        ))
                        db.imageSourceDao().cancelActiveStatsForSession(stale.id, System.currentTimeMillis(), "扫描任务记录丢失")
                    }
                }
                synchronized(prefsMonitor) {
                    if (prefs.getInt(KEY_GENERATION, 0) == generationSnapshot && prefs.getString(KEY_SESSION, null) == sessionId) {
                        prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
                    }
                }
            }
            }
        }
    }

    /** 通过同一生命周期锁释放活动标记，并校验会话 ID，避免旧 Worker 清掉新任务标记。 */
    fun markFinished(
        context: Context,
        expectedSessionId: String? = null,
        cancellationCompleted: Boolean = false
    ) {
        val app = context.applicationContext
        ioScope.launch {
            lifecycleMutex.withLock {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                synchronized(prefsMonitor) {
                    val currentSessionId = prefs.getString(KEY_SESSION, null)
                    val sameSession = expectedSessionId == null || currentSessionId == expectedSessionId
                    if (sameSession && (!prefs.getBoolean(KEY_CANCEL_REQUESTED, false) || cancellationCompleted)) {
                        prefs.edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_CANCEL_REQUESTED, false).apply()
                    }
                }
            }
        }
    }
}
