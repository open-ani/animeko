/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.collection

import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo

data class TvCollectionUiState(
    val selectedTabIndex: Int,
    val counts: SubjectCollectionCounts?,
    val items: LazyPagingItems<SubjectCollectionInfo>,
    val hasPreviousTab: Boolean,
    val hasNextTab: Boolean,
)

sealed interface TvCollectionIntent {
    data class SelectTab(val index: Int) : TvCollectionIntent
    data class SwitchTab(val direction: Int) : TvCollectionIntent
    data class OpenSubject(val subject: SubjectCollectionInfo) : TvCollectionIntent
}
