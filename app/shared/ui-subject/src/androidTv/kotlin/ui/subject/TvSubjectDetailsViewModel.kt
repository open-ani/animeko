/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.subject

import androidx.compose.runtime.snapshotFlow
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.details.SubjectDetailsLoadState
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.topic.toggleCollected
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

class TvSubjectDetailsViewModel(
    private val subjectId: Int,
    private val placeholder: SubjectInfo?,
    koin: Koin,
) : SubjectDetailsViewModel(subjectId, placeholder, koin) {
    private val images = MutableStateFlow(TvSubjectImages())
    private val operation = MutableStateFlow(TvSubjectOperation())
    private val tagResults = MutableStateFlow<TvTagResults?>(null)
    private val reportDraft = MutableStateFlow<TvSubjectReportDraft?>(null)
    private var imagesJob: Job? = null
    private var operationJob: Job? = null
    private val feedback = Channel<LoadError>(Channel.BUFFERED)
    val errors = feedback.receiveAsFlow()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private val loggedIn = authState.map { it.isSessionValid }
        .stateIn(backgroundScope, SharingStarted.Eagerly, null)

    // Keep paging consumers mounted across loader refreshes. Paging retains the current
    // generation until the replacement has data, including lazy item/focus identities.
    private fun <T : Any> pager(select: (SubjectDetailsState) -> Flow<PagingData<T>>) = state
        .filterIsInstance<SubjectDetailsLoadState.Ok>()
        .flatMapLatest { select(it.value) }
        .cachedIn(backgroundScope)

    private val charactersPager = pager { it.charactersPager }
    private val staffPager = pager { it.staffPager }
    private val relatedPager = pager { it.relatedSubjectsPager }
    private val commentsPager = pager { it.subjectCommentState.list }

    private val details = state.flatMapLatest { loaded ->
        when (loaded) {
            is SubjectDetailsLoadState.Placeholder -> flowOf(TvSubjectDetailsUiState(refreshing = true))
            is SubjectDetailsLoadState.Err -> flowOf(TvSubjectDetailsUiState(error = loaded.error))
            is SubjectDetailsLoadState.Ok -> {
                val shared = loaded.value
                combine(shared.uiState, snapshotFlow { shared.subjectCommentState.count }) { ui, commentCount ->
                    val episodeList = ui.episodeListUiState
                    val episodes = episodeList.mainEpisodes + episodeList.otherEpisodes
                    TvSubjectDetailsUiState(content = TvSubjectDetailsContentState(
                        info = shared.info ?: SubjectInfo.Empty,
                        episodes = episodes,
                        episodesLoading = ui.isPlaceholder || episodeList.isPlaceholder,
                        playTargetId = selectTvResumeEpisode(ui.progressInfo?.nextEpisodeIdToPlay, episodes),
                        watchedCount = episodes.count { it.isDoneOrDropped },
                        exposedCharactersPager = shared.exposedCharactersPager,
                        totalCharactersCount = ui.totalCharactersCount,
                        exposedStaffPager = shared.exposedStaffPager,
                        totalStaffCount = ui.totalStaffCount,
                        relatedSubjectsPager = relatedPager,
                        commentsPager = commentsPager,
                        commentCount = commentCount,
                        collectionType = ui.selfCollectionType,
                        selfRating = ui.rating.selfRatingInfo,
                        mainEpisodeIds = episodeList.mainEpisodes.mapTo(mutableSetOf()) { it.episodeId },
                        charactersPager = charactersPager,
                        staffPager = staffPager,
                        commentPresentation = shared.subjectCommentState::withOverlay,
                        canReport = shared.subjectCommentReportState != null,
                        airing = AiringLabelState(ui.airingInfo, ui.progressInfo),
                        progress = SubjectProgressState(ui.progressInfo),
                    ))
                }
            }
        }
    }.runningFold(TvSubjectDetailsUiState()) { previous, next ->
        // Refreshing does not unmount useful content or reset its focus and viewport.
        val replacement = next.content
        if (replacement?.episodesLoading == true && previous.content != null) {
            next.copy(content = previous.content, refreshing = true)
        } else next.copy(content = replacement ?: previous.content)
    }
    private val auxiliary = combine(operation, tagResults, loggedIn, reportDraft) { action, tags, login, draft ->
        TvSubjectDetailsUiState(operation = action, tagResults = tags, loggedIn = login, reportDraft = draft)
    }
    val uiState = combine(details, images, auxiliary) { state, pictures, extra ->
        state.copy(
            images = pictures, operation = extra.operation,
            tagResults = extra.tagResults, loggedIn = extra.loggedIn,
            reportDraft = extra.reportDraft,
        )
    }.stateIn(backgroundScope, SharingStarted.Eagerly, TvSubjectDetailsUiState())

    init {
        load()
        loadImages()
        backgroundScope.launch {
            state.collectLatest { state ->
                if (state !is SubjectDetailsLoadState.Ok) return@collectLatest
                coroutineScope {
                    launch { state.value.subjectCommentState.actionSubmitFailures.collect { feedback.send(LoadError.fromException(it)) } }
                    launch { state.value.subjectCommentState.commentLoadFailures.collect { feedback.send(LoadError.fromException(it)) } }
                    awaitCancellation()
                }
            }
        }
    }

    fun onIntent(intent: TvSubjectDetailsIntent) {
        when (intent) {
            TvSubjectDetailsIntent.Retry -> {
                if (operation.value.busy) return
                reload()
                loadImages()
            }
            TvSubjectDetailsIntent.Login -> navigation.emit(TvNavigationEvent.Login)
            TvSubjectDetailsIntent.Resume -> uiState.value.content?.playTargetId?.let(::playEpisode)
            is TvSubjectDetailsIntent.PlayEpisode -> playEpisode(intent.episodeId)
            is TvSubjectDetailsIntent.OpenRelatedSubject -> navigation.emit(TvNavigationEvent.Subject(intent.subjectId))
            is TvSubjectDetailsIntent.SetCollection -> perform(intent.requestId) { shared ->
                val error = shared.setSelfCollectionType(intent.type)
                val offer = error == null && shared.shouldOfferMarkAllWatched
                shared.dismissSetAllEpisodesDoneDialog()
                TvSubjectOperation(error = error, offerMarkAllWatched = offer)
            }
            is TvSubjectDetailsIntent.MarkAllWatched -> perform(intent.requestId) {
                TvSubjectOperation(error = it.setAllEpisodesWatchedAwait())
            }
            is TvSubjectDetailsIntent.SetScore -> {
                if (intent.score !in 0..10 || currentDetails()?.uiState?.value?.rating?.enableEdit != true) return
                perform(intent.requestId) { TvSubjectOperation(error = it.updateScore(intent.score)) }
            }
            is TvSubjectDetailsIntent.ToggleEpisode -> {
                val episode = uiState.value.content?.episodes?.find { it.episodeId == intent.episodeId } ?: return
                perform(intent.requestId) {
                    TvSubjectOperation(error = LoadError.runAndWrapOrThrowCancellation {
                        setEpisodeCollectionType(subjectId, episode.episodeId, episode.collectionType.toggleCollected())
                    })
                }
            }
            is TvSubjectDetailsIntent.OpenCharacter -> navigation.emit(TvNavigationEvent.Character(intent.characterId))
            is TvSubjectDetailsIntent.OpenStaff -> navigation.emit(TvNavigationEvent.Staff(intent.personId))
            is TvSubjectDetailsIntent.SearchTag -> {
                val pager = searchTag(intent.tag)
                    .map { page -> page.map { item ->
                        val info = item.subjectInfo
                        PersonSubjectSummary(info.subjectId, info.name, info.nameCn, info.imageLarge)
                    } }.cachedIn(backgroundScope)
                tagResults.value = TvTagResults(intent.tag, pager)
            }
            is TvSubjectDetailsIntent.Vote -> {
                if (!requireLogin() || intent.comment.source != UICommentSource.ANI) return
                currentDetails()?.subjectCommentState?.toggleVote(intent.comment, intent.vote)
            }
            TvSubjectDetailsIntent.CommentsRefreshed -> currentDetails()?.subjectCommentState?.clearStaleOverlays()
            is TvSubjectDetailsIntent.ChooseReportReason -> {
                reportDraft.value = TvSubjectReportDraft(intent.commentId, intent.reason)
            }
            is TvSubjectDetailsIntent.Report -> perform(intent.requestId) { shared ->
                val reportState = checkNotNull(shared.subjectCommentReportState)
                val result = reportState.submitAwait(intent.comment, intent.reason, "")
                TvSubjectOperation(error = result.exceptionOrNull()?.let(LoadError::fromException))
            }
        }
    }

    private fun currentDetails(): SubjectDetailsState? = (state.value as? SubjectDetailsLoadState.Ok)?.value

    private fun requireLogin(): Boolean {
        if (loggedIn.value == false) navigation.emit(TvNavigationEvent.Login)
        return loggedIn.value == true
    }

    private fun perform(requestId: Int, block: suspend (SubjectDetailsState) -> TvSubjectOperation) {
        if (operationJob?.isActive == true || !requireLogin()) return
        val shared = currentDetails() ?: return
        operation.value = TvSubjectOperation(requestId, busy = true)
        operationJob = backgroundScope.launch {
            val result = try { block(shared) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { TvSubjectOperation(error = LoadError.fromException(e)) }
            operation.value = result.copy(requestId = requestId, completed = true)
        }
    }

    private fun playEpisode(episodeId: Int) {
        val content = uiState.value.content ?: return
        if (content.episodesLoading || content.episodes.none { it.episodeId == episodeId }) return
        navigation.emit(TvNavigationEvent.Episode(subjectId, episodeId))
    }

    private fun loadImages() {
        imagesJob?.cancel()
        imagesJob = backgroundScope.launch {
            val collection = loadOrNull { subjectCollection.first() }
            if (collection == null) {
                images.update { it.copy(backdrop = TvBackdropState(null)) }
                return@launch
            }
            val backdrop = collection.subjectInfo.tmdbArt?.primaryBackdrop?.medium
            val stills = collection.episodes.mapNotNull { episode ->
                episode.episodeInfo.imageLarge?.let { episode.episodeId to it }
            }.toMap()
            images.update { it.copy(backdrop = TvBackdropState(backdrop), episodeStills = stills) }
        }
    }

    private suspend fun <T> loadOrNull(block: suspend () -> T): T? = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { null }
}

internal fun selectTvResumeEpisode(resumeEpisodeId: Int?, episodes: List<EpisodeListItem>): Int? =
    resumeEpisodeId?.takeIf { id -> episodes.any { it.episodeId == id } }
        ?: episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId
        ?: episodes.firstOrNull()?.episodeId
