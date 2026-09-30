/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.rating_requires_collection
import me.him188.ani.app.ui.lang.settings_mediasource_close
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * 可编辑评分的展示数据. 交互见 [EditableRatingActions], 逻辑见 [RatingEditController].
 */
@Immutable
data class EditableRatingUiState(
    val ratingInfo: RatingInfo,
    val selfRatingInfo: SelfRatingInfo,
    /** 是否允许点击进入编辑 (必须已收藏条目). */
    val enableEdit: Boolean,
    val showRatingDialog: Boolean = false,
    /** 未收藏时点击评分, 提示需要先收藏. */
    val showRatingRequiresCollectionDialog: Boolean = false,
    val isUpdating: Boolean = false,
) {
    companion object {
        val Placeholder = EditableRatingUiState(
            ratingInfo = RatingInfo.Empty,
            selfRatingInfo = SelfRatingInfo.Empty,
            enableEdit = false,
        )
    }
}

/**
 * 可编辑评分的交互. 通常由 ViewModel 或页面状态实现, 见 [RatingEditController].
 */
interface EditableRatingActions {
    fun requestEditRating()
    fun cancelEditRating()
    fun submitRating(request: RateRequest)

    /**
     * 只修改分数, 保留已有的评价内容和可见性, 并等待完成. 供只能打分的界面 (例如 TV) 使用.
     *
     * @return 失败原因, 成功为 `null`.
     */
    suspend fun updateScore(score: Int): LoadError?
    fun dismissRatingRequiresCollectionDialog()

    companion object Noop : EditableRatingActions {
        override fun requestEditRating() {}
        override fun cancelEditRating() {}
        override fun submitRating(request: RateRequest) {}
        override suspend fun updateScore(score: Int): LoadError? = null
        override fun dismissRatingRequiresCollectionDialog() {}
    }
}

/**
 * 评分展示 + 点击进入编辑, 自带 [EditableRatingDialogsHost].
 */
@Composable
fun EditableRating(
    uiState: EditableRatingUiState,
    actions: EditableRatingActions,
    modifier: Modifier = Modifier,
) {
    EditableRatingDialogsHost(uiState, actions)
    Rating(
        rating = uiState.ratingInfo,
        selfRatingScore = uiState.selfRatingInfo.score,
        onClick = { actions.requestEditRating() },
        clickEnabled = uiState.enableEdit && !uiState.isUpdating,
        modifier = modifier,
    )
}

/**
 * 评分编辑对话框与 "需要先收藏" 提示. 页面里放一个即可.
 */
@Composable
fun EditableRatingDialogsHost(
    uiState: EditableRatingUiState,
    actions: EditableRatingActions,
) {
    if (uiState.showRatingRequiresCollectionDialog) {
        AlertDialog(
            { actions.dismissRatingRequiresCollectionDialog() },
            text = { Text(stringResource(Lang.rating_requires_collection)) },
            confirmButton = {
                TextButton({ actions.dismissRatingRequiresCollectionDialog() }) {
                    Text(stringResource(Lang.settings_mediasource_close))
                }
            },
        )
    }

    if (uiState.showRatingDialog) {
        val selfRatingInfo = uiState.selfRatingInfo
        RatingEditorDialog(
            remember(selfRatingInfo) {
                RatingEditorState(
                    initialScore = selfRatingInfo.score,
                    initialComment = selfRatingInfo.comment ?: "",
                    initialIsPrivate = selfRatingInfo.isPrivate,
                )
            },
            onDismissRequest = { actions.cancelEditRating() },
            onRate = { actions.submitRating(it) },
            isLoading = uiState.isUpdating,
        )
    }
}

@TestOnly
val TestEditableRatingUiState
    get() = EditableRatingUiState(
        ratingInfo = TestSubjectInfo.ratingInfo,
        selfRatingInfo = TestSelfRatingInfo,
        enableEdit = true,
    )
