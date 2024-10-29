/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.SubjectSearchRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.exploration.search.SearchPageState
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SearchViewModel : AbstractViewModel(), KoinComponent {
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()

    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()

    private val queryState = mutableStateOf("")

    val searchPageState: SearchPageState = SearchPageState(
        searchHistoryState = searchHistoryRepository.getHistoryFlow().produceState(emptyList()),
        suggestionsState = searchHistoryRepository.getHistoryFlow()
            .produceState(emptyList()),// todo: suggestions
        onRequestPlay = { info ->
            episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId).first().firstOrNull()?.let {
                SearchPageState.EpisodeTarget(info.subjectId, it.episodeInfo.episodeId)
            }
        },
        queryState = queryState,
        searchState = PagingSearchState(
            createPager = {
                subjectSearchRepository.searchSubjects(SubjectSearchQuery(keyword = queryState.value)).map { data ->
                    data.map {
                        SubjectPreviewItemInfo.compute(
                            it,
                            null, null, // TODO: search results
                        )
                    }
                }
            },
        ),
        backgroundScope,
    )

    fun viewSubjectDetails(
        subjectId: Int
    ) {
        subjectDetailsViewModel?.cancelScope()
        subjectDetailsViewModel = null
        subjectDetailsViewModel = SubjectDetailsViewModel(subjectId)
    }

    var subjectDetailsViewModel: SubjectDetailsViewModel? by mutableStateOf(null)
        private set

    override fun onCleared() {
        super.onCleared()
        subjectDetailsViewModel?.cancelScope()
        subjectDetailsViewModel = null
    }
}
