/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 条目详情页 UI 状态. 所有属性 null 都表示正在加载中.
 */
@Stable
class SubjectDetailsState(
    val subjectId: Int,
    val info: SubjectInfo?,
    selfCollectionTypeState: State<UnifiedCollectionType>,
    val airingLabelState: AiringLabelState,

    // 附加信息, pager
    val staffPager: Flow<PagingData<RelatedPersonInfo>>,
    val exposedStaffPager: Flow<PagingData<RelatedPersonInfo>>,
    val totalStaffCountState: State<Int?>,
    val charactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val exposedCharactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val totalCharactersCountState: State<Int?>,
    val relatedSubjectsPager: Flow<PagingData<RelatedSubjectInfo>>,
    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    val editableRatingState: EditableRatingState,
    val subjectProgressState: SubjectProgressState,
    val subjectCommentState: CommentState,
    val presentation: StateFlow<SubjectDetailsPresentation>, // default to placeholder
) {
    private val selfCollectionTypeOrNull by selfCollectionTypeState
    val selfCollectionType by derivedStateOf { selfCollectionTypeOrNull }

    val selfCollected by derivedStateOf { this.selfCollectionType != UnifiedCollectionType.NOT_COLLECTED }

    val detailsTabLazyListState = LazyListState()
    val commentTabLazyGridState = LazyGridState()
}

@Immutable
data class SubjectDetailsPresentation(
    val subjectId: Int,
    val displayName: String,
    val episodeListUiState: EpisodeListUiState,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = SubjectDetailsPresentation(
            subjectId = 0,
            displayName = "",
            episodeListUiState = EpisodeListUiState.Placeholder,
            isPlaceholder = true,
        )
    }
}
