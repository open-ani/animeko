/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import androidx.compose.runtime.mutableStateOf
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentMapperContext.toCommentVoteValue
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.reportSnapshotText
import me.him188.ani.app.ui.comment.toDataReason
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.foundation.TvNavigationEvents

/** One instance per navigation entry, including separate entries for a person's two roles. */
class TvPeopleDetailsViewModel(
    private val target: TvPeopleTarget,
    private val repository: PersonDetailsRepository,
    commentRepository: PersonCommentRepository,
    reportService: AniCommentReportService,
    session: SessionStateProvider,
) : AbstractViewModel() {
    private val commentTarget = if (target.kind == TvPeopleKind.Character) PersonCommentTarget.Character(target.id)
        else PersonCommentTarget.Person(target.id)
    private val feedback = Channel<LoadError>(Channel.BUFFERED)
    val errors = feedback.receiveAsFlow()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private val loggedIn = session.stateFlow.map { it is SessionState.Valid }
        .stateIn(backgroundScope, SharingStarted.Eagerly, null)
    private var detailsJob: Job? = null
    private var reportJob: Job? = null
    private val comments = CommentState(
        list = commentRepository.commentsPager(commentTarget, onBangumiUnavailable = ::onBangumiUnavailable)
            .map { page -> page.map { it.parseToUIComment() } }.cachedIn(backgroundScope),
        // A mixed-source total is not authoritative until pagination finishes; UI derives the count.
        countState = mutableStateOf(null),
        onSubmitCommentReaction = { comment, value, selected ->
            if (comment.source == UICommentSource.ANI) {
                commentRepository.submitReaction(commentTarget, comment.sourceCommentId, value, selected)
            }
        },
        onSubmitCommentVote = { comment, vote ->
            if (comment.source == UICommentSource.ANI) {
                commentRepository.submitVote(commentTarget, comment.sourceCommentId, vote?.toCommentVoteValue())
            }
        },
        backgroundScope = backgroundScope,
    )
    private val reports = CommentReportState(
        onSubmitReport = { comment, reason, detail ->
            reportService.createReport(
                targetType = if (target.kind == TvPeopleKind.Character) CommentReportTargetType.CHARACTER_COMMENT
                    else CommentReportTargetType.PERSON_COMMENT,
                targetId = comment.sourceCommentId, reason = reason.toDataReason(),
                commentAuthorId = comment.author?.id, detail = detail.takeIf { it.isNotEmpty() },
                contentSnapshot = comment.reportSnapshotText(),
            )
        }, backgroundScope = backgroundScope,
    )
    private val state = MutableStateFlow(TvPeopleDetailsUiState(
        target = target,
        subjects = if (target.kind == TvPeopleKind.Character) repository.characterSubjectsPager(target.id).cachedIn(backgroundScope) else null,
        casts = if (target.kind == TvPeopleKind.VoiceActor) repository.personCastsPager(target.id).cachedIn(backgroundScope) else null,
        works = if (target.kind != TvPeopleKind.Character) repository.personWorksPager(target.id).cachedIn(backgroundScope) else null,
        comments = comments.list, commentPresentation = comments::withOverlay,
    ))
    val uiState = state.asStateFlow()

    init {
        load()
        backgroundScope.launch { comments.actionSubmitFailures.collect { feedback.send(LoadError.fromException(it)) } }
    }

    private fun onBangumiUnavailable() { state.update { it.copy(bangumiUnavailable = true) } }

    fun onIntent(intent: TvPeopleIntent) {
        when (intent) {
            TvPeopleIntent.Retry -> load()
            TvPeopleIntent.CommentsRefreshed -> comments.clearStaleOverlays()
            is TvPeopleIntent.OpenPerson -> navigation.emit(when (intent.target.kind) {
                TvPeopleKind.Character -> TvNavigationEvent.Character(intent.target.id)
                TvPeopleKind.VoiceActor -> TvNavigationEvent.VoiceActor(intent.target.id)
                TvPeopleKind.Staff -> TvNavigationEvent.Staff(intent.target.id)
            })
            is TvPeopleIntent.OpenSubject -> navigation.emit(TvNavigationEvent.Subject(intent.subject.subjectId,
                SubjectDetailPlaceholder(intent.subject.subjectId, intent.subject.name, intent.subject.nameCn, intent.subject.imageLarge)))
            is TvPeopleIntent.Vote -> if (intent.comment.source == UICommentSource.ANI && requireLogin()) {
                comments.toggleVote(intent.comment, intent.vote)
            }
            is TvPeopleIntent.Report -> {
                if (reportJob?.isActive == true || !requireLogin()) return
                state.update { it.copy(reportBusy = true, reportCompleted = null) }
                reportJob = backgroundScope.launch {
                    val result = reports.submitAwait(intent.comment, intent.reason, "")
                    state.update { it.copy(reportBusy = false, reportCompleted = intent.requestId.takeIf { result.isSuccess }) }
                    result.exceptionOrNull()?.let { feedback.send(LoadError.fromException(it)) }
                }
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (loggedIn.value == false) navigation.emit(TvNavigationEvent.Login)
        return loggedIn.value == true
    }

    private fun load() {
        detailsJob?.cancel()
        state.update { it.copy(loading = true, error = null) }
        detailsJob = backgroundScope.launch {
            try {
                val profile = when (target.kind) {
                    TvPeopleKind.Character -> TvPeopleProfile.from(repository.characterDetailsFlow(target.id).first())
                    else -> TvPeopleProfile.from(repository.personDetailsFlow(target.id).first())
                }
                state.update { it.copy(profile = profile, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(loading = false, error = LoadError.fromException(e)) } }
        }
    }
}
