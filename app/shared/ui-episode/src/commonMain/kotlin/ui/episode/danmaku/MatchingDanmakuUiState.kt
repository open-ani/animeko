/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider


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

    /**
     * Trigger the search for [DanmakuSubject] based on the current query.
     */
    fun submitQuery(query: String) {
        // Reset and load subjects
        coroutineScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSubjects = true,
                    subjectError = null,
                    subjects = emptyList(),
                    selectedSubject = null,
                    episodes = emptyList(),
                    selectedEpisode = null,
                    danmakuFetchResults = emptyList(),
                    danmakuError = null,
                    isFlowComplete = false,
                )
            }
            try {
                val list = matchingDanmakuProvider.fetchSubjectList(query)
                _uiState.update { state ->
                    state.copy(
                        isLoadingSubjects = false,
                        subjectError = null,
                        subjects = list,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(isLoadingSubjects = false, subjectError = e.message)
                }
            }
        }
    }

    /**
     * Called when the user chooses a subject from the list.
     * We fetch the episode list immediately after selection.
     */
    fun selectSubject(subject: DanmakuSubject) {
        _uiState.update {
            it.copy(
                selectedSubject = subject,
                episodes = emptyList(),
                selectedEpisode = null,
                danmakuFetchResults = emptyList(),
                danmakuError = null,
                // If we want to show a loading indicator for episodes:
                isLoadingEpisodes = true,
                episodeError = null,
            )
        }

        coroutineScope.launch {
            try {
                val episodes = matchingDanmakuProvider.fetchEpisodeList(subject)
                _uiState.update { state ->
                    state.copy(
                        isLoadingEpisodes = false,
                        episodeError = null,
                        episodes = episodes,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(isLoadingEpisodes = false, episodeError = e.message)
                }
            }
        }
    }

    /**
     * Called when the user chooses an episode. We fetch final Danmaku results
     * and mark the flow as complete.
     */
    fun selectEpisode(episode: DanmakuEpisode) {
        // Update UI to reflect selected episode
        _uiState.update {
            it.copy(
                selectedEpisode = episode,
                danmakuFetchResults = emptyList(),
                danmakuError = null,
                isLoadingDanmaku = true,
                isFlowComplete = false,
            )
        }

        coroutineScope.launch {
            try {
                val danmakuList = matchingDanmakuProvider.fetchDanmakuList(
                    subject = _uiState.value.selectedSubject!!,
                    episode = episode,
                )
                _uiState.update { state ->
                    state.copy(
                        isLoadingDanmaku = false,
                        danmakuFetchResults = danmakuList,
                        danmakuError = null,
                        isFlowComplete = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoadingDanmaku = false,
                        danmakuError = e.message,
                        isFlowComplete = false,
                    )
                }
            }
        }
    }
}
