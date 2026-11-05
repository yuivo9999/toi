package com.toimg.app.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 页面之间传递"待识别图片选择"的进程内持有者。
 * 选择页 / 工作区把选中的 image_id 放进来，识别确认页读取。
 */
object SelectionHolder {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** 调用方明确要求"重新识别已识别图片"时为 true（例如详情页的重新识别）。 */
    @Volatile
    var includeRecognized: Boolean = false
        private set

    fun set(ids: Collection<String>, includeRecognized: Boolean = false) {
        this.includeRecognized = includeRecognized
        _ids.value = ids.toSet()
    }

    fun clear() {
        includeRecognized = false
        _ids.value = emptySet()
    }
}
