/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_collection_change_only
import me.him188.ani.app.ui.lang.subject_collection_collect
import me.him188.ani.app.ui.lang.subject_collection_delete
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_keep
import me.him188.ani.app.ui.lang.subject_collection_mark_all_watched_action
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_remove_confirm_short
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.TvEpisodeIntent
import me.him188.ani.leanback.ui.episode.components.TvPlayerOptionPanelLayout
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UnifiedCollectionType.tvLabel(): String = stringResource(when (this) {
    UnifiedCollectionType.WISH -> Lang.subject_collection_wish
    UnifiedCollectionType.DOING -> Lang.subject_collection_doing
    UnifiedCollectionType.DONE -> Lang.subject_collection_done
    UnifiedCollectionType.ON_HOLD -> Lang.subject_collection_on_hold
    UnifiedCollectionType.DROPPED -> Lang.subject_collection_dropped
    UnifiedCollectionType.NOT_COLLECTED -> Lang.subject_collection_collect
})

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
    TvPlayerOptionPanelLayout(TvPlayerPanel.Collection, listState, modifier) {
        when {
            collectionPrompt == TvCollectionPrompt.Remove -> {
                item { Text(stringResource(Lang.subject_collection_remove_confirm_short), color = Color.White, modifier = Modifier.padding(10.dp)) }
                item {
                    TvOptionRow(
                        stringResource(Lang.subject_collection_delete),
                        modifier = entryModifier,
                    ) { onIntent(TvEpisodeIntent.SetCollection(UnifiedCollectionType.NOT_COLLECTED)) }
                }
                item { TvOptionRow(stringResource(Lang.subject_collection_keep)) { onCollectionPromptChange(null) } }
            }

            collectionPrompt == TvCollectionPrompt.MarkAllWatched -> {
                item {
                    TvOptionRow(
                        stringResource(Lang.subject_collection_mark_all_watched_action),
                        modifier = entryModifier,
                    ) { onIntent(TvEpisodeIntent.MarkAllWatched()) }
                }
                item { TvOptionRow(stringResource(Lang.subject_collection_change_only)) { onCollectionPromptChange(null) } }
            }

            else -> items(UnifiedCollectionType.entries) { type ->
                TvOptionRow(
                    if (type == UnifiedCollectionType.NOT_COLLECTED) stringResource(Lang.subject_collection_delete) else type.tvLabel(),
                    enabled = !busy,
                    compact = true,
                    modifier = if (type == collectionType) entryModifier else Modifier,
                    selected = type == collectionType,
                ) {
                    if (type == UnifiedCollectionType.NOT_COLLECTED) {
                        onCollectionPromptChange(TvCollectionPrompt.Remove)
                    } else {
                        onIntent(TvEpisodeIntent.SetCollection(type))
                    }
                }
            }
        }
    }
}

internal enum class TvCollectionPrompt { Remove, MarkAllWatched }
