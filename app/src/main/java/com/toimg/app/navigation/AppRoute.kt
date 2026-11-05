package com.toimg.app.navigation

object AppRoute {
    const val HOME = "home"
    const val PICKER = "picker"
    const val WORKSPACE = "workspace"
    const val RESULT = "result"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"
    const val SCAN_SCOPE = "scan_scope"
    const val SCAN_PROGRESS = "scan_progress"
    const val SOURCES = "image_sources"
    const val RECOGNIZE_CONFIRM = "recognize_confirm"
    const val TASK_HISTORY = "task_history"
    const val DUPLICATES = "duplicates"
    const val SMART_PROPOSAL = "smart_proposal"

    fun titleFor(route: String?): String = when (route) {
        PICKER -> "选择图片"
        WORKSPACE -> "图片工作区"
        CATEGORIES -> "智能分类与搜索"
        RESULT -> "处理结果"
        SETTINGS -> "设置"
        SCAN_SCOPE -> "扫描范围"
        SCAN_PROGRESS -> "扫描进度"
        SOURCES -> "图片来源管理"
        RECOGNIZE_CONFIRM -> "识别确认"
        TASK_HISTORY -> "任务历史"
        DUPLICATES -> "重复与相似图片清理"
        SMART_PROPOSAL -> "智能分类提案"
        else -> "ToImg"
    }
}
