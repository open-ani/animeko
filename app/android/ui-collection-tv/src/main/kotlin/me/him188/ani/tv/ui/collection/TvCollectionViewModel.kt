/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.collection

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.subject.collection.COLLECTION_TABS_SORTED
import me.him188.ani.app.ui.subject.collection.UserCollectionsViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

class TvCollectionViewModel(koin: Koin) : UserCollectionsViewModel(koin) {
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    val uiState = snapshotFlow { state.selectedTypeIndex to state.collectionCounts }
        .map { (index, _) -> presentation(index) }
        // The shared Compose state is created inside composition. Wait for the Route's
        // post-composition subscription before reading its snapshot on a background thread.
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), presentation(state.selectedTypeIndex))

    private fun presentation(index: Int) = TvCollectionUiState(
        selectedTabIndex = index,
        counts = state.collectionCounts,
        items = state.getCollectionLazyPagingItems(index),
        hasPreviousTab = index > 0,
        hasNextTab = index < COLLECTION_TABS_SORTED.lastIndex,
    )

    fun onIntent(intent: TvCollectionIntent) {
        when (intent) {
            is TvCollectionIntent.SelectTab -> selectTab(intent.index)
            is TvCollectionIntent.SwitchTab -> selectTab(state.selectedTypeIndex + intent.direction)
            is TvCollectionIntent.OpenSubject -> {
                val info = intent.subject.subjectInfo
                navigation.emit(
                    TvNavigationEvent.Subject(
                        info.subjectId,
                        SubjectDetailPlaceholder(
                            id = info.subjectId,
                            name = info.name,
                            coverUrl = info.imageLarge,
                            nameCN = info.nameCn
                        ),
                    )
                )
            }
        }
    }

    private fun selectTab(index: Int) {
        if (index in COLLECTION_TABS_SORTED.indices && index != state.selectedTypeIndex) {
            state.selectTypeIndex(index)
        }
    }
}
