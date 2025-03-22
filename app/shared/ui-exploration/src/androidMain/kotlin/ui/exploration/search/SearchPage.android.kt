/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.exploration.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults.Text
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.search.TestSearchState
import me.him188.ani.app.ui.search.collectItemsWithLifecycle
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@PreviewSizeClasses
@Preview
fun PreviewSearchPage() = ProvideCompositionLocalsForPreview {
    PreviewImpl()
}

@OptIn(TestOnly::class)
@Composable
@PreviewSizeClasses
@PreviewLightDark
fun PreviewSearchPageEmptyResult() = ProvideCompositionLocalsForPreview {
    PreviewImpl(
        createTestSearchPageState(
            rememberCoroutineScope(),
            TestSearchState(
                MutableStateFlow(MutableStateFlow(PagingData.from(emptyList()))),
            ),
        ),
    )
}

/**
 * @sample me.him188.ani.app.ui.search.PreviewLoadErrorCard
 */
@OptIn(TestOnly::class)
@Composable
@PreviewSizeClasses
@PreviewLightDark
fun PreviewSearchPageError() = ProvideCompositionLocalsForPreview {
    PreviewImpl(
        createTestSearchPageState(
            rememberCoroutineScope(),
            remember {
                TestSearchState(
                    MutableStateFlow(
                        MutableStateFlow(
                            PagingData.from(
                                emptyList(),
                                sourceLoadStates = LoadStates(
                                    LoadState.NotLoading(true),
                                    LoadState.NotLoading(true),
                                    LoadState.Error(RepositoryNetworkException()),
                                ),
                                mediatorLoadStates = LoadStates(
                                    LoadState.NotLoading(true),
                                    LoadState.NotLoading(true),
                                    LoadState.Error(RepositoryNetworkException()),
                                ),
                            ),
                        ),
                    ),
                )
            },
        ),
    )
}

@Composable
@PreviewLightDark
fun PreviewSearchPageResultColumn() = ProvideCompositionLocalsForPreview {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        val state = createTestFinishedSubjectSearchState()
        SearchPageResultColumn(
            items = state.collectItemsWithLifecycle(),
            showSummary = { true },
            selectedItemIndex = { 1 },
            onSelect = {},
            onPlay = {},
            searchBar = {},
        )
    }
}


@Composable
@OptIn(TestOnly::class)
private fun PreviewImpl(state: SearchPageState = createTestSearchPageState(rememberCoroutineScope())) {
    SideEffect {
        state.searchState.startSearch()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        SearchPage(
            state,
            detailContent = { Text("Hello, World!") },
        )
    }
}
