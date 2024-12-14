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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastDistinctBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.combine
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.xml.Document
import kotlin.coroutines.CoroutineContext
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
            isPlaceholder = true,
        )
    }
}

@Stable
class SelectorTestState(
    private val searchConfigState: State<SelectorSearchConfig?>,
    engine: SelectorMediaSourceEngine,
    backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    private val tester = SelectorMediaSourceTester(engine)

    var selectedSubjectIndex by mutableIntStateOf(0)
        private set

    fun selectSubjectIndex(index: Int) {
        selectedSubjectIndex = index
        tester.setSubjectIndex(index)
    }

    var filterByChannel by mutableStateOf<String?>(null)

    private val selectedSubjectFlow = tester.subjectSelectionResultFlow.map { subjectSearchSelectResult ->
        val success = subjectSearchSelectResult as? SelectorTestSearchSubjectResult.Success
            ?: return@map null
        success.subjects.getOrNull(selectedSubjectIndex)
    }

    private val searchUrl by derivedStateOf {
        searchConfigState.value?.searchUrl
    }
    private val useOnlyFirstWord by derivedStateOf {
        searchConfigState.value?.searchUseOnlyFirstWord
    }

    private val filteredEpisodesFlow = tester.episodeListSelectionResultFlow.map { result ->
        when (result) {
            is SelectorTestEpisodeListResult.Success -> result.episodes.filter {
                filterByChannel == null || it.channel == filterByChannel
            }

            is SelectorTestEpisodeListResult.ApiError -> null
            SelectorTestEpisodeListResult.InvalidConfig -> null
            is SelectorTestEpisodeListResult.UnknownError -> null
        }
    }

    val presentation = combine(
        tester.subjectSearchRunning.isRunning,
        tester.episodeSearchRunning.isRunning,
        tester.subjectSelectionResultFlow,
        tester.episodeListSelectionResultFlow,
        selectedSubjectFlow,
        filteredEpisodesFlow,
    ) { isSearchingSubject, isSearchingEpisode, subjectSearchSelectResult, episodeListSearchSelectResult, selectedSubject, filteredEpisodes ->
        SelectorTestPresentation(
            isSearchingSubject,
            isSearchingEpisode,
            subjectSearchSelectResult,
            episodeListSearchSelectResult,
            selectedSubject,
            filteredEpisodes,
        )
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5000), 1)

    @UiThread
    suspend fun observeChanges() {
        // 监听各 UI 编辑属性的变化, 发起重新搜索

        try {
            coroutineScope {
                launch {
                    combine(
                        snapshotFlow { searchKeyword },
                        snapshotFlow { searchUrl },
                        snapshotFlow { useOnlyFirstWord },
                        snapshotFlow { searchConfigState.value },
                    ) { searchKeyword, searchUrl, useOnlyFirstWord, searchConfigState ->
                        SelectorMediaSourceTester.SubjectQuery(
                            searchKeyword,
                            searchUrl,
                            useOnlyFirstWord,
                            searchConfigState?.searchRemoveSpecial,
                        )
                    }.distinctUntilChanged().debounce(0.5.seconds).collect { query ->
                        tester.setSubjectQuery(query)
                    }
                }

                launch {
                    snapshotFlow { searchConfigState.value }.distinctUntilChanged().debounce(0.5.seconds)
                        .collect { config ->
                            tester.setSelectorSearchConfig(config)
                        }
                }

                launch {
                    snapshotFlow { sort }.distinctUntilChanged().debounce(0.5.seconds).collect { sort ->
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
}

/**
 * 交互式的 [SelectorMediaSourceEngine].
 */
internal class SelectorMediaSourceTester(
    private val engine: SelectorMediaSourceEngine,
    flowContext: CoroutineContext = Dispatchers.Default,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) {
    // must be data class
    data class SubjectQuery(
        val searchKeyword: String,
        val searchUrl: String?,
        val searchUseOnlyFirstWord: Boolean?,
        val searchRemoveSpecial: Boolean?,
    )

    data class EpisodeQuery(
        val sort: EpisodeSort,
    )

    private val scope = CoroutineScope(flowContext) // No ExceptionHandler! You must catch all exceptions in shareIn!

    val subjectSearchLifecycle = FlowRestarter()
    val subjectSearchRunning = FlowRunning()
    val episodeSearchLifecycle = FlowRestarter()
    val episodeSearchRunning = FlowRunning()

    /**
     * 将会影响两个筛选. 不会直接触发搜索. 如果变更导致 subject 的搜索结果变化, 可能会触发 episode list 搜索.
     */
    private val selectorSearchConfigFlow = MutableStateFlow<SelectorSearchConfig?>(null)
    private val subjectQueryFlow = MutableStateFlow<SubjectQuery?>(null)
    private val episodeQueryFlow = MutableStateFlow<EpisodeQuery?>(null)
    private val selectedSubjectIndexFlow = MutableStateFlow(0)

    /**
     * 用于查询条目列表, 每当编辑请求和 `searchUrl`, 会重新搜索, 但不会筛选.
     * 筛选在 [subjectSelectionResultFlow].
     */
    private val subjectSearchResultFlow = subjectQueryFlow
        .mapLatest { query ->
            if (query == null) {
                return@mapLatest null
            }

            subjectSearchRunning.withRunning {
                searchSubject(
                    query.searchUrl,
                    query.searchKeyword,
                    query.searchUseOnlyFirstWord,
                    query.searchRemoveSpecial,
                )
            }
        }
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 解析好的搜索结果.
     */
    val subjectSelectionResultFlow = combine(
        subjectSearchResultFlow,
        selectorSearchConfigFlow,
        subjectQueryFlow,
        episodeQueryFlow,
    ) { apiResponse, searchConfig, query, episodeQuery ->
        if (apiResponse == null) return@combine null
        if (searchConfig == null || query == null || episodeQuery == null) return@combine SelectorTestSearchSubjectResult.InvalidConfig

        selectSubjectResult(
            apiResponse, searchConfig,
            createSelectorSearchQuery(query, episodeQuery),
        )
    }.restartable(subjectSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 用户选择的条目.
     */
    private val selectedSubjectFlow = subjectSelectionResultFlow
        .combine(selectedSubjectIndexFlow) { result, index ->
            if (result == null) return@combine null

            (result as? SelectorTestSearchSubjectResult.Success)?.subjects?.getOrNull(index)
        } // not shared
        .distinctUntilChanged() // required, 否则在修改无关配置时也会触发重新搜索

    /**
     * 用于查询条目的剧集列表, 每当选择新的条目时, 会重新搜索. 但不会筛选. 筛选在 [episodeListSelectionResultFlow].
     */
    private val episodeListSearchResultFlow = selectedSubjectFlow
        .mapLatest { subject ->
            val subjectDetailsPageUrl = subject?.subjectDetailsPageUrl
            if (subjectDetailsPageUrl == null) {
                null
            } else {
                episodeSearchRunning.withRunning {
                    subjectDetailsPageUrl to searchEpisodes(subjectDetailsPageUrl)
                }
            }
        }.shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    /**
     * 解析好的剧集列表.
     */
    val episodeListSelectionResultFlow = combine(
        episodeListSearchResultFlow, subjectQueryFlow, selectorSearchConfigFlow, episodeQueryFlow,
    ) { episodeListDocumentResult, query, searchConfig, episodeQuery ->
        when {
            query == null || searchConfig == null || episodeQuery == null -> {
                SelectorTestEpisodeListResult.InvalidConfig
            }

            episodeListDocumentResult == null -> {
                SelectorTestEpisodeListResult.Success(null, emptyList())
            }

            else -> {
                val (subjectUrl, documentResult) = episodeListDocumentResult
                convertEpisodeResult(
                    documentResult, searchConfig,
                    createSelectorSearchQuery(query, episodeQuery),
                    subjectUrl,
                )
            }
        }
    }.restartable(episodeSearchLifecycle)
        .shareIn(scope, sharingStarted, replay = 1)
        .distinctUntilChanged()

    // region setters

    fun setSelectorSearchConfig(config: SelectorSearchConfig?) {
        selectorSearchConfigFlow.value = config
    }

    fun setSubjectQuery(query: SubjectQuery) {
        subjectQueryFlow.value = query
    }

    fun setEpisodeQuery(query: EpisodeQuery) {
        episodeQueryFlow.value = query
    }

    fun clearSubjectQuery() {
        subjectQueryFlow.value = null
    }

    fun setSubjectIndex(index: Int) {
        selectedSubjectIndexFlow.value = index
    }

    // endregion

    private fun createSelectorSearchQuery(
        query: SubjectQuery,
        episodeQuery: EpisodeQuery
    ) = SelectorSearchQuery(
        subjectName = query.searchKeyword,
        episodeSort = episodeQuery.sort,
        allSubjectNames = setOf(query.searchKeyword),
        episodeName = null,
        episodeEp = null,
    )

    private suspend fun searchEpisodes(subjectDetailsPageUrl: String): Result<Document?> {
        return try {
            Result.success(engine.searchEpisodes(subjectDetailsPageUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun searchSubject(
        url: String?,
        searchKeyword: String,
        useOnlyFirstWord: Boolean?,
        removeSpecial: Boolean?
    ) =
        if (url.isNullOrBlank() || searchKeyword.isBlank() || useOnlyFirstWord == null || removeSpecial == null) {
            null
        } else {
            try {
                val res = engine.searchSubjects(
                    searchUrl = url,
                    searchKeyword,
                    useOnlyFirstWord = useOnlyFirstWord,
                    removeSpecial = removeSpecial,
                )
                Result.success(res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }


    private fun convertEpisodeResult(
        res: Result<Document?>,
        config: SelectorSearchConfig,
        query: SelectorSearchQuery,
        subjectUrl: String,
    ): SelectorTestEpisodeListResult {
        return res.fold(
            onSuccess = { document ->
                try {
                    document ?: return SelectorTestEpisodeListResult.Success(null, emptyList())
                    val episodeList = engine.selectEpisodes(document, subjectUrl, config)
                        ?: return SelectorTestEpisodeListResult.InvalidConfig
                    SelectorTestEpisodeListResult.Success(
                        episodeList.channels,
                        episodeList.episodes
                            .fastDistinctBy { it.playUrl }
                            .map {
                                SelectorTestEpisodePresentation.compute(it, query, document, config)
                            },
                    )
                } catch (e: Throwable) {
                    SelectorTestEpisodeListResult.UnknownError(e)
                }
            },
            onFailure = { reason ->
                if (reason is RepositoryException) {
                    SelectorTestEpisodeListResult.ApiError(reason)
                } else {
                    SelectorTestEpisodeListResult.UnknownError(reason)
                }
            },
        )
    }

    private fun selectSubjectResult(
        res: Result<SelectorMediaSourceEngine.SearchSubjectResult>,
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): SelectorTestSearchSubjectResult {
        return res.fold(
            onSuccess = { data ->
                val document = data.document

                val originalList = if (document == null) {
                    emptyList()
                } else {
                    engine.selectSubjects(document, searchConfig).let { list ->
                        if (list == null) {
                            return SelectorTestSearchSubjectResult.InvalidConfig
                        }
                        list
                    }
                }

                SelectorTestSearchSubjectResult.Success(
                    data.url.toString(),
                    originalList.map {
                        SelectorTestSubjectPresentation.compute(
                            it,
                            query,
                            document,
                            searchConfig.filterBySubjectName,
                        )
                    },
                )
            },
            onFailure = { reason ->
                if (reason is RepositoryException) {
                    SelectorTestSearchSubjectResult.ApiError(reason)
                } else {
                    SelectorTestSearchSubjectResult.UnknownError(reason)
                }
            },
        )
    }
}
