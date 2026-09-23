/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.subject.person

import androidx.paging.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.domain.foundation.LoadError
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
    private val feedback = Channel<LoadError>(Channel.BUFFERED)
    val errors = feedback.receiveAsFlow()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private val loggedIn = authState.map { it.isSessionValid }
        .stateIn(backgroundScope, SharingStarted.Eagerly, null)
    private var reportJob: Job? = null
    private val commentState get() = comments.commentState
    private val reports get() = comments.reportState
    private val state = MutableStateFlow(TvPeopleDetailsUiState(
        target = target,
        subjects = if (target.kind == TvPeopleKind.Character) subjectsPager else null,
        casts = if (target.kind == TvPeopleKind.VoiceActor) castsPager else null,
        works = if (target.kind != TvPeopleKind.Character) worksPager else null,
        comments = commentState.list, commentPresentation = commentState::withOverlay,
    ))
    val uiState = state.asStateFlow()

    init {
        backgroundScope.launch {
            combine(personDetails, characterDetails, detailsLoadError) { person, character, error ->
                Triple(person?.let(TvPeopleProfile::from) ?: character?.let(TvPeopleProfile::from), error, error == null && person == null && character == null)
            }.collect { (profile, error, loading) ->
                state.update { it.copy(profile = profile, error = error, loading = loading) }
            }
        }
        backgroundScope.launch { commentState.commentLoadFailures.collect { onBangumiUnavailable() } }
        backgroundScope.launch { commentState.actionSubmitFailures.collect { feedback.send(LoadError.fromException(it)) } }
    }

    private fun onBangumiUnavailable() { state.update { it.copy(bangumiUnavailable = true) } }

    fun onIntent(intent: TvPeopleIntent) {
        when (intent) {
            TvPeopleIntent.Retry -> reloadDetails()
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

}
