/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.rating

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.RatingEnter
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.RatingSubmit
import me.him188.ani.utils.analytics.recordEvent

/**
 * 评分编辑的交互逻辑 (非 Compose): 对话框开关、提交任务、埋点.
 *
 * 展示数据由 [uiStateFlow] 派生, 与页面其它内容一起 `combine` 成页面的 UiState.
 *
 * @param isCollected 是否已收藏待评分的条目. 必须收藏才能评分, 否则请求编辑时提示先收藏.
 * @param currentSelfRating 当前的自评分, 用于埋点.
 */
class RatingEditController(
    private val isCollected: () -> Boolean,
    private val currentSelfRating: () -> SelfRatingInfo,
    private val onRate: suspend (RateRequest) -> Unit,
    backgroundScope: CoroutineScope,
    private val subjectId: Int? = null,
) : EditableRatingActions {
    private val showRatingDialog = MutableStateFlow(false)
    private val showRatingRequiresCollectionDialog = MutableStateFlow(false)
    private val tasker = MonoTasker(backgroundScope)

    /**
     * 把对话框状态与提交状态合并进展示数据.
     *
     * @param enableEdit 是否允许点击进入编辑, 通常是 "已收藏".
     */
    fun uiStateFlow(
        ratingInfo: RatingInfo,
        selfRatingInfo: Flow<SelfRatingInfo>,
        enableEdit: Flow<Boolean>,
    ): Flow<EditableRatingUiState> = combine(
        selfRatingInfo,
        enableEdit,
        showRatingDialog,
        showRatingRequiresCollectionDialog,
        tasker.isRunning,
    ) { self, enable, showDialog, showRequiresCollection, updating ->
        EditableRatingUiState(
            ratingInfo = ratingInfo,
            selfRatingInfo = self,
            enableEdit = enable,
            showRatingDialog = showDialog,
            showRatingRequiresCollectionDialog = showRequiresCollection,
            isUpdating = updating,
        )
    }

    override fun requestEditRating() {
        if (isCollected()) {
            showRatingDialog.value = true
            val hasExistingScore = currentSelfRating().score > 0
            Analytics.recordEvent(RatingEnter) {
                put("has_existing_score", hasExistingScore)
                subjectId?.let { put("subject_id", it) }
            }
        } else {
            showRatingRequiresCollectionDialog.value = true
        }
    }

    override fun cancelEditRating() {
        showRatingDialog.value = false
        showRatingRequiresCollectionDialog.value = false
    }

    override fun submitRating(request: RateRequest) {
        tasker.launch {
            onRate(request)
            Analytics.recordEvent(RatingSubmit) {
                put("score", request.score)
                put("has_comment", request.comment.isNotEmpty())
                put("comment_length", request.comment.length)
                put("is_private", request.isPrivate)
                subjectId?.let { put("subject_id", it) }
            }
            showRatingDialog.value = false
        }
    }

    override suspend fun updateScore(score: Int): LoadError? {
        require(score in 0..10)
        check(isCollected())
        val current = currentSelfRating()
        return tasker.async {
            LoadError.runAndWrapOrThrowCancellation {
                onRate(RateRequest(score, current.comment.orEmpty(), current.isPrivate))
                Analytics.recordEvent(RatingSubmit) {
                    put("score", score)
                    subjectId?.let { put("subject_id", it) }
                }
            }
        }.await()
    }

    override fun dismissRatingRequiresCollectionDialog() {
        showRatingRequiresCollectionDialog.value = false
    }
}
