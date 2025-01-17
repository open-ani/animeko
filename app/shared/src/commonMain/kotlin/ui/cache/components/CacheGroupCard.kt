/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.FileSize

/**
 * 一个组缓存的共同信息, 例如一个条目的十几个剧集的缓存 [CachedMedia] 都来自一个季度全集 [Media], 那它们就有相同的 [CacheGroupCommonInfo].
 */
@Immutable
class CacheGroupCommonInfo(
    val subjectId: Int,
    val subjectDisplayName: String,
    val mediaSourceId: String,
    val allianceName: String,
    val imageUrl: String? = null,
)

/**
 * 表示一个合并的缓存组.
 * @see CacheGroupCommonInfo
 */
@Stable
class CacheGroupState(
    /**
     * 共同属于的 media.
     */
    val media: Media,
    commonInfo: State<CacheGroupCommonInfo?>, // null means loading
    episodes: List<CacheEpisodeState>,
    stats: State<Stats>,
) {
    @Immutable
    data class Stats(
        val downloadSpeed: FileSize,
        val downloadedSize: FileSize,
        /**
         * 上传速度, 每秒. 对于不支持上传的缓存, 该值为 [FileSize.Zero].
         *
         * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
         * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
         */
        val uploadSpeed: FileSize,
    )

    val episodes = episodes.sortedBy { it.sort }
    val cacheId = episodes.firstOrNull()?.cacheId

    val latestCreationTime = episodes.asSequence().mapNotNull { it.creationTime }.maxOfOrNull { it }

    private val allEpisodesFinished by derivedStateOf {
        episodes.all { it.isFinished }
    }
    val downloadSpeedText by derivedStateOf {
        computeSpeedText(
            speed = if (allEpisodesFinished) FileSize.Unspecified else stats.value.downloadSpeed,
            size = stats.value.downloadedSize,
        )
    }

    val uploadSpeedText by derivedStateOf {
        computeSpeedText(
            speed = stats.value.uploadSpeed,
            size = FileSize.Unspecified,
        )
    }

    val subjectId by derivedStateOf {
        commonInfo.value?.subjectId?.takeIf { it != 0 }
    }

    private val commonInfo by commonInfo
    var expanded: Boolean by mutableStateOf(true)

    /**
     * null means loading
     */
    val cardTitle by derivedStateOf {
        this.commonInfo?.subjectDisplayName
    }

    companion object {
        fun computeSpeedText(speed: FileSize, size: FileSize): String {
            return when {
                size == FileSize.Unspecified && speed == FileSize.Unspecified -> ""
                size == FileSize.Unspecified -> "$speed/s"
                speed == FileSize.Unspecified -> "$size"
                else -> "$size ($speed/s)"
            }
        }
    }
}

@Immutable
data class CacheGroupCardLayoutProperties(
    /**
     * 整个卡片的内部左右边距
     */
    val horizontalPadding: Dp = 20.dp,
    /**
     * 头部卡片内部的垂直间距
     */
    val headerVerticalSpacing: Dp = 16.dp,
    val headerVerticalPadding: Dp = 20.dp,
    /**
     * 头部卡片内部的组件细节的垂直间距, 例如 [FlowRow] 的 vertical arrangemnet
     */
    val headerInnerVerticalSpacing: Dp = 12.dp,

    /**
     * 卡片下半部分的剧集缓存列表的垂直内边距
     */
    val episodeListVerticalPadding: Dp = 16.dp,

    /**
     * 每两个剧集之间的间距
     */
    val episodeItemSpacing: Dp = 2.dp,
)

@Immutable
object CacheGroupCardDefaults {
    @Stable
    val LayoutProperties = CacheGroupCardLayoutProperties()
}

@Composable
fun CacheGroupCard(
    state: CacheGroupState,
    modifier: Modifier = Modifier,
    layoutProperties: CacheGroupCardLayoutProperties = CacheGroupCardDefaults.LayoutProperties,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val outerCardColors = AniThemeDefaults.primaryCardColors()
    Card(
        modifier,
        shape = shape,
        colors = outerCardColors,
    ) {
        Card(
            Modifier.fillMaxWidth()
                .clickable { state.expanded = !state.expanded },
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                Modifier.padding(
                    top = (layoutProperties.headerVerticalPadding - 8.dp).coerceAtLeast(0.dp),
                    bottom = layoutProperties.headerVerticalPadding,
                ),
            ) {
                Row(
                    Modifier
                        .padding(
                            start = layoutProperties.horizontalPadding,
                            end = (layoutProperties.horizontalPadding - 8.dp).coerceAtLeast(0.dp),
                            bottom = (layoutProperties.horizontalPadding - 8.dp).coerceAtLeast(0.dp),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val navigator = LocalNavigator.current
                    // 条目标题
                    Box(
                        Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Crossfade(
                                state.cardTitle,
                                Modifier.animateContentSize(),
                            ) {
                                SelectionContainer { Text(it ?: "") }
                            }
                        }
                    }

                    Row(Modifier.align(Alignment.Top)) { // no horizontal spacing
                        state.cacheId?.let {
                            IconButton({ navigator.navigateCacheDetails(it) }) {
                                Icon(Icons.Outlined.Info, "更多缓存信息")
                            }

                        }

                        state.subjectId?.let { subjectId ->
                            IconButton({ navigator.navigateSubjectDetails(subjectId) }) {
                                Icon(Icons.Outlined.ArrowOutward, "查看条目详情")
                            }
                        }
                    }
                }

                // 下载速度和上传速度
                FlowRow(
                    Modifier
                        .padding(horizontal = layoutProperties.horizontalPadding)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        layoutProperties.headerInnerVerticalSpacing,
                        alignment = Alignment.CenterVertically,
                    ),
                ) {
                    ProvideTextStyleContentColor(MaterialTheme.typography.labelLarge) {
                        Row(
                            Modifier,
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                alignment = Alignment.Start,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Box {
                                // 占用足够大的位置, 防止下载速度更新时导致上传速度的位置变了
                                Text("888.88 MB (888.88 MB/s)", Modifier.alpha(0f), softWrap = false)
                                Text(state.downloadSpeedText, softWrap = false)
                            }
                        }

                        Row(
                            Modifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Upload, null)
                            Text(state.uploadSpeedText, softWrap = false)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(state.expanded) {
            Column(
                Modifier
                    .padding(
                        top = (layoutProperties.episodeListVerticalPadding - 8.dp).coerceAtLeast(0.dp),
                        bottom = layoutProperties.episodeListVerticalPadding,
                    )
                    .padding(horizontal = (layoutProperties.horizontalPadding - 16.dp).coerceAtLeast(0.dp)),
                verticalArrangement = Arrangement.spacedBy(layoutProperties.episodeItemSpacing), // each item already has inner paddings
            ) {
                for (episode in state.episodes) {
                    CacheEpisodeItem(episode, containerColor = outerCardColors.containerColor)
                }
            }
        }
    }
}
