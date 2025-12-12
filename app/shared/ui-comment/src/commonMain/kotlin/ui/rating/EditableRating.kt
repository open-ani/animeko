/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.rating

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.RatingEnter
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.RatingSubmit
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.platform.annotations.TestOnly


@Stable
class EditableRatingState(
    ratingInfo: State<RatingInfo>,
    selfRatingInfo: State<SelfRatingInfo>,
    enableEdit: State<Boolean>,
    /**
     * 是否收藏了待评分的条目. 必须要收藏才能评分.
     */
    private val isCollected: () -> Boolean,
    private val onRate: suspend (RateRequest) -> Unit,
    backgroundScope: CoroutineScope,
    private val subjectId: Int? = null,
) {
    val ratingInfo by ratingInfo
    val selfRatingInfo by selfRatingInfo
    val enableEdit by enableEdit

    var showRatingRequiresCollectionDialog by mutableStateOf(false)

    var showRatingDialog by mutableStateOf(false)
        private set

    fun requestEdit() {
        if (isCollected()) {
            showRatingDialog = true
            val hasExistingScore = selfRatingInfo.score > 0
            Analytics.recordEvent(RatingEnter) {
                put("has_existing_score", hasExistingScore)
                subjectId?.let { put("subject_id", it) }
            }
        } else {
            showRatingRequiresCollectionDialog = true
        }
    }

    fun cancelEdit() {
        showRatingDialog = false
        showRatingRequiresCollectionDialog = false
    }

    private val tasker = MonoTasker(backgroundScope)
    val isUpdatingRating get() = tasker.isRunning
    fun updateRating(rateRequest: RateRequest) {
        tasker.launch {
            onRate(rateRequest)
            Analytics.recordEvent(RatingSubmit) {
                put("score", rateRequest.score)
                put("has_comment", rateRequest.comment.isNotEmpty())
                put("comment_length", rateRequest.comment.length)
                put("is_private", rateRequest.isPrivate)
                subjectId?.let { put("subject_id", it) }
            }
            showRatingDialog = false
        }
    }

    fun dismissRatingRequiresCollectionDialog() {
        showRatingRequiresCollectionDialog = false
    }
}

/**
 * 显示 [Rating] 和 [RatingEditorDialog] 的组合
 */
@Composable
fun EditableRating(
    state: EditableRatingState,
    modifier: Modifier = Modifier,
) {
    if (state.showRatingRequiresCollectionDialog) {
        AlertDialog(
            { state.dismissRatingRequiresCollectionDialog() },
            text = { Text("请先收藏再评分") },
            confirmButton = {
                TextButton({ state.dismissRatingRequiresCollectionDialog() }) {
                    Text("关闭")
                }
            },
        )
    }

    val isUpdatingRating = state.isUpdatingRating.collectAsStateWithLifecycle()
    if (state.showRatingDialog) {
        val selfRatingInfo = state.selfRatingInfo
        RatingEditorDialog(
            remember(selfRatingInfo) {
                RatingEditorState(
                    initialScore = selfRatingInfo.score,
                    initialComment = selfRatingInfo.comment ?: "",
                    initialIsPrivate = selfRatingInfo.isPrivate,
                )
            },
            onDismissRequest = {
                state.cancelEdit()
            },
            onRate = { state.updateRating(it) },
            isLoading = isUpdatingRating.value,
        )
    }
    Rating(
        rating = state.ratingInfo,
        selfRatingScore = state.selfRatingInfo.score,
        onClick = { state.requestEdit() },
        clickEnabled = state.enableEdit && !isUpdatingRating.value,
        modifier = modifier,
    )
}

@Composable
@TestOnly
fun rememberTestEditableRatingState(): EditableRatingState {
    val backgroundScope = rememberCoroutineScope()
    return remember {
        createTestEditableRatingState(TestSubjectInfo, TestSelfRatingInfo, backgroundScope)
    }
}

@TestOnly
fun createTestEditableRatingState(
    subjectInfo: SubjectInfo,
    selfRatingInfo: SelfRatingInfo,
    backgroundScope: CoroutineScope,
) = EditableRatingState(
    ratingInfo = mutableStateOf(subjectInfo.ratingInfo),
    selfRatingInfo = mutableStateOf(selfRatingInfo),
    enableEdit = mutableStateOf(true),
    isCollected = { true },
    onRate = { _ -> },
    backgroundScope,
    subjectInfo.subjectId,
)
