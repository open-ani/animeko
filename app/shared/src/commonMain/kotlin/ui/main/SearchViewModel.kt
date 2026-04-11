/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.subject.BangumiSubjectSearchCompletionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.exploration.search.SearchPageEffect
import me.him188.ani.app.ui.exploration.search.SearchPageIntent
import me.him188.ani.app.ui.exploration.search.SearchPageState
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.exploration.search.buildSearchFilterState
import me.him188.ani.app.ui.exploration.search.withQuery
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SearchStart
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SearchViewModel(
    initialSearchQuery: SubjectSearchQuery,
) : AbstractViewModel(), KoinComponent {
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()
    private val bangumiSubjectSearchCompletionRepository: BangumiSubjectSearchCompletionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val settingsRepository: SettingsRepository by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()

    private val initialQuery = initialSearchQuery.normalized()
    private val hasInitialSearchQuery = initialQuery.shouldTriggerSearch()
    private val queryFlow = MutableStateFlow(initialQuery)

    private val nsfwSettingFlow = settingsRepository.uiSettings.flow
        .map { it.searchSettings.nsfwMode }
        .stateIn(backgroundScope, SharingStarted.Lazily, NsfwMode.HIDE)

    private val searchHistoryPager = searchHistoryRepository.getHistoryPager().cachedIn(backgroundScope)
    private val searchState = PagingSearchState(
        createPager = { scope ->
            val rawQuery = queryFlow.value.normalized()
            val explicitR18 = rawQuery.tags?.contains("R18") == true
            val query = rawQuery.copy(
                nsfw = when {
                    explicitR18 -> true
                    nsfwSettingFlow.value == NsfwMode.HIDE -> false
                    else -> null
                },
            )

            subjectSearchRepository.searchSubjects(
                searchQuery = query,
                useNewApi = {
                    query.hasFilters() ||
                            settingsRepository.uiSettings.flow.map { it.searchSettings.enableNewSearchSubjectApi }
                                .first()
                },
                ignoreDoneAndDropped = {
                    settingsRepository.uiSettings.flow.map {
                        it.searchSettings.ignoreDoneAndDroppedSubjects
                    }.first()
                },
            ).combine(nsfwSettingFlow) { data, nsfwMode ->
                data.map { subject ->
                    SubjectPreviewItemInfo.compute(
                        subject.subjectInfo,
                        subject.mainEpisodeCount,
                        nsfwModeSettings = if (explicitR18) {
                            NsfwMode.DISPLAY
                        } else {
                            nsfwMode
                        },
                        relatedPersonList = subject.lightSubjectRelations.lightRelatedPersonInfoList,
                        characters = subject.lightSubjectRelations.lightRelatedCharacterInfoList,
                    )
                }
            }.cachedIn(scope)
        },
        backgroundScope = backgroundScope,
    )

    private val _searchPageState = MutableStateFlow(
        SearchPageState(
            query = initialQuery,
            hasActiveSearch = false,
            removingHistory = null,
            searchFilterState = buildSearchFilterState(initialQuery.tags.orEmpty()),
            selectedItemIndex = -1,
            searchHistoryPager = searchHistoryPager,
            searchState = searchState,
        ),
    )
    val searchPageState = _searchPageState.asStateFlow()

    private val _searchPageEffects = MutableSharedFlow<SearchPageEffect>(extraBufferCapacity = 1)
    val searchPageEffects = _searchPageEffects.asSharedFlow()

    val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow
    val subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)

    private var currentPreviewingSubject: SubjectInfo? = null
    private var initialSearchQueryStarted = false

    fun suggestionsPager(query: String): Flow<PagingData<String>> {
        return bangumiSubjectSearchCompletionRepository.completionsFlow(query.trim())
    }

    fun onSearchPageIntent(intent: SearchPageIntent) {
        when (intent) {
            SearchPageIntent.ClearSelection -> {
                updateSearchPageState {
                    if (it.selectedItemIndex == -1) {
                        it
                    } else {
                        it.copy(selectedItemIndex = -1)
                    }
                }
            }

            is SearchPageIntent.ChangeSort -> {
                refreshSearch(_searchPageState.value.query.copy(sort = intent.sort))
            }

            is SearchPageIntent.Play -> {
                launchInBackground {
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(intent.item.subjectId)
                        .first()
                        .firstOrNull()
                        ?.let {
                            _searchPageEffects.emit(
                                SearchPageEffect.NavigateToEpisodeDetails(
                                    subjectId = intent.item.subjectId,
                                    episodeId = it.episodeInfo.episodeId,
                                ),
                            )
                        }
                }
            }

            is SearchPageIntent.RemoveHistory -> {
                updateSearchPageState { it.copy(removingHistory = intent.text) }
                launchInBackground {
                    searchHistoryRepository.removeHistory(intent.text)
                    updateSearchPageState { state ->
                        if (state.removingHistory == intent.text) {
                            state.copy(removingHistory = null)
                        } else {
                            state
                        }
                    }
                }
            }

            is SearchPageIntent.SelectResult -> {
                updateSearchPageState { state ->
                    state.copy(
                        selectedItemIndex = intent.index,
                    )
                }
                Analytics.recordEvent(SubjectEnter) {
                    put("source", "search")
                    put("subject_id", intent.item.subjectId)
                    put("position", intent.index)
                }
                viewSubjectDetails(intent.item)
            }

            is SearchPageIntent.OpenSubjectDetails -> {
                Analytics.recordEvent(SubjectEnter) {
                    put("source", "search")
                    put("subject_id", intent.item.subjectId)
                    put("position", intent.index)
                }
                launchInBackground {
                    _searchPageEffects.emit(
                        SearchPageEffect.NavigateToSubjectDetails(
                            subjectId = intent.item.subjectId,
                            title = intent.item.title,
                            imageUrl = intent.item.imageUrl,
                        ),
                    )
                }
            }

            SearchPageIntent.StartInitialSearch -> {
                if (!initialSearchQueryStarted) {
                    initialSearchQueryStarted = true
                    if (hasInitialSearchQuery) {
                        refreshSearch(_searchPageState.value.query)
                    }
                }
            }

            is SearchPageIntent.UpdateQuery -> {
                updateQuery(intent.query, intent.submit)
            }
        }
    }

    fun reloadCurrentSubjectDetails() {
        val curr = currentPreviewingSubject ?: return
        subjectDetailsStateLoader.reload(curr.subjectId, curr)
    }

    private fun updateQuery(query: SubjectSearchQuery, submit: Boolean) {
        val updatedQuery = query.normalized()
        updateQueryState(updatedQuery)
        if (updatedQuery.shouldTriggerSearch()) {
            if (submit) {
                Analytics.recordEvent(SearchStart) {
                    put("query", updatedQuery.keywords)
                    put("query_length", updatedQuery.keywords.length)
                    put("has_query", updatedQuery.keywords.isNotEmpty())
                    put("tags", updatedQuery.tags.orEmpty().joinToString(","))
                    put("tag_count", updatedQuery.tags.orEmpty().size)
                }
            }
            refreshSearch(updatedQuery)
            if (submit && updatedQuery.keywords.isNotEmpty()) {
                launchInBackground {
                    searchHistoryRepository.addHistory(updatedQuery.keywords)
                }
            }
        }
    }

    private fun refreshSearch(query: SubjectSearchQuery) {
        val normalizedQuery = query.normalized()
        updateQueryState(normalizedQuery)

        if (normalizedQuery.shouldTriggerSearch()) {
            clearSubjectDetails()
            searchState.startSearch()
            updateSearchPageState {
                it.copy(
                    hasActiveSearch = true,
                    selectedItemIndex = -1,
                )
            }
        }
    }

    private fun clearSearchResults() {
        clearSubjectDetails()
        searchState.clear()
    }

    private fun clearSubjectDetails() {
        currentPreviewingSubject = null
        subjectDetailsStateLoader.clear()
    }

    private fun viewSubjectDetails(previewItem: SubjectPreviewItemInfo) {
        subjectDetailsStateLoader.clear()
        subjectDetailsStateLoader.load(
            previewItem.subjectId,
            placeholder = SubjectInfo.createPlaceholder(
                previewItem.subjectId,
                previewItem.title,
                previewItem.imageUrl,
                previewItem.title,
            ).also { currentPreviewingSubject = it },
        )
    }

    private fun updateQueryState(query: SubjectSearchQuery) {
        val normalizedQuery = query.normalized()
        if (queryFlow.value == normalizedQuery && _searchPageState.value.query == normalizedQuery) {
            return
        }

        queryFlow.value = normalizedQuery
        updateSearchPageState { it.withQuery(normalizedQuery) }
    }

    private inline fun updateSearchPageState(block: (SearchPageState) -> SearchPageState) {
        _searchPageState.update(block)
    }
}

private fun SubjectSearchQuery.shouldTriggerSearch(): Boolean {
    return keywords.isNotEmpty() ||
            !tags.isNullOrEmpty() ||
            season != null ||
            rating != null ||
            nsfw != null
}
