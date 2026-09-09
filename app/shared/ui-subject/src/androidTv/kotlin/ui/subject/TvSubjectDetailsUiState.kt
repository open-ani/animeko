/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.subject

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentReportReason
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

data class TvBackdropState(val url: String?)

data class TvSubjectImages(
    val backdrop: TvBackdropState? = null,
    val episodeStills: Map<Int, String> = emptyMap(),
)

data class TvSubjectDetailsUiState(
    val content: TvSubjectDetailsContentState? = null,
    val error: LoadError? = null,
    val images: TvSubjectImages = TvSubjectImages(),
    val refreshing: Boolean = false,
    val loggedIn: Boolean? = null,
    val operation: TvSubjectOperation = TvSubjectOperation(),
    val tagResults: TvTagResults? = null,
    val reportDraft: TvSubjectReportDraft? = null,
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
    val collectionType: UnifiedCollectionType = UnifiedCollectionType.NOT_COLLECTED,
    val selfRating: SelfRatingInfo = SelfRatingInfo.Empty,
    val mainEpisodeIds: Set<Int> = emptySet(),
    val charactersPager: Flow<PagingData<RelatedCharacterInfo>> = exposedCharactersPager,
    val staffPager: Flow<PagingData<RelatedPersonInfo>> = exposedStaffPager,
    val commentPresentation: (UIComment) -> UIComment = { it },
    val canReport: Boolean = false,
    val airing: AiringLabelState? = null,
    val progress: SubjectProgressState? = null,
)

data class TvSubjectOperation(
    val requestId: Int = -1,
    val busy: Boolean = false,
    val error: LoadError? = null,
    val completed: Boolean = false,
    val offerMarkAllWatched: Boolean = false,
)

data class TvTagResults(val tag: String, val subjects: Flow<PagingData<PersonSubjectSummary>>)

data class TvSubjectReportDraft(val commentId: String, val reason: CommentReportReason)

sealed interface TvSubjectDetailsIntent {
    data object Retry : TvSubjectDetailsIntent
    data object Resume : TvSubjectDetailsIntent
    data class PlayEpisode(val episodeId: Int) : TvSubjectDetailsIntent
    data class OpenRelatedSubject(val subjectId: Int) : TvSubjectDetailsIntent
    data object Login : TvSubjectDetailsIntent
    data class SetCollection(val type: UnifiedCollectionType, val requestId: Int) : TvSubjectDetailsIntent
    data class MarkAllWatched(val requestId: Int) : TvSubjectDetailsIntent
    data class SetScore(val score: Int, val requestId: Int) : TvSubjectDetailsIntent
    data class ToggleEpisode(val episodeId: Int, val requestId: Int) : TvSubjectDetailsIntent
    data class OpenCharacter(val characterId: Int) : TvSubjectDetailsIntent
    data class OpenStaff(val personId: Int) : TvSubjectDetailsIntent
    data class SearchTag(val tag: String) : TvSubjectDetailsIntent
    data class Vote(val comment: UIComment, val vote: UICommentVote) : TvSubjectDetailsIntent
    data class ChooseReportReason(val commentId: String, val reason: CommentReportReason) : TvSubjectDetailsIntent
    data class Report(val comment: UIComment, val reason: CommentReportReason, val requestId: Int) : TvSubjectDetailsIntent
}
