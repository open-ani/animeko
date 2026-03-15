/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.annotation.UiThread
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.test.web.SelectorMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodePresentation
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestSearchSubjectResult
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestSubjectPresentation
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSolveResult
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.coroutines.flows.combine as combineMany
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@Immutable
data class SelectorTestPresentation(
    val isSearchingSubject: Boolean,
    val isSearchingEpisode: Boolean,
    val subjectSearchResult: SelectorTestSearchSubjectResult?,
    val episodeListSearchResult: SelectorTestEpisodeListResult?,
    val selectedSubject: SelectorTestSubjectPresentation?,
    val filteredEpisodes: List<SelectorTestEpisodePresentation>?,
    val filterByChannel: String?,
    val selectedSubjectIndex: Int,
    val subjectCaptchaRequest: WebCaptchaRequest?,
    val episodeCaptchaRequest: WebCaptchaRequest?,
    val hasCaptchaSession: Boolean,
    val isHandlingCaptcha: Boolean,
    val isPlaceholder: Boolean = false,
) {
    @Stable
    companion object {
        @Stable
        val Placeholder = SelectorTestPresentation(
            isSearchingSubject = false,
            isSearchingEpisode = false,
            subjectSearchResult = null,
            episodeListSearchResult = null,
            selectedSubject = null,
            filteredEpisodes = null,
            filterByChannel = null,
            selectedSubjectIndex = 0,
            subjectCaptchaRequest = null,
            episodeCaptchaRequest = null,
            hasCaptchaSession = false,
            isHandlingCaptcha = false,
            isPlaceholder = true,
        )
    }
}

@Stable
class SelectorTestState(
    private val searchConfigState: State<SelectorSearchConfig?>,
    private val tester: SelectorMediaSourceTester,
    private val backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    private val selectedSubjectIndexFlow = MutableStateFlow(0)
    private val filterByChannelFlow = MutableStateFlow<String?>(null)
    private val isHandlingCaptchaFlow = MutableStateFlow(false)

    private val searchUrl by derivedStateOf {
        searchConfigState.value?.searchUrl
    }
    private val useOnlyFirstWord by derivedStateOf {
        searchConfigState.value?.searchUseOnlyFirstWord
    }

    val gridState = LazyGridState()

    fun selectSubjectIndex(index: Int) {
        selectedSubjectIndexFlow.value = index
        tester.setSubjectIndex(index)
    }

    fun filterByChannel(channel: String?) {
        filterByChannelFlow.value = channel
    }

    private val selectedSubjectFlow = combine(
        tester.subjectSelectionResultFlow,
        selectedSubjectIndexFlow,
    ) { subjectSearchResult, selectedSubjectIndex ->
        val success = subjectSearchResult as? SelectorTestSearchSubjectResult.Success ?: return@combine null
        success.subjects.getOrNull(selectedSubjectIndex)
    }

    private val filteredEpisodesFlow = combine(
        tester.episodeListSelectionResultFlow,
        filterByChannelFlow,
    ) { result, filterByChannel ->
        when (result) {
            is SelectorTestEpisodeListResult.Success -> result.episodes.filter {
                filterByChannel == null || it.channel == filterByChannel
            }

            is SelectorTestEpisodeListResult.ApiError -> null
            is SelectorTestEpisodeListResult.CaptchaRequired -> null
            SelectorTestEpisodeListResult.InvalidConfig -> null
            is SelectorTestEpisodeListResult.UnknownError -> null
        }
    }

    val presentation = combineMany(
        tester.subjectSearchRunning.isRunning,
        tester.episodeSearchRunning.isRunning,
        tester.subjectSelectionResultFlow,
        tester.episodeListSelectionResultFlow,
        selectedSubjectFlow,
        filteredEpisodesFlow,
        filterByChannelFlow,
        selectedSubjectIndexFlow,
        tester.hasCaptchaSession,
        isHandlingCaptchaFlow,
    ) { isSearchingSubject,
        isSearchingEpisode,
        subjectSearchResult,
        episodeListSearchResult,
        selectedSubject,
        filteredEpisodes,
        filterByChannel,
        selectedSubjectIndex,
        hasCaptchaSession,
        isHandlingCaptcha,
        ->
        val normalizedSubjectSearchResult = normalizeSubjectSearchResult(subjectSearchResult)
        val normalizedEpisodeListSearchResult =
            normalizeEpisodeListSearchResult(episodeListSearchResult, selectedSubject)
        SelectorTestPresentation(
            isSearchingSubject = isSearchingSubject,
            isSearchingEpisode = isSearchingEpisode,
            subjectSearchResult = normalizedSubjectSearchResult,
            episodeListSearchResult = normalizedEpisodeListSearchResult,
            selectedSubject = selectedSubject,
            filteredEpisodes = filteredEpisodes,
            filterByChannel = filterByChannel,
            selectedSubjectIndex = selectedSubjectIndex,
            subjectCaptchaRequest = (normalizedSubjectSearchResult as? SelectorTestSearchSubjectResult.CaptchaRequired)?.request,
            episodeCaptchaRequest = (normalizedEpisodeListSearchResult as? SelectorTestEpisodeListResult.CaptchaRequired)?.request,
            hasCaptchaSession = hasCaptchaSession,
            isHandlingCaptcha = isHandlingCaptcha,
        )
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private fun normalizeSubjectSearchResult(
        result: SelectorTestSearchSubjectResult?,
    ): SelectorTestSearchSubjectResult? {
        val apiError = result as? SelectorTestSearchSubjectResult.ApiError ?: return result
        val exception = apiError.exception
        if (exception !is RepositoryAuthorizationException && exception !is RepositoryRateLimitedException) {
            return result
        }
        val searchConfig = searchConfigState.value ?: return result
        if (searchConfig.searchUrl.isBlank()) return result
        val pageUrl = createSearchUrl(searchConfig)
        return SelectorTestSearchSubjectResult.CaptchaRequired(
            WebCaptchaRequest(
                mediaSourceId = tester.mediaSourceId,
                pageUrl = pageUrl,
                kind = WebCaptchaKind.Unknown,
            ),
        )
    }

    private fun normalizeEpisodeListSearchResult(
        result: SelectorTestEpisodeListResult?,
        selectedSubject: SelectorTestSubjectPresentation?,
    ): SelectorTestEpisodeListResult? {
        val apiError = result as? SelectorTestEpisodeListResult.ApiError ?: return result
        val exception = apiError.exception
        if (exception !is RepositoryAuthorizationException && exception !is RepositoryRateLimitedException) {
            return result
        }
        val pageUrl = selectedSubject?.subjectDetailsPageUrl ?: return result
        return SelectorTestEpisodeListResult.CaptchaRequired(
            WebCaptchaRequest(
                mediaSourceId = tester.mediaSourceId,
                pageUrl = pageUrl,
                kind = WebCaptchaKind.Unknown,
            ),
        )
    }

    private fun createSearchUrl(
        searchConfig: SelectorSearchConfig,
    ): String {
        val encodedUrl = MediaSourceEngineHelpers.encodeUrlSegment(
            MediaSourceEngineHelpers.getSearchKeyword(
                searchKeyword,
                searchConfig.searchRemoveSpecial,
                searchConfig.searchUseOnlyFirstWord,
            ),
        )
        return searchConfig.searchUrl.replace("{keyword}", encodedUrl)
    }

    @UiThread
    suspend fun observeChanges() {
        try {
            coroutineScope {
                launch {
                    combine(
                        snapshotFlow { searchKeyword },
                        snapshotFlow { searchUrl },
                        snapshotFlow { useOnlyFirstWord },
                        snapshotFlow { searchConfigState.value?.searchRemoveSpecial },
                    ) { searchKeyword, searchUrl, useOnlyFirstWord, searchRemoveSpecial ->
                        SelectorMediaSourceTester.SubjectQuery(
                            searchKeyword = searchKeyword,
                            searchUrl = searchUrl,
                            searchUseOnlyFirstWord = useOnlyFirstWord,
                            searchRemoveSpecial = searchRemoveSpecial,
                        )
                    }.distinctUntilChanged().debounce(0.5.seconds).collect { query ->
                        tester.setSubjectQuery(query)
                    }
                }

                launch {
                    snapshotFlow { searchConfigState.value }
                        .distinctUntilChanged()
                        .debounce(0.5.seconds)
                        .collect { config ->
                            tester.setSelectorSearchConfig(config)
                        }
                }

                launch {
                    snapshotFlow { sort }
                        .distinctUntilChanged()
                        .debounce(0.5.seconds)
                        .collect { sort ->
                            tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(sort)))
                        }
                }
            }
        } catch (e: CancellationException) {
            tester.clearSubjectQuery()
            throw e
        }
    }

    fun restartCurrentSubjectSearch() {
        tester.subjectSearchLifecycle.restart()
    }

    fun restartCurrentEpisodeSearch() {
        tester.episodeSearchLifecycle.restart()
    }

    fun solveCaptcha(request: WebCaptchaRequest, forEpisodeSearch: Boolean) {
        backgroundScope.launch {
            isHandlingCaptchaFlow.value = true
            try {
                if (tester.solveCaptchaInteractively(request) is WebCaptchaSolveResult.Solved) {
                    if (forEpisodeSearch) {
                        restartCurrentEpisodeSearch()
                    } else {
                        restartCurrentSubjectSearch()
                    }
                }
            } finally {
                isHandlingCaptchaFlow.value = false
            }
        }
    }

    fun resetCaptchaSession() {
        tester.resetCaptchaSession()
        restartCurrentSubjectSearch()
        restartCurrentEpisodeSearch()
    }
}
