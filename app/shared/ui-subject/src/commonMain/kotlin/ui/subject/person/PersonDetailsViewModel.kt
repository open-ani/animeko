/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentMapperContext.toCommentVoteValue
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.reportSnapshotText
import me.him188.ani.app.ui.comment.toDataReason
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * 人物与角色的详情、关联条目分页与评论区 ([comments]).
 *
 * @param commentTarget 评论挂在哪个人物/角色上.
 * @param originalCommentsUrl 评论来源页 (Bangumi 吐槽箱), 用于菜单的 "在 Bangumi 打开".
 */
open class PeopleDetailsViewModel(
    private val commentTarget: PersonCommentTarget,
    private val originalCommentsUrl: String,
    private val koin: Koin = GlobalKoin,
) : AbstractViewModel(), KoinComponent {
    final override fun getKoin(): Koin = koin

    protected val repository: PersonDetailsRepository by inject()
    private val commentRepository: PersonCommentRepository by inject()
    private val commentReportService: AniCommentReportService by inject()
    private val postCommentUseCase: PostCommentUseCase by inject()

    /** 详情 (含评论数) 的重启器: 发送评论后重新拉一次, 让评论数与列表一致. */
    protected val detailsRestarter = FlowRestarter()
    private val commentsRestarter = FlowRestarter()
    private val commentLoadFailureChannel = Channel<Throwable>(Channel.BUFFERED)

    private val detailsError = MutableStateFlow<LoadError?>(null)
    val detailsLoadError = detailsError.asStateFlow()
    val authState = SelfInfoStateProducer(backgroundScope.coroutineContext, koin).flow

    val personDetails = (if (commentTarget is PersonCommentTarget.Person) {
        repository.personDetailsFlow(commentTarget.personId).observeDetails()
    } else flowOf(null)).stateInBackground(null)

    val characterDetails = (if (commentTarget is PersonCommentTarget.Character) {
        repository.characterDetailsFlow(commentTarget.characterId).observeDetails()
    } else flowOf(null)).stateInBackground(null)

    val castsPager = (if (commentTarget is PersonCommentTarget.Person) {
        repository.personCastsPager(commentTarget.personId)
    } else flowOf(PagingData.empty())).cachedIn(backgroundScope)

    val worksPager = (if (commentTarget is PersonCommentTarget.Person) {
        repository.personWorksPager(commentTarget.personId)
    } else flowOf(PagingData.empty())).cachedIn(backgroundScope)

    val subjectsPager = (if (commentTarget is PersonCommentTarget.Character) {
        repository.characterSubjectsPager(commentTarget.characterId)
    } else flowOf(PagingData.empty())).cachedIn(backgroundScope)

    private val commentPanelTitleFlow = combine(personDetails, characterDetails) { person, character ->
        person?.person?.displayName ?: character?.character?.displayName
    }

    fun reloadDetails() {
        detailsError.value = null
        detailsRestarter.restart()
    }

    private fun <T> Flow<T>.observeDetails() = onEach { detailsError.value = null }
        .retryWhen { error, attempt ->
            detailsError.value = LoadError.fromException(error)
            delay(2.seconds * (attempt + 1).coerceAtMost(5).toInt())
            true
        }.restartable(detailsRestarter)

    /** 评论总数 (Ani + Bangumi), 来自详情接口. */
    private val commentCountFlow = combine(personDetails, characterDetails) { person, character ->
        person?.commentCount ?: character?.commentCount
    }

    /**
     * 评论区状态. 复用剧集/条目评论 UI ([me.him188.ani.app.ui.comment.CommentColumn]); 人物评论无评分.
     */
    val comments: PeopleCommentsState by lazy { createComments() }

    private fun createComments(): PeopleCommentsState {
        val commentState = CommentState(
            list = commentRepository.commentsPager(
                commentTarget,
                // Ani 评论正常但服务端没取到 Bangumi 评论: 列表照常显示, 额外提示一次, 免得看起来像"没有评论"
                onBangumiUnavailable = {
                    commentLoadFailureChannel.trySend(
                        RepositoryServiceUnavailableException("Bangumi person comments unavailable"),
                    )
                },
            )
                .map { page -> page.map { it.parseToUIComment() } }
                .restartable(commentsRestarter)
                .cachedIn(backgroundScope),
            countState = commentCountFlow.produceState(null),
            onSubmitCommentReaction = { comment, value, selected ->
                // Bangumi 评论只读, 不支持提交表情回应
                if (comment.source == UICommentSource.ANI) {
                    commentRepository.submitReaction(commentTarget, comment.sourceCommentId, value, selected)
                }
            },
            backgroundScope = backgroundScope,
            commentLoadFailures = commentLoadFailureChannel.receiveAsFlow(),
            onSubmitCommentVote = { comment, vote ->
                // Bangumi 评论只读, 不支持点赞
                if (comment.source == UICommentSource.ANI) {
                    commentRepository.submitVote(commentTarget, comment.sourceCommentId, vote?.toCommentVoteValue())
                }
            },
        )

        val reportState = CommentReportState(
            onSubmitReport = { comment, reason, detail ->
                commentReportService.createReport(
                    targetType = when (commentTarget) {
                        is PersonCommentTarget.Person -> CommentReportTargetType.PERSON_COMMENT
                        is PersonCommentTarget.Character -> CommentReportTargetType.CHARACTER_COMMENT
                    },
                    targetId = comment.sourceCommentId,
                    reason = reason.toDataReason(),
                    commentAuthorId = comment.author?.id,
                    detail = detail.takeIf { it.isNotEmpty() },
                    contentSnapshot = comment.reportSnapshotText(),
                )
            },
            backgroundScope = backgroundScope,
        )

        val editorState = CommentEditorState(
            showExpandEditCommentButton = true,
            initialEditExpanded = false,
            panelTitle = commentPanelTitleFlow.produceState(null),
            stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
                .produceState(emptyList()),
            richTextRenderer = { text ->
                withContext(Dispatchers.Default) {
                    with(CommentMapperContext) { parseBBCode(text) }
                }
            },
            onSend = { context, content -> postCommentUseCase(context, content) },
            backgroundScope = backgroundScope,
        )

        return PeopleCommentsState(
            target = commentTarget,
            commentState = commentState,
            reportState = reportState,
            editorState = editorState,
            originalCommentsUrl = originalCommentsUrl,
            onRefresh = {
                commentsRestarter.restart()
                detailsRestarter.restart()
            },
        )
    }
}

class PersonDetailsViewModel(personId: Int) : PeopleDetailsViewModel(
    commentTarget = PersonCommentTarget.Person(personId),
    originalCommentsUrl = "https://bgm.tv/person/$personId",
) {
    val details = personDetails
}

class CharacterDetailsViewModel(characterId: Int) : PeopleDetailsViewModel(
    commentTarget = PersonCommentTarget.Character(characterId),
    originalCommentsUrl = "https://bgm.tv/character/$characterId",
) {
    val details = characterDetails
}
