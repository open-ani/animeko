/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


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
                item { Text("确定取消收藏？", color = Color.White, modifier = Modifier.padding(10.dp)) }
                item {
                    TvOptionRow(
                        "取消收藏",
                        modifier = entryModifier,
                    ) { onIntent(TvEpisodeIntent.SetCollection(UnifiedCollectionType.NOT_COLLECTED)) }
                }
                item { TvOptionRow("保留收藏") { onCollectionPromptChange(null) } }
            }

            collectionPrompt == TvCollectionPrompt.MarkAllWatched -> {
                item {
                    TvOptionRow(
                        "将全部剧集标为已看",
                        modifier = entryModifier,
                    ) { onIntent(TvEpisodeIntent.MarkAllWatched()) }
                }
                item { TvOptionRow("仅修改收藏状态") { onCollectionPromptChange(null) } }
            }

            else -> items(UnifiedCollectionType.entries) { type ->
                TvOptionRow(
                    if (type == UnifiedCollectionType.NOT_COLLECTED) "取消收藏" else type.tvLabel(),
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
