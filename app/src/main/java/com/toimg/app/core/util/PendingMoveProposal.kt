package com.toimg.app.core.util

import android.net.Uri

/** 「智能分类提案」→「移动预览」之间传递一次性数据（读取后即清空）。 */
object PendingMoveProposal {
    data class Proposal(
        val imageIds: List<String>,
        val targetUri: Uri,
        val targetName: String
    )

    @Volatile
    private var pending: Proposal? = null

    fun set(proposal: Proposal) {
        pending = proposal
    }

    fun consume(): Proposal? {
        val p = pending
        pending = null
        return p
    }
}
