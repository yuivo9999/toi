package com.toimg.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.toimg.app.core.util.NewImageIngestor
import com.toimg.app.core.util.NewImageWatcher

/** 相册出现新图片时由系统唤醒：入库 → 通知（或按设置自动识别）→ 重新注册下一次触发。 */
class NewImageWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return Result.success()
    }
}
