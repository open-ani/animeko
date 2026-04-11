/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.search.SearchState
import me.him188.ani.app.ui.search.TestSearchState
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
data class SearchPageState(
    val query: SubjectSearchQuery,
    val hasActiveSearch: Boolean,
    val removingHistory: String?,
    val searchFilterState: SearchFilterState,
    val selectedItemIndex: Int,
    val searchHistoryPager: Flow<PagingData<String>>,
    val searchState: SearchState<SubjectPreviewItemInfo>,
) {
    data class EpisodeTarget(
        val subjectId: Int,
        val episodeId: Int,
    )

    val hasSelectedItem: Boolean
        get() = selectedItemIndex != -1
}

sealed interface SearchPageIntent {
    data class UpdateQuery(
        val query: SubjectSearchQuery,
        val submit: Boolean = false,
    ) : SearchPageIntent

    data object StartInitialSearch : SearchPageIntent
    data class RemoveHistory(val text: String) : SearchPageIntent
    data class ChangeSort(val sort: SearchSort) : SearchPageIntent
    data class SelectResult(
        val index: Int,
        val item: SubjectPreviewItemInfo,
    ) : SearchPageIntent

    data class OpenSubjectDetails(
        val index: Int,
        val item: SubjectPreviewItemInfo,
    ) : SearchPageIntent

    data object ClearSelection : SearchPageIntent
    data class Play(val item: SubjectPreviewItemInfo) : SearchPageIntent
}

sealed interface SearchPageEffect {
    data class NavigateToEpisodeDetails(
        val subjectId: Int,
        val episodeId: Int,
    ) : SearchPageEffect

    data class NavigateToSubjectDetails(
        val subjectId: Int,
        val title: String,
        val imageUrl: String,
    ) : SearchPageEffect
}

fun SearchPageState.withQuery(
    query: SubjectSearchQuery,
    tagKinds: List<CanonicalTagKind> = SearchFilterState.DEFAULT_TAG_KINDS,
): SearchPageState {
    val normalizedQuery = query.normalized()
    if (normalizedQuery == this.query) {
        return this
    }

    return copy(
        query = normalizedQuery,
        searchFilterState = buildSearchFilterState(normalizedQuery.tags.orEmpty(), tagKinds),
    )
}

fun SearchPageState.toggleTagSelection(
    tag: SearchFilterChipState,
    value: String,
    unselectOthersOfSameKind: Boolean,
    tagKinds: List<CanonicalTagKind> = SearchFilterState.DEFAULT_TAG_KINDS,
): SearchPageState {
    val existingTags = query.tags.orEmpty()
    val updatedTags = if (value in existingTags) {
        existingTags - value
    } else if (unselectOthersOfSameKind) {
        existingTags.filterNot { it in tag.values } + value
    } else {
        existingTags + value
    }

    return withQuery(
        query.copy(tags = updatedTags),
        tagKinds = tagKinds,
    )
}

fun buildSearchFilterState(
    selectedTags: List<String>,
    tagKinds: List<CanonicalTagKind> = SearchFilterState.DEFAULT_TAG_KINDS,
): SearchFilterState {
    val groups = selectedTags.groupBy { tag ->
        tagKinds.find { kind -> tag in kind.values }
    }

    val chips = buildList(capacity = tagKinds.size + 1) {
        for (kind in tagKinds) {
            add(
                SearchFilterChipState(
                    kind = kind,
                    values = kind.values,
                    selected = groups[kind].orEmpty(),
                ),
            )
        }

        groups[null]?.let { customValues ->
            add(
                SearchFilterChipState(
                    kind = null,
                    values = customValues,
                    selected = customValues,
                ),
            )
        }
    }

    return SearchFilterState(chips = chips)
}

@TestOnly
fun createTestSearchPageState(
    searchState: SearchState<SubjectPreviewItemInfo> = TestSearchState(
        MutableStateFlow(flowOf(PagingData.from(TestSubjectPreviewItemInfos))),
    ),
    query: SubjectSearchQuery = SubjectSearchQuery("test"),
    hasActiveSearch: Boolean = true,
): SearchPageState {
    val normalizedQuery = query.normalized()
    return SearchPageState(
        query = normalizedQuery,
        hasActiveSearch = hasActiveSearch,
        removingHistory = null,
        searchFilterState = buildSearchFilterState(normalizedQuery.tags.orEmpty()),
        selectedItemIndex = -1,
        searchHistoryPager = flowOf(PagingData.from(listOf("test history"))),
        searchState = searchState,
    )
}

@TestOnly
fun createTestInteractiveSubjectSearchState(scope: CoroutineScope): SearchState<SubjectPreviewItemInfo> {
    return PagingSearchState(
        createPager = {
            MutableStateFlow(PagingData.from(TestSubjectPreviewItemInfos))
        },
        backgroundScope = scope,
    )
}

@TestOnly
fun createTestFinishedSubjectSearchState(): SearchState<SubjectPreviewItemInfo> {
    return TestSearchState(
        MutableStateFlow(flowOf(PagingData.from(TestSubjectPreviewItemInfos))),
    )
}
