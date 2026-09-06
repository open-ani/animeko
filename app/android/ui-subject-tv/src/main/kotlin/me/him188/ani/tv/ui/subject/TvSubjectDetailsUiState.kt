/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem

data class TvBackdropState(val url: String?)

data class TvSubjectImages(
    val collection: SubjectCollectionInfo? = null,
    val backdrop: TvBackdropState? = null,
    val episodeStills: Map<Int, String> = emptyMap(),
)

data class TvSubjectDetailsUiState(
    val content: TvSubjectDetailsContentState? = null,
    val error: LoadError? = null,
    val images: TvSubjectImages = TvSubjectImages(),
)

data class TvSubjectDetailsContentState(
    val info: SubjectInfo,
    val episodes: List<EpisodeListItem>,
    val episodesLoading: Boolean,
    val playTargetId: Int?,
    val watchedCount: Int,
    val exposedCharactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val totalCharactersCount: Int?,
    val exposedStaffPager: Flow<PagingData<RelatedPersonInfo>>,
    val totalStaffCount: Int?,
    val relatedSubjectsPager: Flow<PagingData<RelatedSubjectInfo>>,
    val commentsPager: Flow<PagingData<UIComment>>,
    val commentCount: Int?,
)

sealed interface TvSubjectDetailsIntent {
    data object Retry : TvSubjectDetailsIntent
    data object Resume : TvSubjectDetailsIntent
    data class PlayEpisode(val episodeId: Int) : TvSubjectDetailsIntent
    data class OpenRelatedSubject(val subjectId: Int) : TvSubjectDetailsIntent
}
