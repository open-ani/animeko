/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.data.models.episode.EpisodeProgressItem
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.FilledTonalCombinedClickButton
import me.him188.ani.app.ui.foundation.indication.HorizontalIndicator
import me.him188.ani.app.ui.foundation.indication.IndicatedBox
import me.him188.ani.app.ui.foundation.interaction.VibrationStrength
import me.him188.ani.app.ui.foundation.interaction.vibrateIfSupported
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


/**
 * "选集播放" 对话框, 包含剧集观看进度, 还可以标记为已看
 */
@Composable
fun EpisodeListDialog(
    state: EpisodeListState,
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigator by rememberUpdatedState(LocalNavigator.current)
    val context by rememberUpdatedState(LocalContext.current)

    EpisodeListDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        onClickCache = { navigator.navigateSubjectCaches(state.subjectId) },
        actions = actions,
    ) {
        EpisodeListFlowRow(
            episodes = { state.episodes },
            onClickEpisodeState = {
                navigator.navigateEpisodeDetails(state.subjectId, it.episodeId)
            },
            onLongClickEpisode = { progressItem ->
                context.vibrateIfSupported(VibrationStrength.TICK)
                state.toggleEpisodeWatched(progressItem)
            },
            colors = EpisodeListDefaults.colors(state.theme),
        )
    }
}

@Composable
fun EpisodeListDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    onClickCache: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest, properties) {
        Card {
            Box {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text("选集播放", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                    }

                    Row(Modifier.padding(top = 8.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            title()
                        }
                    }

                    Row(
                        Modifier.clipToBounds()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp)
                            .padding(top = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null)

                        Text("长按还可以标记为已看", Modifier.padding(start = 4.dp))
                    }

                    Row(Modifier.padding(top = 16.dp).align(Alignment.End)) {
                        actions()

                        FilledTonalButton(onDismissRequest, Modifier.padding(start = 8.dp)) {
                            Text("取消")
                        }
                    }
                }

                IconButton(onClickCache, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Download, "缓存")
                }
            }
        }
    }
}


@Composable
fun EpisodeListFlowRow(
    episodes: () -> List<EpisodeProgressItem>,
    onClickEpisodeState: (episode: EpisodeProgressItem) -> Unit,
    onLongClickEpisode: (episode: EpisodeProgressItem) -> Unit,
    modifier: Modifier = Modifier,
    colors: EpisodeListColors = EpisodeListDefaults.colors(),
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (it in episodes()) {
            SmallEpisodeButton(
                episodeSort = { it.episodeSort },
                watchStatus = it.collectionType,
                isOnAir = it.isOnAir,
                onClick = { onClickEpisodeState(it) },
                onLongClick = { onLongClickEpisode(it) },
                cacheStatus = it.cacheStatus,
                colors = colors,
            )
        }
    }
//
//    // 初始化时滚动到上次观看的那一集
//    val density by rememberUpdatedState(LocalDensity.current)
//    LaunchedEffect(true) {
//        if (item.episodes.isNotEmpty()) {
//            item.lastWatchedEpIndex?.let {
//                val index = it.minus(1).coerceAtLeast(0)
//                if (index != 0) {
//                    // 左边留出来半个按钮的宽度, 让用户知道还有前面的
//                    state.scrollToItem(
//                        index,
//                        density.run { 16.dp.toPx().toInt() }
//                    )
//                }
//            }
//        }
//    }
}

@Immutable
class EpisodeListColors(
    /**
     * 看过或抛弃的颜色
     */
    val doneOrDroppedColor: Color,
    /**
     * 可以看但还没看
     */
    val canWatchColor: Color,
    /**
     * 未开播颜色
     */
    val notPublishedColor: Color,
)

object EpisodeListDefaults {
    @Composable
    fun colors(
        theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
        action: Color = MaterialTheme.colorScheme.primary,
        disabled: Color = MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
    ): EpisodeListColors {
        val dark = action.weaken()
        return when (theme) {
            EpisodeListProgressTheme.ACTION -> EpisodeListColors(
                doneOrDroppedColor = dark,
                canWatchColor = action,
                notPublishedColor = disabled,
            )

            EpisodeListProgressTheme.LIGHT_UP -> EpisodeListColors(
                doneOrDroppedColor = action,
                canWatchColor = dark,
                notPublishedColor = disabled,
            )
        }
    }
}


@Composable
private fun SmallEpisodeButton(
    episodeSort: () -> String,
    watchStatus: UnifiedCollectionType,
    isOnAir: Boolean?, // null means unknown
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cacheStatus: EpisodeCacheStatus? = null,
    colors: EpisodeListColors = EpisodeListDefaults.colors(),
) {
    val isDoneOrDropped = watchStatus == UnifiedCollectionType.DONE || watchStatus == UnifiedCollectionType.DROPPED
    IndicatedBox(
        {
            HorizontalIndicator(
                color = cacheStatusIndicationColor(
                    cacheStatus,
                    isDoneOrDropped,
                ),
                shape = MaterialTheme.shapes.small,
                height = 6.dp,
            )
        },
        modifier,//.padding(end = if (hasBadge) 12.dp else 0.dp)
    ) {
        val containerColor = when {
            isDoneOrDropped -> colors.doneOrDroppedColor
            isOnAir != false -> colors.notPublishedColor // 未开播
            else -> colors.canWatchColor // 还没看
        }
        FilledTonalCombinedClickButton(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .combinedClickable(onLongClick = onLongClick, onClick = onClick)
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(episodeSort(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 剧集按钮底部的指示条颜色, 用于表示缓存状态
 */
@Composable
fun cacheStatusIndicationColor(
    cacheStatus: EpisodeCacheStatus?,
    isDoneOrDropped: Boolean,
): Color {
    if (isDoneOrDropped) return Color.Transparent
    return when (cacheStatus) {
        is EpisodeCacheStatus.Cached -> {
            val primaryColor = MaterialTheme.colorScheme.primary.stronglyWeaken()
            primaryColor.compositeOver(Color.Green)
        }

        is EpisodeCacheStatus.Caching -> Color(0xDFe0ef51)

        else -> Color.Transparent
    }
}