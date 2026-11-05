package com.toimg.app.core.util

import com.toimg.app.data.local.entity.ImageEntity

/** 批次切分的纯函数（从 [BackgroundBatchManager] 抽出，便于单测）。 */
object BatchPlanner {

    /** 按 id 去重后按 [batchSize] 切批；batchSize < 1 时按 1 处理。 */
    fun plan(images: List<ImageEntity>, batchSize: Int): List<List<ImageEntity>> =
        images.distinctBy { it.id }.chunked(batchSize.coerceAtLeast(1))
}
