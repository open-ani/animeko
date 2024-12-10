/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.NSFWMode
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.search.SearchState
import me.him188.ani.app.ui.search.TestSearchState
import me.him188.ani.app.ui.search.launchAsItemsIn
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class SearchPageState(
    searchHistoryState: State<List<String>>,
    suggestionsState: State<List<String>>,
    val onRequestPlay: suspend (SubjectPreviewItemInfo) -> EpisodeTarget?,
    val searchState: SearchState<SubjectPreviewItemInfo>,
    val nsfwModeState: State<NSFWMode>,
    backgroundScope: CoroutineScope,
    queryState: MutableState<String> = mutableStateOf(""),
    private val onStartSearch: (String) -> Unit = {},
) {
    // to navigate to episode page
    data class EpisodeTarget(
        val subjectId: Int,
        val episodeId: Int,
    )

    val suggestionSearchBarState = SuggestionSearchBarState(
        historyState = searchHistoryState,
        suggestionsState = suggestionsState,
        searchState = searchState,
        queryState = queryState,
        onStartSearch = { query ->
            onStartSearch(query)
        },
    )
    val gridState = LazyStaggeredGridState()

    val items = searchState.launchAsItemsIn(backgroundScope)

    var selectedItemIndex: Int by mutableIntStateOf(0)

    val playTasker = MonoTasker(backgroundScope)
    var playingItem: SubjectPreviewItemInfo? by mutableStateOf(null)
        private set

    suspend fun requestPlay(info: SubjectPreviewItemInfo): EpisodeTarget? {
        playingItem = info
        return playTasker.async {
            onRequestPlay(info)
        }.await()
    }
}

@TestOnly
fun createTestSearchPageState(
    backgroundScope: CoroutineScope,
    searchState: SearchState<SubjectPreviewItemInfo> = TestSearchState(
        MutableStateFlow(MutableStateFlow(PagingData.from(TestSubjectPreviewItemInfos))),
    )
): SearchPageState {
    val results = mutableStateOf<List<SubjectPreviewItemInfo>>(emptyList())
    return SearchPageState(
        searchHistoryState = mutableStateOf(emptyList()),
        suggestionsState = mutableStateOf(emptyList()),
        onRequestPlay = {
            delay(3000)
            SearchPageState.EpisodeTarget(1, 2)
        },
        queryState = mutableStateOf(""),
        searchState = searchState,
        backgroundScope = backgroundScope,
        nsfwModeState = stateOf(NSFWMode.DISPLAY),
    )
}

@TestOnly
fun createTestInteractiveSubjectSearchState(scope: CoroutineScope): SearchState<SubjectPreviewItemInfo> {
    return PagingSearchState {
        MutableStateFlow(PagingData.from(TestSubjectPreviewItemInfos))
    }
}

@TestOnly
fun createTestFinishedSubjectSearchState(): SearchState<SubjectPreviewItemInfo> {
    return TestSearchState(
        MutableStateFlow(MutableStateFlow(PagingData.from(TestSubjectPreviewItemInfos))),
    )
}
