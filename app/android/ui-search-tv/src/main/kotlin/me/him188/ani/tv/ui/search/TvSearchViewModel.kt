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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 搜索页薄 VM (atv-architecture.md §7.3, M2 精简版: 关键词搜索, 筛选弹窗 M3).
 */
@Stable
class TvSearchViewModel : AbstractViewModel(), KoinComponent {
    private val subjectSearchRepository: SubjectSearchRepository by inject()

    private val _keywords = MutableStateFlow("")
    val keywords: StateFlow<String> = _keywords.asStateFlow()

    /** 已提交的搜索 (软键盘 Search 动作触发, 非边输边搜) */
    private val submittedQuery = MutableStateFlow<SubjectSearchQuery?>(null)

    val results: Flow<PagingData<BatchSubjectDetails>> = submittedQuery
        .flatMapLatest { query ->
            if (query == null) {
                emptyFlow()
            } else {
                subjectSearchRepository.searchSubjects(query)
            }
        }
        .cachedIn(backgroundScope)

    val hasSearched: StateFlow<SubjectSearchQuery?> = submittedQuery.asStateFlow()

    fun setKeywords(value: String) {
        _keywords.value = value
    }

    fun search() {
        val query = SubjectSearchQuery(keywords = _keywords.value).normalized()
        if (query.hasSearchRequest()) {
            submittedQuery.value = query
        }
    }
}
