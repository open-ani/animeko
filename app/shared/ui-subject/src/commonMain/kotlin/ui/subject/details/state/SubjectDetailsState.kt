/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 条目详情页 UI 状态.
 *
 * 随条目数据变化的展示内容都在 [uiState] 里, 页面只订阅这一个 flow;
 * 其余是分页数据源和带交互的子状态 (收藏类型编辑、评分、评论).
 */
@Stable
class SubjectDetailsState(
    val subjectId: Int,
    val info: SubjectInfo?,

    // 附加信息, pager
    val staffPager: Flow<PagingData<RelatedPersonInfo>>,
    val exposedStaffPager: Flow<PagingData<RelatedPersonInfo>>,
    val charactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val exposedCharactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val relatedSubjectsPager: Flow<PagingData<RelatedSubjectInfo>>,
    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    val editableRatingState: EditableRatingState,
    val subjectCommentState: CommentState,
    /**
     * 页面展示内容. 数据未加载时为 [SubjectDetailsUiState.Placeholder].
     */
    val uiState: StateFlow<SubjectDetailsUiState>,
    val subjectCommentReportState: CommentReportState? = null,
) {
    val detailsTabLazyListState = LazyListState()
    val commentTabLazyGridState = LazyGridState()
}

/**
 * 条目详情页随条目数据变化的展示内容, 由 [me.him188.ani.app.data.models.subject.SubjectCollectionInfo] 等派生.
 *
 * 所有字段来自同一次 `combine`, 因此彼此一致: 不会出现头部已经显示看过、选集却还是未看的情况.
 */
@Immutable
data class SubjectDetailsUiState(
    val subjectId: Int,
    val displayName: String,
    val selfCollectionType: UnifiedCollectionType,
    /** `null` 表示加载中. */
    val airingInfo: SubjectAiringInfo?,
    /** `null` 表示加载中. */
    val progressInfo: SubjectProgressInfo?,
    val episodeListUiState: EpisodeListUiState,
    /** `null` 表示加载中. */
    val totalStaffCount: Int?,
    /** `null` 表示加载中. */
    val totalCharactersCount: Int?,
    val isPlaceholder: Boolean = false,
) {
    val selfCollected: Boolean get() = selfCollectionType != UnifiedCollectionType.NOT_COLLECTED

    companion object {
        val Placeholder = SubjectDetailsUiState(
            subjectId = 0,
            displayName = "",
            selfCollectionType = UnifiedCollectionType.NOT_COLLECTED,
            airingInfo = null,
            progressInfo = null,
            episodeListUiState = EpisodeListUiState.Placeholder,
            totalStaffCount = null,
            totalCharactersCount = null,
            isPlaceholder = true,
        )
    }
}

/**
 * 供 [me.him188.ani.app.ui.subject.AiringLabel] 使用的适配: 该组件仍以 [AiringLabelState] 为参数 (收藏页、播放页也在用).
 */
@Composable
fun SubjectDetailsUiState.rememberAiringLabelState(): AiringLabelState {
    val airingInfo = airingInfo
    val progressInfo = progressInfo
    return remember(airingInfo, progressInfo) { AiringLabelState(airingInfo, progressInfo) }
}

/**
 * 供播放按钮使用的适配, 见 [rememberAiringLabelState].
 */
@Composable
fun SubjectDetailsUiState.rememberSubjectProgressState(): SubjectProgressState {
    val progressInfo = progressInfo
    return remember(progressInfo) { SubjectProgressState(progressInfo) }
}
