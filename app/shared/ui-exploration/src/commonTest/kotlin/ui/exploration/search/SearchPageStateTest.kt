/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.search.SearchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPageStateTest {
    @Test
    fun `suggestion search trims query before starting search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var stateScope: CoroutineScope? = null
        try {
            val queryFlow = MutableStateFlow(SubjectSearchQuery(""))
            val searchState = RecordingSearchState<SubjectPreviewItemInfo>()
            val startedQueries = mutableListOf<String>()
            stateScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val state = createState(stateScope, queryFlow, searchState) {
                startedQueries += it
            }
            val collectJob = launch {
                state.suggestionSearchBarState.presentationFlow.collect {}
            }

            state.suggestionSearchBarState.setQuery("  bocchi  ")
            advanceUntilIdle()

            state.suggestionSearchBarState.startSearch()

            assertEquals("bocchi", queryFlow.value.keywords)
            assertEquals("bocchi", state.suggestionSearchBarState.editingQuery)
            assertEquals(1, searchState.startSearchCount)
            assertEquals(0, searchState.clearCount)
            assertEquals(listOf("bocchi"), startedQueries)

            collectJob.cancel()
        } finally {
            stateScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `suggestion search clears instead of starting for whitespace only query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var stateScope: CoroutineScope? = null
        try {
            val queryFlow = MutableStateFlow(SubjectSearchQuery(""))
            val searchState = RecordingSearchState<SubjectPreviewItemInfo>()
            val startedQueries = mutableListOf<String>()
            stateScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val state = createState(stateScope, queryFlow, searchState) {
                startedQueries += it
            }
            val collectJob = launch {
                state.suggestionSearchBarState.presentationFlow.collect {}
            }

            state.suggestionSearchBarState.setQuery("   ")
            advanceUntilIdle()

            state.suggestionSearchBarState.startSearch()

            assertEquals("", queryFlow.value.keywords)
            assertEquals("", state.suggestionSearchBarState.editingQuery)
            assertEquals(0, searchState.startSearchCount)
            assertEquals(1, searchState.clearCount)
            assertTrue(startedQueries.isEmpty())

            collectJob.cancel()
        } finally {
            stateScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `updateQuery keeps filter only searches and trims whitespace keywords`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var stateScope: CoroutineScope? = null
        try {
            val queryFlow = MutableStateFlow(SubjectSearchQuery("   "))
            val searchState = RecordingSearchState<SubjectPreviewItemInfo>()
            stateScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val state = createState(stateScope, queryFlow, searchState)

            state.updateQuery {
                copy(tags = listOf("百合"))
            }

            assertEquals("", queryFlow.value.keywords)
            assertEquals(listOf("百合"), queryFlow.value.tags)
            assertEquals(1, searchState.startSearchCount)
            assertEquals(0, searchState.clearCount)
        } finally {
            stateScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun createState(
        backgroundScope: CoroutineScope,
        queryFlow: MutableStateFlow<SubjectSearchQuery>,
        searchState: SearchState<SubjectPreviewItemInfo>,
        onStartSearch: (String) -> Unit = {},
    ): SearchPageState {
        return SearchPageState(
            searchHistoryPager = flowOf(PagingData.empty()),
            suggestionsPager = flowOf(PagingData.empty()),
            queryFlow = queryFlow,
            setQuery = { newQuery ->
                queryFlow.update { newQuery }
            },
            onRequestPlay = { null },
            searchState = searchState,
            onRemoveHistory = {},
            backgroundScope = backgroundScope,
            onStartSearch = onStartSearch,
        )
    }

    private class RecordingSearchState<T : Any> : SearchState<T>() {
        override val pagerFlow: MutableStateFlow<Flow<PagingData<T>>?> = MutableStateFlow(null)

        var startSearchCount: Int = 0
            private set
        var clearCount: Int = 0
            private set

        override fun startSearch() {
            startSearchCount += 1
        }

        override fun clear() {
            clearCount += 1
        }
    }
}
