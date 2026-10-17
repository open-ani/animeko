/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi.test

import androidx.annotation.UiThread
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
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
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchConfig
import me.him188.ani.app.domain.mediasource.test.TestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.TestSubjectPresentation
import me.him188.ani.app.domain.mediasource.test.TestSubjectSearchResult
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiTestEpisode
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.coroutines.flows.combine as combineMany
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@Immutable
data class JsonApiTestPresentation(
    val isSearchingSubject: Boolean,
    val isSearchingEpisode: Boolean,
    val subjectSearchResult: TestSubjectSearchResult?,
    val episodeListSearchResult: TestEpisodeListResult?,
    val selectedSubject: TestSubjectPresentation?,
    val filteredEpisodes: List<JsonApiTestEpisode>?,
    val filterByChannel: String?,
    val selectedSubjectIndex: Int,
    val isPlaceholder: Boolean = false,
) {
    @Stable
    companion object {
        @Stable
        val Placeholder = JsonApiTestPresentation(
            isSearchingSubject = false,
            isSearchingEpisode = false,
            subjectSearchResult = null,
            episodeListSearchResult = null,
            selectedSubject = null,
            filteredEpisodes = null,
            filterByChannel = null,
            selectedSubjectIndex = 0,
            isPlaceholder = true,
        )
    }
}

/**
 * 测试页的状态: 把编辑中的配置与测试输入交给 [JsonApiMediaSourceTester], 汇总三阶段的结果供 UI 展示.
 */
@Stable
class JsonApiTestState(
    private val searchConfigState: State<JsonApiSearchConfig?>,
    private val tester: JsonApiMediaSourceTester,
    backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    private val selectedSubjectIndexFlow = MutableStateFlow(0)
    private val filterByChannelFlow = MutableStateFlow<String?>(null)

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
        val success = subjectSearchResult as? TestSubjectSearchResult.Success ?: return@combine null
        success.subjects.getOrNull(selectedSubjectIndex)
    }

    private val filteredEpisodesFlow = combine(
        tester.episodeListSelectionResultFlow,
        tester.testEpisodesFlow,
        filterByChannelFlow,
    ) { result, episodes, filterByChannel ->
        if (result !is TestEpisodeListResult.Success) return@combine null
        episodes.filter { filterByChannel == null || it.presentation.channel == filterByChannel }
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
    ) { isSearchingSubject,
        isSearchingEpisode,
        subjectSearchResult,
        episodeListSearchResult,
        selectedSubject,
        filteredEpisodes,
        filterByChannel,
        selectedSubjectIndex,
        ->
        JsonApiTestPresentation(
            isSearchingSubject = isSearchingSubject,
            isSearchingEpisode = isSearchingEpisode,
            subjectSearchResult = subjectSearchResult,
            episodeListSearchResult = episodeListSearchResult,
            selectedSubject = selectedSubject,
            filteredEpisodes = filteredEpisodes,
            filterByChannel = filterByChannel,
            selectedSubjectIndex = selectedSubjectIndex,
        )
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    @UiThread
    suspend fun observeChanges() {
        try {
            coroutineScope {
                launch {
                    combine(
                        snapshotFlow { searchKeyword },
                        snapshotFlow { searchConfigState.value?.autoMatch?.searchUseOnlyFirstWord },
                        snapshotFlow { searchConfigState.value?.autoMatch?.searchRemoveSpecial },
                    ) { searchKeyword, useOnlyFirstWord, removeSpecial ->
                        JsonApiMediaSourceTester.SubjectQuery(
                            searchKeyword = searchKeyword,
                            searchUseOnlyFirstWord = useOnlyFirstWord,
                            searchRemoveSpecial = removeSpecial,
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
                            tester.setSearchConfig(config)
                        }
                }

                launch {
                    snapshotFlow { sort }
                        .distinctUntilChanged()
                        .debounce(0.5.seconds)
                        .collect { sort ->
                            tester.setEpisodeQuery(JsonApiMediaSourceTester.EpisodeQuery(EpisodeSort(sort)))
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
}
