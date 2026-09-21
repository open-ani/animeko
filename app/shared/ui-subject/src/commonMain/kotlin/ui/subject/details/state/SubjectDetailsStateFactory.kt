/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.player.playProgressByEpisodeId
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentMapperContext.toCommentVoteValue
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.reportSnapshotText
import me.him188.ani.app.ui.comment.toDataReason
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.rating.EditableRatingActions
import me.him188.ani.app.ui.rating.RatingEditController
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionTypeEditActions
import me.him188.ani.app.ui.subject.details.updateRating
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

interface SubjectDetailsStateFactory {
    fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState>
    fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState>

    /**
     * @param placeholder 通常是仅仅包含少量信息的预加载的 subject 信息.
     *        例如从探索页导航到详情页时, subject 名字和封面图时已知的, 可以作为预加载信息以第一时间显示一些东西.
     */
    fun create(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ): Flow<SubjectDetailsState>

    fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState
}

class DefaultSubjectDetailsStateFactory : SubjectDetailsStateFactory, KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val episodePlayHistoryRepository: EpisodePlayHistoryRepository by inject()
    private val bangumiRelatedPeopleService: BangumiRelatedPeopleService by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()
    private val bangumiCommentRepository: BangumiCommentRepository by inject()
    private val commentReportService: AniCommentReportService by inject()
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase by inject()

    override fun create(
        subjectInfoFlow: Flow<SubjectInfo>
    ): Flow<SubjectDetailsState> = flow {
        coroutineScope {
            subjectInfoFlow.transformLatest { subjectInfo ->
                coroutineScope {
                    val subjectCollectionFlow = subjectCollectionRepository.subjectCollectionFlow(subjectInfo.subjectId)
                        .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

                    emit(createImpl(subjectInfo, subjectCollectionFlow))
                    awaitCancellation()
                }
            }.collect()
            awaitCancellation()
        }
    }

    override fun create(
        subjectInfo: SubjectInfo,
    ): Flow<SubjectDetailsState> = flow {
        coroutineScope {
            val subjectCollectionFlow = subjectCollectionRepository.subjectCollectionFlow(subjectInfo.subjectId)
                .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

            emit(createImpl(subjectInfo, subjectCollectionFlow))
            awaitCancellation()
        }
    }

    override fun create(subjectId: Int, placeholder: SubjectInfo?): Flow<SubjectDetailsState> = flow {
        coroutineScope {
            val subjectCollectionInfoFlow = subjectCollectionRepository.subjectCollectionFlow(subjectId)
                .stateIn(this)

            emit(createImpl(subjectCollectionInfoFlow.value.subjectInfo, subjectCollectionInfoFlow))

            awaitCancellation()
        }
    }

    override fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState {
        val subjectCollectionInfoFlow = MutableStateFlow(subjectCollectionInfo)
        return scope.createImpl(subjectCollectionInfoFlow.value.subjectInfo, subjectCollectionInfoFlow)
    }

    private fun CoroutineScope.createImpl(
        subjectInfo: SubjectInfo,
        subjectCollectionFlow: SharedFlow<SubjectCollectionInfo>,
    ): SubjectDetailsState {
        val subjectId = subjectInfo.subjectId
        val editableSubjectCollectionTypeState = EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = subjectCollectionFlow
                .map { it.collectionType },
            hasAnyUnwatched = hasAnyUnwatched@{
                val collections = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
                    .flowOn(Dispatchers.Default).firstOrNull() ?: return@hasAnyUnwatched true

                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = {
                setSubjectCollectionTypeOrDeleteUseCase(subjectId, it)
            },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
            },
            this,
        )

        val ratingEditController = RatingEditController(
            isCollected = {
                val collection = subjectCollectionFlow.replayCache.firstOrNull() ?: return@RatingEditController false
                collection.collectionType != UnifiedCollectionType.NOT_COLLECTED
            },
            currentSelfRating = {
                subjectCollectionFlow.replayCache.firstOrNull()?.selfRatingInfo ?: SelfRatingInfo.Empty
            },
            onRate = { request ->
                subjectCollectionRepository.updateRating(
                    subjectId,
                    request,
                )
            },
            this,
            subjectId,
        )
        val ratingUiStateFlow = ratingEditController.uiStateFlow(
            ratingInfo = subjectInfo.ratingInfo,
            selfRatingInfo = subjectCollectionFlow.map { it.selfRatingInfo },
            enableEdit = subjectCollectionFlow.map { it.collectionType != UnifiedCollectionType.NOT_COLLECTED },
        )

        val actions = object : SubjectDetailsActions,
            SubjectCollectionTypeEditActions by editableSubjectCollectionTypeState,
            EditableRatingActions by ratingEditController {}


        val commentsCount = MutableStateFlow<Int?>(null)
        val comments = bangumiCommentRepository.subjectCommentsPager(subjectId) { commentsCount.value = it }
            .map { page ->
                page.map { it.parseToUIComment() }
            }
            .cachedIn(this)

        val subjectCommentState = CommentState(
            list = comments,
            countState = commentsCount.produceState(null, this),
            onSubmitCommentReaction = { _, _, _ -> },
            backgroundScope = this,
            onSubmitCommentVote = { comment, vote ->
                // 只有 Ani 源的评价可投票; Bangumi 源的评价 reviewId 为空
                if (comment.source == UICommentSource.ANI && comment.sourceCommentId.isNotEmpty()) {
                    bangumiCommentRepository.voteSubjectReview(
                        subjectId = subjectId,
                        reviewId = comment.sourceCommentId,
                        vote = vote?.toCommentVoteValue(),
                    )
                }
            },
        )

        val subjectCommentReportState = CommentReportState(
            onSubmitReport = { comment, reason, detail ->
                commentReportService.createReport(
                    targetType = CommentReportTargetType.SUBJECT_REVIEW,
                    targetId = comment.sourceCommentId,
                    reason = reason.toDataReason(),
                    commentAuthorId = comment.author?.id,
                    detail = detail.takeIf { it.isNotEmpty() },
                    contentSnapshot = comment.reportSnapshotText(),
                    subjectId = subjectId.toLong(),
                )
            },
            backgroundScope = this,
        )

        val relatedPersonsFlow = subjectRelationsRepository.subjectRelatedPersonsFlow(subjectId)
            .stateIn(this, SharingStarted.Eagerly, null)

        val relatedCharactersFlow = subjectRelationsRepository.subjectRelatedCharactersFlow(subjectId)
            .stateIn(this, SharingStarted.Eagerly, null)

        val minuteTicker = flow {
            while (true) {
                emit(Unit)
                delay(1.minutes)
            }
        }

        // 只订阅本条目剧集的播放记录, 换算成按剧集 id 索引的进度
        @OptIn(ExperimentalCoroutinesApi::class)
        val playProgressFlow = subjectCollectionFlow
            .map { collection -> collection.episodes.map { it.episodeId } }
            .distinctUntilChanged()
            .flatMapLatest { episodeIds -> episodePlayHistoryRepository.flowByEpisodeIds(episodeIds) }
            .map { it.playProgressByEpisodeId() }

        val state = SubjectDetailsState(
            subjectId = subjectInfo.subjectId,
            info = subjectInfo,
            staffPager = relatedPersonsFlow
                .filterNotNull()
                .map {
                    PagingData.from(
                        it,
                        sourceLoadStates = LoadStates(
                            LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true),
                        ),
                    )
                }
                .cachedIn(this),
            exposedStaffPager = relatedPersonsFlow
                .filterNotNull()
                .map { list ->
                    list.take(EXPOSED_STAFF_COUNT)
                }
                .map { PagingData.from(it) }
                .cachedIn(this),
            charactersPager = relatedCharactersFlow.filterNotNull().map {
                PagingData.from(
                    it,
                    sourceLoadStates = LoadStates(
                        LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true),
                    ),
                )
            }.cachedIn(this),
            relatedSubjectsPager = bangumiRelatedPeopleService.relatedSubjectsFlow(subjectId)
                .map {
                    // This response contains the complete list; no further pages will arrive.
                    PagingData.from(
                        it,
                        sourceLoadStates = LoadStates(
                            LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true),
                        ),
                    )
                }
                .cachedIn(this),
            exposedCharactersPager = relatedCharactersFlow
                .filterNotNull()
                .map { it.computeExposed() }
                .map { PagingData.from(it) }
                .cachedIn(this),
            subjectCommentState = subjectCommentState,
            subjectCommentReportState = subjectCommentReportState,
            actions = actions,
            // 页面展示内容只从这一处派生, 每分钟重算一次以跟上日期变化 (播出状态、未开播判断)
            uiState = combine(
                minuteTicker,
                subjectCollectionFlow,
                playProgressFlow,
                combine(relatedPersonsFlow, relatedCharactersFlow) { persons, characters ->
                    persons?.size to characters?.size
                },
                combine(editableSubjectCollectionTypeState.presentationFlow, ratingUiStateFlow) { c, r -> c to r },
            ) { _, collection, playProgress, (staffCount, charactersCount), (collectionTypeEdit, rating) ->
                val now = Clock.System.now()
                SubjectDetailsUiState(
                    subjectId = subjectId,
                    displayName = collection.subjectInfo.displayName,
                    selfCollectionType = collection.collectionType,
                    airingInfo = collection.airingInfo,
                    progressInfo = SubjectProgressInfo.compute(
                        collection.subjectInfo, collection.episodes, PackedDate.now(),
                        recurrence = collection.recurrence,
                    ),
                    episodeListUiState = EpisodeListUiState.from(collection, now, playProgress),
                    totalStaffCount = staffCount,
                    totalCharactersCount = charactersCount,
                    collectionTypeEdit = collectionTypeEdit,
                    rating = rating,
                )
            }.stateIn(
                this, SharingStarted.WhileSubscribed(5000),
                SubjectDetailsUiState.Placeholder.copy(subjectId = subjectId),
            ),
        )
        return state
    }
}

@TestOnly
class TestSubjectDetailsStateFactory : SubjectDetailsStateFactory {
    @TestOnly
    override fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState> {
        return emptyFlow()
    }

    override fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState> {
        return emptyFlow()
    }

    override fun create(subjectId: Int, placeholder: SubjectInfo?): Flow<SubjectDetailsState> {
        return emptyFlow()
    }

    override fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState {
        throw UnsupportedOperationException()
    }

}

/** 角色区块露出数 (Figma 定稿 1515:336: 8 个). */
private const val EXPOSED_CHARACTERS_COUNT = 8

/** 制作人员露出数 (Figma 定稿三栏右栏卡: 10 个职位; 双栏/手机由 UI 层截取前 6). */
private const val EXPOSED_STAFF_COUNT = 10

private fun List<RelatedCharacterInfo>.computeExposed(): List<RelatedCharacterInfo> {
    // 主角优先; 主角不足 4 个时按原始顺序补足 (含配角).
    val mains = filter { it.isMainCharacter() }
    return if (mains.size >= 4) {
        mains.take(EXPOSED_CHARACTERS_COUNT)
    } else {
        take(EXPOSED_CHARACTERS_COUNT)
    }
}
