/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.search

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.repository.SubjectInfo
import me.him188.ani.app.data.repository.SubjectSearchRepository
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import kotlin.coroutines.CoroutineContext

interface SubjectSearcher {
    /**
     * 唯一 ID, 每次调用 [search] 时增加
     */
    val searchId: StateFlow<Int>

    val result: Flow<PagingData<SubjectInfo>>

    fun search(query: SubjectSearchQuery)
}

class SubjectSearcherImpl(
    private val subjectSearchRepository: SubjectSearchRepository,
    parentCoroutineContext: CoroutineContext,
) : SubjectSearcher, HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private val currentQuery: MutableStateFlow<SubjectSearchQuery?> = MutableStateFlow(null)

    override val result: Flow<PagingData<SubjectInfo>> = currentQuery
        .flatMapLatest { query ->
            if (query == null) {
                return@flatMapLatest emptyFlow()
            }
            subjectSearchRepository.searchSubjects(
                query,
                useNewApi = false,
            )
        }

    override val searchId: MutableStateFlow<Int> = MutableStateFlow(0)

    override fun search(query: SubjectSearchQuery) {
        searchId.update { it + 1 }
        currentQuery.value = query
    }
}