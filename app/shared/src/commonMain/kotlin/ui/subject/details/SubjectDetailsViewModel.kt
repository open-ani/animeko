/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.collection.components.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListStateFactory
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.app.ui.subject.components.comment.CommentLoader
import me.him188.ani.app.ui.subject.components.comment.CommentState
import me.him188.ani.app.ui.subject.rating.EditableRatingState
import me.him188.ani.app.ui.subject.rating.RateRequest
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SubjectDetailsViewModel(
    private val subjectId: Int,
) : AbstractViewModel(), KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeProgressRepository: EpisodeProgressRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val browserNavigator: BrowserNavigator by inject()
    private val bangumiRelatedPeopleService: BangumiRelatedPeopleService by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val bangumiCommentService: BangumiCommentService by inject()

    private val subjectCollectionFlow = subjectCollectionRepository.subjectCollectionFlow(subjectId).shareInBackground()
    private val subjectInfo: Flow<SubjectInfo> = subjectCollectionFlow.map { it.subjectInfo }

    val authState = AuthState()

    private val subjectProgressInfoState =
        subjectCollectionFlow.map { SubjectProgressInfo.compute(it.subjectInfo, it.episodes, PackedDate.now()) }
            .produceState(null)

    val subjectProgressState = kotlin.run {
        SubjectProgressStateFactory(
            episodeProgressRepository,
        ).run {
            SubjectProgressState(
                subjectProgressInfoState,
                episodeProgressInfoList(subjectId).produceState(emptyList()),
            )
        }
    }

    val subjectDetailsState = kotlin.run {
        SubjectDetailsState(
            subjectInfoState = subjectInfo.produceState(null),
            selfCollectionTypeState = subjectCollectionFlow.map { it.collectionType }.produceState(null),
            airingLabelState = AiringLabelState(
                subjectCollectionFlow.map { it.airingInfo }.produceState(null),
                subjectProgressInfoState,
            ),
            personsState = bangumiRelatedPeopleService.relatedPersonsFlow(subjectId).map {
                RelatedPersonInfo.sortList(it)
            }.onCompletion { if (it != null) emit(emptyList()) }.produceState(null),
            charactersState = bangumiRelatedPeopleService.relatedCharactersFlow(subjectId).map {
                RelatedCharacterInfo.sortList(it)
            }.produceState(null),
            relatedSubjectsState = bangumiRelatedPeopleService.relatedSubjectsFlow(subjectId).map {
                RelatedSubjectInfo.sortList(it)
            }.produceState(null),
        )
    }

    val episodeListState by lazy {
        EpisodeListStateFactory(
            settingsRepository,
            episodeCollectionRepository,
            episodeProgressRepository,
            backgroundScope,
        ).run {
            EpisodeListState(
                stateOf(subjectId),
                theme.produceState(EpisodeListProgressTheme.Default),
                episodes(subjectId).produceState(emptyList()),
                ::onSetEpisodeWatched,
                backgroundScope,
            )
        }
    }

    val editableSubjectCollectionTypeState = EditableSubjectCollectionTypeState(
        selfCollectionType = subjectCollectionFlow
            .map { it.collectionType }
            .produceState(UnifiedCollectionType.NOT_COLLECTED),
        hasAnyUnwatched = hasAnyUnwatched@{
            val collections = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
                .flowOn(Dispatchers.Default).firstOrNull() ?: return@hasAnyUnwatched true
            collections.any { !it.collectionType.isDoneOrDropped() }
        },
        onSetSelfCollectionType = { subjectCollectionRepository.setSubjectCollectionTypeOrDelete(subjectId, it) },
        onSetAllEpisodesWatched = {
            episodeCollectionRepository.setAllEpisodesWatched(subjectId)
        },
        backgroundScope,
    )

    val editableRatingState = EditableRatingState(
        ratingInfo = subjectInfo.map { it.ratingInfo }.produceState(RatingInfo.Empty),
        selfRatingInfo = subjectCollectionFlow.map { it.selfRatingInfo }
            .produceState(SelfRatingInfo.Empty),
        enableEdit = subjectCollectionFlow
            .map { it.collectionType != UnifiedCollectionType.NOT_COLLECTED }
            .produceState(false),
        isCollected = {
            val collection = subjectCollectionFlow.replayCache.firstOrNull() ?: return@EditableRatingState false
            collection.collectionType != UnifiedCollectionType.NOT_COLLECTED
        },
        onRate = { request ->
            subjectCollectionRepository.updateRating(
                subjectId,
                request,
            )
        },
        backgroundScope,
    )

    val detailsTabLazyListState = LazyListState()
    val commentTabLazyListState = LazyListState()

    private val subjectCommentLoader = CommentLoader.createForSubject(
        subjectId = flowOf(subjectId),
        coroutineContext = backgroundScope.coroutineContext,
        subjectCommentSource = { bangumiCommentService.getSubjectComments(it) },
    )

    val subjectCommentState: CommentState = CommentState(
        sourceVersion = subjectCommentLoader.sourceVersion.produceState(null),
        list = subjectCommentLoader.list.produceState(emptyList()),
        hasMore = subjectCommentLoader.hasFinished.map { !it }.produceState(true),
        onReload = { subjectCommentLoader.reload() },
        onLoadMore = { subjectCommentLoader.loadMore() },
        onSubmitCommentReaction = { _, _ -> },
        backgroundScope = backgroundScope,
    )

    fun browseSubjectBangumi(context: ContextMP) {
        browserNavigator.openBrowser(context, "https://bgm.tv/subject/${subjectId}")
    }

    // TODO: Remove cancelScope (maybe create a SubjectDetailsState for easier integration into other pages)
    fun cancelScope() {
        backgroundScope.cancel()
    }
}

suspend inline fun SubjectCollectionRepository.updateRating(subjectId: Int, request: RateRequest) {
    return this.updateRating(subjectId, request.score, request.comment, isPrivate = request.isPrivate)
}
