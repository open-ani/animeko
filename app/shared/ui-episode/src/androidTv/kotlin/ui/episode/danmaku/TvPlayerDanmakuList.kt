/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.danmaku

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.leanback.ui.episode.components.TvPlayerPanelList
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.formatPlaybackTime
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.foundation.widgets.tvOptionSurfaceColors
import org.jetbrains.compose.resources.stringResource

private data class DanmakuItemKey(val value: String) : TvFocusKey

private enum class DanmakuListFocus : TvFocusKey { Empty }

@Composable
internal fun TvDanmakuListDialog(
    danmakuList: List<DanmakuPresentation>,
    loading: Boolean,
    focus: TvFocusScope,
    entryKey: TvFocusKey,
) {
    val listState = rememberLazyListState()
    val itemKeys = remember(danmakuList) {
        // A prepend must not change existing row identities. Qualify source IDs and
        // repeated occurrences without including the row's position in the whole list.
        val occurrences = mutableMapOf<String, Int>()
        danmakuList.map {
            val id = "${it.danmaku.serviceId.value}/${it.id}"
            val occurrence = occurrences[id] ?: 0
            occurrences[id] = occurrence + 1
            "$id/$occurrence"
        }
    }
    var focusedKey by remember { mutableStateOf<TvFocusKey?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    // Capture before lazy rows are replaced; their focus callbacks can run before the effect.
    val focusBeforeUpdate = focusedKey
    val indexBeforeUpdate = focusedIndex
    val navigationGeneration = focus.userNavGeneration
    LaunchedEffect(itemKeys, loading) {
        if (focusBeforeUpdate == null || focus.userNavGeneration != navigationGeneration) return@LaunchedEffect
        val retainedIndex = itemKeys.indexOf((focusBeforeUpdate as? DanmakuItemKey)?.value)
        if (retainedIndex >= 0) {
            focusedIndex = retainedIndex
        } else if (itemKeys.isEmpty()) {
            if (loading) listState.scrollToItem(0)
            if (focus.userNavGeneration == navigationGeneration) focus.request(DanmakuListFocus.Empty)
        } else {
            // Trimming/repopulation can remove the focused row. Keep the user's place
            // inside the list, and compose the replacement before requesting its focus.
            val targetIndex = indexBeforeUpdate.coerceIn(itemKeys.indices)
            listState.scrollToItem(targetIndex)
            if (focus.userNavGeneration == navigationGeneration) {
                focus.request(DanmakuItemKey(itemKeys[targetIndex]))
            }
        }
    }
    if (danmakuList.isEmpty()) {
        val entryModifier = Modifier.tvFocusAnchor(focus, entryKey)
            .tvFocusAnchor(focus, DanmakuListFocus.Empty)
            .onFocusChanged {
                if (it.isFocused) {
                    focusedKey = DanmakuListFocus.Empty
                    focusedIndex = 0
                }
            }
        if (loading) {
            TvPlayerPanelList(
                Modifier.fillMaxWidth().testTag("tv-danmaku-list-loading").progressSemantics(), Modifier,
                empty = false, reverseLayout = true, state = listState,
            ) {
                items(8) { index ->
                    TvDanmakuItemPlaceholder(
                        // One temporary content anchor keeps Back inside the list; skeletons have no action.
                        (if (index == 0) entryModifier.focusable() else Modifier)
                            .testTag("tv-danmaku-placeholder-$index"),
                    )
                }
            }
        } else {
            TvOptionRow(
                stringResource(Lang.subject_episode_danmaku_list_empty),
                modifier = entryModifier.testTag("tv-danmaku-list-empty"),
                onClick = {},
            )
        }
    } else {
        TvPlayerPanelList(Modifier.fillMaxWidth(), Modifier, empty = false, reverseLayout = true, state = listState) {
            itemsIndexed(danmakuList, key = { index, _ -> itemKeys[index] }) { index, danmaku ->
                val itemKey = DanmakuItemKey(itemKeys[index])
                DanmakuItem(
                    danmaku,
                    modifier = (if (index == 0) Modifier.tvFocusAnchor(focus, entryKey) else Modifier)
                        .tvFocusAnchor(focus, itemKey)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedKey = itemKey
                                focusedIndex = index
                            }
                        },
                )
            }
        }
    }
}

/** 内容条目: 聚焦后反色, 与交互面板共用视觉状态. */
@Composable
private fun PanelItemSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        colors = tvOptionSurfaceColors(filled = true),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun DanmakuItem(danmaku: DanmakuPresentation, modifier: Modifier = Modifier) {
    PanelItemSurface(onClick = {}, modifier) {
        Text(
            formatPlaybackTime(danmaku.danmaku.playTimeMillis),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            danmaku.danmaku.text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
