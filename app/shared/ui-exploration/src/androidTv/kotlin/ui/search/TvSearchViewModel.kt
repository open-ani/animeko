/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.search

import androidx.compose.runtime.Stable
import androidx.paging.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.exploration.search.SearchPageIntent
import me.him188.ani.app.ui.main.SearchViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

/** TV 搜索表单将已提交的关键词接入共享搜索状态。 */
@Stable
class TvSearchViewModel(koin: Koin) : SearchViewModel(SubjectSearchQuery(keywords = ""), koin) {
    private val keywords = MutableStateFlow("")
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    val uiState = combine(keywords, searchPageState) { keywords, search ->
        TvSearchUiState(keywords = keywords, hasSearched = search.hasActiveSearch)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvSearchUiState())

    val results = searchPageState.value.searchState.pagerFlow.flatMapLatest { pager ->
        pager ?: emptyFlow()
    }.map { page -> page.filter { !it.hide && it.nsfwMode != NsfwMode.HIDE } }

    fun onIntent(intent: TvSearchIntent) {
        when (intent) {
            is TvSearchIntent.ChangeKeywords -> keywords.value = intent.value
            TvSearchIntent.Search -> onSearchPageIntent(
                SearchPageIntent.UpdateQuery(SubjectSearchQuery(keywords = keywords.value), submit = true),
            )
            is TvSearchIntent.OpenSubject -> {
                val item = intent.subject
                navigation.emit(TvNavigationEvent.Subject(
                    item.subjectId,
                    SubjectDetailPlaceholder(item.subjectId, item.originalTitle, item.title, item.imageUrl),
                ))
            }
        }
    }
}
