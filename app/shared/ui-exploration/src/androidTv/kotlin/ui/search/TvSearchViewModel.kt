/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.search

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.foundation.TvNavigationEvents

/**
 * TV 搜索页薄 VM (atv-architecture.md §7.3, M2 精简版: 关键词搜索, 筛选弹窗 M3).
 */
@Stable
class TvSearchViewModel(
    private val subjectSearchRepository: SubjectSearchRepository,
    settingsRepository: SettingsRepository,
) : AbstractViewModel() {
    private val _uiState = MutableStateFlow(TvSearchUiState())
    private val searchSettings = settingsRepository.uiSettings.flow.map { it.searchSettings }.distinctUntilChanged()
    val uiState = combine(_uiState, searchSettings) { state, settings -> state.copy(nsfwMode = settings.nsfwMode) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvSearchUiState())
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    /** 已提交的搜索 (软键盘 Search 动作触发, 非边输边搜) */
    private val submittedQuery = MutableStateFlow<SubjectSearchQuery?>(null)

    val results: Flow<PagingData<BatchSubjectDetails>> = combine(submittedQuery, searchSettings) { query, settings -> query to settings }
        .flatMapLatest { (query, settings) ->
            if (query == null) {
                emptyFlow()
            } else {
                subjectSearchRepository.searchSubjects(
                    if (settings.nsfwMode == NsfwMode.HIDE) query.copy(nsfw = false) else query,
                    ignoreDoneAndDropped = { settings.ignoreDoneAndDroppedSubjects },
                ).map { page -> page.filter { settings.nsfwMode != NsfwMode.HIDE || !it.subjectInfo.nsfw } }
            }
        }
        .cachedIn(backgroundScope)

    fun onIntent(intent: TvSearchIntent) {
        when (intent) {
            is TvSearchIntent.ChangeKeywords -> _uiState.update { it.copy(keywords = intent.value) }
            TvSearchIntent.Search -> {
                val query = SubjectSearchQuery(keywords = _uiState.value.keywords).normalized()
                if (query.hasSearchRequest()) {
                    submittedQuery.value = query
                    _uiState.update { it.copy(hasSearched = true) }
                }
            }
            is TvSearchIntent.OpenSubject -> {
                val info = intent.subject.subjectInfo
                navigation.emit(TvNavigationEvent.Subject(
                    info.subjectId,
                    SubjectDetailPlaceholder(id = info.subjectId, name = info.name, coverUrl = info.imageLarge, nameCN = info.nameCn),
                ))
            }
        }
    }
}
