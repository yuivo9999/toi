package com.toimg.app.domain.usecase

import com.toimg.app.domain.repository.ImageSourceRepository
import com.toimg.app.domain.repository.RemoveSourceResult

/** F5 安全移出用例；实现层使用数据库事务并且不调用任何设备文件删除 API。 */
class RemoveImageSourceUseCase(private val repository: ImageSourceRepository) {
    suspend operator fun invoke(sourceId: String): RemoveSourceResult = repository.removeSourceFromProject(sourceId)
}
