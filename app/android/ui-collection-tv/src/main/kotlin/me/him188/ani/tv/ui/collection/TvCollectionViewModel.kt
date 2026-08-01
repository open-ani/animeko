/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.collection

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 追番页薄 VM (atv-architecture.md §7.4): 五分类 tab + 分页网格.
 */
@Stable
class TvCollectionViewModel : AbstractViewModel(), KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()

    /** 五 tab: 在看/想看/搁置/看过/抛弃 (在看放首位, 使用频率最高) */
    val tabs: List<UnifiedCollectionType> = listOf(
        UnifiedCollectionType.DOING,
        UnifiedCollectionType.WISH,
        UnifiedCollectionType.ON_HOLD,
        UnifiedCollectionType.DONE,
        UnifiedCollectionType.DROPPED,
    )

    private val selectedTab = MutableStateFlow(UnifiedCollectionType.DOING)

    val counts: StateFlow<SubjectCollectionCounts?> = subjectCollectionRepository
        .subjectCollectionCountsFlow()
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    val pager: Flow<PagingData<SubjectCollectionInfo>> = selectedTab
        .flatMapLatest { type ->
            subjectCollectionRepository.subjectCollectionsPager(CollectionsFilterQuery(type))
        }
        .cachedIn(backgroundScope)

    fun selectTab(type: UnifiedCollectionType) {
        selectedTab.value = type
    }
}
