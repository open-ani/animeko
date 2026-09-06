/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.subject

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.foundation.TvNavigationEvents

class TvSubjectDetailsViewModel(
    private val subjectId: Int,
    private val placeholder: SubjectInfo?,
    factory: SubjectDetailsStateFactory,
    private val collectionRepository: SubjectCollectionRepository,
    private val tmdb: TmdbImageService,
) : AbstractViewModel() {
    private val loader = SubjectDetailsStateLoader(factory, backgroundScope)
    private val images = MutableStateFlow(TvSubjectImages())
    private var imagesJob: Job? = null
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    private val details = loader.state.flatMapLatest { loaded ->
        when (loaded) {
            is SubjectDetailsUIState.Placeholder -> flowOf(TvSubjectDetailsUiState())
            is SubjectDetailsUIState.Err -> flowOf(TvSubjectDetailsUiState(error = loaded.error))
            is SubjectDetailsUIState.Ok -> {
                val details = loaded.value
                combine(details.presentation, snapshotFlow {
                    DetailSnapshot(
                        details.subjectProgressState.episodeIdToPlay,
                        details.totalCharactersCountState.value,
                        details.totalStaffCountState.value,
                        details.subjectCommentState.count,
                    )
                }) { presentation, snapshot ->
                    val episodes = presentation.episodeListUiState.mainEpisodes + presentation.episodeListUiState.otherEpisodes
                    TvSubjectDetailsUiState(content = TvSubjectDetailsContentState(
                        info = details.info ?: SubjectInfo.Empty,
                        episodes = episodes,
                        episodesLoading = presentation.isPlaceholder || presentation.episodeListUiState.isPlaceholder,
                        playTargetId = selectTvResumeEpisode(snapshot.resumeEpisodeId, episodes),
                        watchedCount = episodes.count { it.isDoneOrDropped },
                        exposedCharactersPager = details.exposedCharactersPager,
                        totalCharactersCount = snapshot.charactersCount,
                        exposedStaffPager = details.exposedStaffPager,
                        totalStaffCount = snapshot.staffCount,
                        relatedSubjectsPager = details.relatedSubjectsPager,
                        commentsPager = details.subjectCommentState.list,
                        commentCount = snapshot.commentCount,
                    ))
                }
            }
        }
    }
    val uiState = combine(details, images) { state, images -> state.copy(images = images) }
        .stateIn(backgroundScope, SharingStarted.Eagerly, TvSubjectDetailsUiState())

    init {
        loader.load(subjectId, placeholder)
        loadImages()
    }

    fun onIntent(intent: TvSubjectDetailsIntent) {
        when (intent) {
            TvSubjectDetailsIntent.Retry -> {
                loader.load(subjectId, placeholder, force = true)
                loadImages()
            }
            TvSubjectDetailsIntent.Resume -> uiState.value.content?.playTargetId?.let(::playEpisode)
            is TvSubjectDetailsIntent.PlayEpisode -> playEpisode(intent.episodeId)
            is TvSubjectDetailsIntent.OpenRelatedSubject -> navigation.emit(TvNavigationEvent.Subject(intent.subjectId))
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
            images.value = TvSubjectImages()
            val collection = loadOrNull { collectionRepository.subjectCollectionFlow(subjectId).first() }
            if (collection == null) {
                images.update { it.copy(backdrop = TvBackdropState(null)) }
                return@launch
            }
            images.update { it.copy(collection = collection) }
            val newest = collection.episodes.newestAiredDateStringOrNull()
            val backdrop = loadOrNull {
                tmdb.getBackdropUrl(subjectId, collection.subjectInfo.name, activeAsOfDate = newest)
            }
            images.update { it.copy(backdrop = TvBackdropState(backdrop)) }
            val stills = loadOrNull {
                tmdb.getEpisodeStills(subjectId, collection.subjectInfo.name, "zh-CN", newestWantedAirDate = newest)
                    .matchToEpisodes(collection.episodes)
                    .mapNotNull { (id, media) -> media.stillUrl?.let { id to it } }.toMap()
            }.orEmpty()
            images.update { it.copy(episodeStills = stills) }
        }
    }

    private suspend fun <T> loadOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private data class DetailSnapshot(
        val resumeEpisodeId: Int?,
        val charactersCount: Int?,
        val staffCount: Int?,
        val commentCount: Int?,
    )
}

/** Shared progress wins; otherwise start at the first unfinished episode, then the first episode. */
internal fun selectTvResumeEpisode(resumeEpisodeId: Int?, episodes: List<EpisodeListItem>): Int? =
    resumeEpisodeId?.takeIf { id -> episodes.any { it.episodeId == id } }
        ?: episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId
        ?: episodes.firstOrNull()?.episodeId
