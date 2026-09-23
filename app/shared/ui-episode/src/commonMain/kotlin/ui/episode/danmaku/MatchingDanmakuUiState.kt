/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import kotlin.coroutines.coroutineContext


/**
 * Represents the UI state for a flow where the user:
 * 1) Inputs a query,
 * 2) Sees a list of subjects,
 * 3) Selects a subject and sees its episode list,
 * 4) Selects an episode, and
 * 5) Eventually triggers a callback returning the fetched Danmaku list.
 */
data class MatchingDanmakuUiState(
    val initialQuery: String = "",
    val isLoadingSubjects: Boolean = false,
    val subjects: List<DanmakuSubject> = emptyList(),
    val subjectError: String? = null,

    val selectedSubject: DanmakuSubject? = null,
    val isLoadingEpisodes: Boolean = false,
    val episodes: List<DanmakuEpisode> = emptyList(),
    val episodeError: String? = null,

    val selectedEpisode: DanmakuEpisode? = null,
    val isLoadingDanmaku: Boolean = false,
    val danmakuFetchResults: List<DanmakuFetchResult> = emptyList(),
    val danmakuError: String? = null,

    /**
     * If set to true, indicates that the entire flow is completed
     * (e.g., the user has selected an episode and the Danmaku results
     * have been fetched or processed). A ViewModel might set this
     * once everything is done, so the UI can respond (e.g., close
     * the screen or navigate away).
     */
    val isFlowComplete: Boolean = false,
)

/**
 * Produces and manages [MatchingDanmakuUiState].
 *
 * Typical usage:
 * 2) The user submits the query (call [submitQuery]) => loads subjects.
 * 3) The user selects a subject (call [selectSubject]) => loads episodes.
 * 4) The user selects an episode (call [selectEpisode]) => loads danmaku results, flow completes.
 */
class MatchingDanmakuPresenter(
    private val matchingDanmakuProvider: MatchingDanmakuProvider,
    private val coroutineScope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(MatchingDanmakuUiState())
    val uiState: StateFlow<MatchingDanmakuUiState> = _uiState.asStateFlow()
    val providerId get() = matchingDanmakuProvider.providerId
    private var requestJob: Job? = null

    private fun request(block: suspend () -> Unit) {
        val previous = requestJob
        previous?.cancel()
        requestJob = coroutineScope.launch {
            previous?.join()
            block()
        }
    }

    fun cancel() {
        requestJob?.cancel()
        _uiState.update { it.copy(isLoadingSubjects = false, isLoadingEpisodes = false, isLoadingDanmaku = false) }
    }

    fun backToSubjects() {
        cancel()
        _uiState.update { it.copy(selectedSubject = null, selectedEpisode = null, episodes = emptyList(), isFlowComplete = false) }
    }

    fun submitQuery(query: String) = request {
        _uiState.value = MatchingDanmakuUiState(initialQuery = query, isLoadingSubjects = true)
        try {
            val subjects = matchingDanmakuProvider.fetchSubjectList(query)
            coroutineContext.ensureActive()
            _uiState.update { it.copy(isLoadingSubjects = false, subjects = subjects) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingSubjects = false, subjectError = e.message ?: "Search failed") }
        }
    }

    fun selectSubject(subject: DanmakuSubject) = request {
        _uiState.update { it.copy(
            selectedSubject = subject, selectedEpisode = null, episodes = emptyList(),
            danmakuFetchResults = emptyList(), isLoadingEpisodes = true, episodeError = null,
            isLoadingSubjects = false, isLoadingDanmaku = false, danmakuError = null, isFlowComplete = false,
        ) }
        try {
            val episodes = matchingDanmakuProvider.fetchEpisodeList(subject)
            coroutineContext.ensureActive()
            _uiState.update { it.copy(isLoadingEpisodes = false, episodes = episodes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingEpisodes = false, episodeError = e.message ?: "Episode loading failed") }
        }
    }

    fun selectEpisode(episode: DanmakuEpisode) = request {
        val subject = _uiState.value.selectedSubject ?: return@request
        _uiState.update { it.copy(
            selectedEpisode = episode, danmakuFetchResults = emptyList(),
            isLoadingDanmaku = true, danmakuError = null, isFlowComplete = false,
        ) }
        try {
            val results = matchingDanmakuProvider.fetchDanmakuList(subject, episode)
            coroutineContext.ensureActive()
            _uiState.update { it.copy(isLoadingDanmaku = false, danmakuFetchResults = results, isFlowComplete = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingDanmaku = false, danmakuError = e.message ?: "Danmaku loading failed") }
        }
    }
}
