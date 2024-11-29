/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastDistinctBy
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.app.ui.settings.mediasource.BackgroundSearcher
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.xml.Document
import kotlin.coroutines.cancellation.CancellationException

@Stable
class SelectorTestState(
    searchConfigState: State<SelectorSearchConfig?>,
    private val engine: SelectorMediaSourceEngine,
    backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    // null for invalid config
    private val queryState = derivedStateOf {
        val searchKeyword = searchKeyword.ifEmpty { searchKeywordPlaceholder }
        val sort = sort
        if (searchKeyword.isBlank() || sort.isBlank()) {
            null
        } else {
            SelectorSearchQuery(
                subjectName = searchKeyword,
                episodeSort = EpisodeSort(sort),
                allSubjectNames = setOf(searchKeyword),
                episodeName = null,
                episodeEp = null,
            )
        }
    }

    var selectedSubjectIndex by mutableIntStateOf(0)
    val selectedSubjectState = derivedStateOf {
        val success = subjectSearchSelectResult as? SelectorTestSearchSubjectResult.Success
            ?: return@derivedStateOf null
        success.subjects.getOrNull(selectedSubjectIndex)
    }
    val selectedSubject by selectedSubjectState
    private val searchUrl by derivedStateOf {
        searchConfigState.value?.searchUrl
    }
    private val useOnlyFirstWord by derivedStateOf {
        searchConfigState.value?.searchUseOnlyFirstWord
    }

    data class MySubjectSearcherQuery(
        val searchUrl: String?,
        val searchKeyword: String,
        val searchUseOnlyFirstWord: Boolean?,
        val searchRemoveSpecial: Boolean?
    )

    /**
     * 用于查询条目列表, 每当编辑请求和 `searchUrl`, 会重新搜索, 但不会筛选.
     * 筛选在 [subjectSearchSelectResult].
     */
    val subjectSearcher = BackgroundSearcher(
        backgroundScope,
        derivedStateOf {
            val searchConfig = searchConfigState.value
            MySubjectSearcherQuery(
                searchConfig?.searchUrl,
                searchKeyword,
                searchConfig?.searchUseOnlyFirstWord,
                searchConfig?.searchRemoveSpecial,
            )
        },
        search = { (url, searchKeyword, useOnlyFirstWord, removeSpecial) ->
            // 不清除 selectedSubjectIndex

            launchRequestInBackground {
                if (url == null || url.isBlank() || searchKeyword.isBlank() || useOnlyFirstWord == null || removeSpecial == null) {
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
            }
        },
    )

    val subjectSearchSelectResult by derivedStateOf {
        val res = subjectSearcher.searchResult
        val config = searchConfigState.value
        val query = queryState.value
        when {
            res == null -> {
                null
            }

            config == null || query == null -> {
                SelectorTestSearchSubjectResult.InvalidConfig
            }

            else -> {
                res.fold(
                    onSuccess = {
                        selectSubjectResult(it, config, query)
                    },
                    onFailure = {
                        SelectorTestSearchSubjectResult.UnknownError(it)
                    },
                )
            }
        }
    }

    /**
     * 用于查询条目的剧集列表, 每当选择新的条目时, 会重新搜索. 但不会筛选. 筛选在 [episodeListSearchSelectResult].
     */
    val episodeListSearcher = BackgroundSearcher(
        backgroundScope,
        derivedStateOf { selectedSubject?.subjectDetailsPageUrl },
        search = { subjectDetailsPageUrl ->
            launchRequestInBackground {
                if (subjectDetailsPageUrl == null) {
                    null
                } else {
                    try {
                        Result.success(
                            engine.searchEpisodes(
                                subjectDetailsPageUrl,
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
            }
        },
    )

    /**
     * 经过筛选的条目的剧集列表
     */
    val episodeListSearchSelectResult by derivedStateOf {
        val subjectDetailsPageDocument = episodeListSearcher.searchResult
        val searchConfig = searchConfigState.value
        val queryState = queryState.value

        when {
            queryState == null || searchConfig == null -> {
                SelectorTestEpisodeListResult.InvalidConfig
            }

            subjectDetailsPageDocument == null -> {
                SelectorTestEpisodeListResult.Success(null, emptyList())
            }

            else -> {
                val subjectUrl = selectedSubject?.subjectDetailsPageUrl
                    ?: return@derivedStateOf SelectorTestEpisodeListResult.InvalidConfig

                subjectDetailsPageDocument.fold(
                    onSuccess = { document ->
                        convertEpisodeResult(document, searchConfig, queryState, subjectUrl)
                    },
                    onFailure = {
                        SelectorTestEpisodeListResult.UnknownError(it)
                    },
                )
            }
        }
    }

    var filterByChannel by mutableStateOf<String?>(null)
    val filteredEpisodes by derivedStateOf {
        when (val result = episodeListSearchSelectResult) {
            is SelectorTestEpisodeListResult.Success -> result.episodes.filter {
                filterByChannel == null || it.channel == filterByChannel
            }

            is SelectorTestEpisodeListResult.ApiError -> null
            SelectorTestEpisodeListResult.InvalidConfig -> null
            is SelectorTestEpisodeListResult.UnknownError -> null
        }
    }

    private fun convertEpisodeResult(
        res: ApiResponse<Document?>,
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
            onKnownFailure = { reason ->
                SelectorTestEpisodeListResult.ApiError(reason)
            },
        )
    }

    private fun selectSubjectResult(
        res: ApiResponse<SelectorMediaSourceEngine.SearchSubjectResult>,
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
    ): SelectorTestSearchSubjectResult {
        return res.fold(
            onSuccess = { data ->
                val document = data.document

                val originalList = if (document == null) {
                    emptyList()
                } else {
                    engine.selectSubjects(document, searchConfig).let {
                        if (it == null) {
                            return SelectorTestSearchSubjectResult.InvalidConfig
                        }
                        it
                    }
                }

                SelectorTestSearchSubjectResult.Success(
                    data.url.toString(),
                    originalList.map {
                        SelectorTestSubjectPresentation.compute(it, query, document, searchConfig.filterBySubjectName)
                    },
                )
            },
            onKnownFailure = { reason ->
                SelectorTestSearchSubjectResult.ApiError(reason)
            },
        )
    }
}