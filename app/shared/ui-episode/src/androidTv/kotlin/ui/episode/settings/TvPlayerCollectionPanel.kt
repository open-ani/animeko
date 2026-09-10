/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.TvEpisodeIntent
import me.him188.ani.leanback.ui.episode.components.TvPlayerOptionPanelLayout
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.subject.collection.TvCollectionPrompt
import me.him188.ani.leanback.ui.subject.collection.tvCollectionOptions

@Composable
internal fun TvPlayerCollectionPanel(
    collectionType: UnifiedCollectionType,
    busy: Boolean,
    collectionPrompt: TvCollectionPrompt?,
    onCollectionPromptChange: (TvCollectionPrompt?) -> Unit,
    onIntent: (TvEpisodeIntent) -> Boolean,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val options = tvCollectionOptions(
        collectionType, busy, collectionPrompt, onCollectionPromptChange,
        onSetCollection = { onIntent(TvEpisodeIntent.SetCollection(it)) },
        onMarkAllWatched = { onIntent(TvEpisodeIntent.MarkAllWatched()) },
    )
    TvPlayerOptionPanelLayout(TvPlayerPanel.Collection, listState, modifier) {
        items(options, key = { it.key }) { option ->
            option.content((if (option.isEntry) entryModifier else Modifier)
                .testTag("tv-player-collection-${option.key}"))
        }
    }
}
