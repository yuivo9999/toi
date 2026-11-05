package com.toimg.app.core.di

import android.content.Context
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.repository.ImageSourceRepositoryImpl
import com.toimg.app.domain.repository.ImageSourceRepository

/**
 * 轻量手写依赖容器（取代散落在各处的 `ToImgDatabase.getInstance`）。
 *
 * - 业务代码统一通过 [database] 取得数据库，不再直接触碰单例实现；
 * - 测试里可用 [overrideDatabaseForTesting] 注入内存数据库；
 * - 之后若迁移到 Hilt，只需把本对象替换成 @Module，调用点不变。
 */
object AppContainer {

    @Volatile
    private var databaseOverride: ToImgDatabase? = null

    fun database(context: Context): ToImgDatabase =
        databaseOverride ?: ToImgDatabase.provide(context)

    fun imageSourceRepository(context: Context): ImageSourceRepository = ImageSourceRepositoryImpl(context.applicationContext, database(context))

    /** 仅供测试：传入 null 即恢复默认实现。 */
    fun overrideDatabaseForTesting(db: ToImgDatabase?) {
        databaseOverride = db
    }
}
