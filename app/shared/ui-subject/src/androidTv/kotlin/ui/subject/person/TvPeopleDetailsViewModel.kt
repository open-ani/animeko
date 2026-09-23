/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.subject.person

import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.subject.person.PeopleDetailsViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

/** One instance per navigation entry, including separate entries for a person's two roles. */
class TvPeopleDetailsViewModel(
    private val target: TvPeopleTarget,
    koin: Koin,
) : PeopleDetailsViewModel(
    commentTarget = if (target.kind == TvPeopleKind.Character) PersonCommentTarget.Character(target.id)
        else PersonCommentTarget.Person(target.id),
    originalCommentsUrl = if (target.kind == TvPeopleKind.Character) "https://bgm.tv/character/${target.id}"
        else "https://bgm.tv/person/${target.id}",
    koin = koin,
) {
    private val session = koin.get<SessionStateProvider>()
    override val commentPanelTitleFlow = MutableStateFlow<String?>(null)
    // The TV list derives its mixed-source total after pagination completes.
    override val commentCountFlow = flowOf<Int?>(null)
    private var detailsJob: Job? = null
    private val feedback = Channel<LoadError>(Channel.BUFFERED)
    val errors = feedback.receiveAsFlow()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private val loggedIn = session.stateFlow.map { it is SessionState.Valid }
        .stateIn(backgroundScope, SharingStarted.Eagerly, null)
    private var reportJob: Job? = null
    private val commentState get() = comments.commentState
    private val reports get() = comments.reportState
    private val state = MutableStateFlow(TvPeopleDetailsUiState(
        target = target,
        subjects = if (target.kind == TvPeopleKind.Character) repository.characterSubjectsPager(target.id).cachedIn(backgroundScope) else null,
        casts = if (target.kind == TvPeopleKind.VoiceActor) repository.personCastsPager(target.id).cachedIn(backgroundScope) else null,
        works = if (target.kind != TvPeopleKind.Character) repository.personWorksPager(target.id).cachedIn(backgroundScope) else null,
        comments = commentState.list, commentPresentation = commentState::withOverlay,
    ))
    val uiState = state.asStateFlow()

    init {
        load()
        backgroundScope.launch { commentState.commentLoadFailures.collect { onBangumiUnavailable() } }
        backgroundScope.launch { commentState.actionSubmitFailures.collect { feedback.send(LoadError.fromException(it)) } }
    }

    private fun onBangumiUnavailable() { state.update { it.copy(bangumiUnavailable = true) } }

    fun onIntent(intent: TvPeopleIntent) {
        when (intent) {
            TvPeopleIntent.Retry -> load()
            TvPeopleIntent.CommentsRefreshed -> commentState.clearStaleOverlays()
            is TvPeopleIntent.OpenPerson -> navigation.emit(when (intent.target.kind) {
                TvPeopleKind.Character -> TvNavigationEvent.Character(intent.target.id)
                TvPeopleKind.VoiceActor -> TvNavigationEvent.VoiceActor(intent.target.id)
                TvPeopleKind.Staff -> TvNavigationEvent.Staff(intent.target.id)
            })
            is TvPeopleIntent.OpenSubject -> navigation.emit(TvNavigationEvent.Subject(intent.subject.subjectId,
                SubjectDetailPlaceholder(intent.subject.subjectId, intent.subject.name, intent.subject.nameCn, intent.subject.imageLarge)))
            is TvPeopleIntent.Vote -> if (intent.comment.source == UICommentSource.ANI && requireLogin()) {
                commentState.toggleVote(intent.comment, intent.vote)
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
                commentPanelTitleFlow.value = profile.name
                state.update { it.copy(profile = profile, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(loading = false, error = LoadError.fromException(e)) } }
        }
    }
}
