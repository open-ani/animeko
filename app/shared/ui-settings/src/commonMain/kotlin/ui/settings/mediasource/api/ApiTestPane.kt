/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ktor.http.Url
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.cardHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_api_choose_episode
import me.him188.ani.app.ui.lang.settings_api_detail
import me.him188.ani.app.ui.lang.settings_api_no_results
import me.him188.ani.app.ui.lang.settings_api_playback
import me.him188.ani.app.ui.lang.settings_api_search_rules
import me.him188.ani.app.ui.lang.settings_api_video_resolved
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_channels
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_copied
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_copy_link
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_open_page
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test_title
import me.him188.ani.app.ui.settings.mediasource.EditMediaSourceTestDataCardDefaults
import me.him188.ani.app.ui.settings.mediasource.RefreshIndicatedHeadlineRow
import me.him188.ani.app.ui.settings.mediasource.selector.test.SelectorTestResultCard
import me.him188.ani.datasources.api.source.BrowseEpisode
import org.jetbrains.compose.resources.stringResource

/**
 * 测试数据源: 搜索条目, 选择线路与剧集, 解析视频地址.
 *
 * 布局与 [me.him188.ani.app.ui.settings.mediasource.selector.test.SelectorTestPane] 一致.
 */
@Composable
fun ApiTestPane(
    state: ApiTestState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val presentation by state.presentation.collectAsStateWithLifecycle(ApiTestPresentation.Placeholder)
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    val verticalSpacing = windowSizeClass.cardVerticalPadding
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier,
        state.gridState,
        contentPadding,
        horizontalArrangement = Arrangement.spacedBy(windowSizeClass.cardHorizontalPadding),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    stringResource(Lang.settings_mediasource_selector_test_title),
                    style = MaterialTheme.typography.headlineSmall,
                )

                with(EditMediaSourceTestDataCardDefaults) {
                    Card(
                        Modifier
                            .padding(top = verticalSpacing)
                            .fillMaxWidth(),
                        shape = cardShape,
                        colors = cardColors,
                    ) {
                        FlowRow {
                            KeywordTextField(state, Modifier.weight(1f))
                        }
                    }
                }

                RefreshIndicatedHeadlineRow(
                    headline = { Text(stringResource(Lang.settings_api_search_rules)) },
                    onRefresh = { state.restartSubjectSearch() },
                    result = presentation.subjectResult,
                    Modifier.padding(top = verticalSpacing),
                )

                Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    FastLinearProgressIndicator(
                        presentation.isSearchingSubject,
                        delayMillis = 0,
                        minimumDurationMillis = 300,
                    )
                }

                AnimatedContent(
                    presentation.subjectResult,
                    transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                ) { result ->
                    if (result !is ApiTestSubjectResult.Success) return@AnimatedContent
                    if (result.subjects.isEmpty()) {
                        Text(
                            stringResource(Lang.settings_api_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(
                            Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp)),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(result.subjects) { index, subject ->
                                SelectorTestResultCard(
                                    title = {
                                        Text(
                                            subject.name,
                                            Modifier.width(IntrinsicSize.Max),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    isSelected = presentation.selectedSubjectIndex == index,
                                    onClick = { state.selectSubject(index) },
                                    Modifier
                                        .width(IntrinsicSize.Min)
                                        .widthIn(min = 120.dp, max = 240.dp),
                                    tags = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        val subject = presentation.selectedSubject ?: return@LazyVerticalGrid
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                RefreshIndicatedHeadlineRow(
                    headline = { Text(stringResource(Lang.settings_api_detail)) },
                    onRefresh = { state.restartEpisodeSearch() },
                    result = presentation.episodeResult,
                    Modifier.padding(top = verticalSpacing),
                )

                // 浏览链接带有标识用的 fragment, 展示给用户的是站点页面地址
                PageLinkRow(subject.url.substringBefore('#'))

                Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
                    FastLinearProgressIndicator(
                        presentation.isSearchingEpisode,
                        delayMillis = 0,
                        minimumDurationMillis = 300,
                    )
                }

                val channels = presentation.channels
                if (channels.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Lang.settings_mediasource_selector_test_channels, channels.size))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            itemsIndexed(channels) { index, channel ->
                                FilterChip(
                                    selected = presentation.selectedChannelIndex == index,
                                    onClick = { state.selectChannel(index) },
                                    label = { Text(channel.label ?: channel.name ?: (index + 1).toString()) },
                                )
                            }
                        }
                    }
                } else if (presentation.episodeResult is ApiTestEpisodeResult.Success) {
                    Text(
                        stringResource(Lang.settings_api_no_results),
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RefreshIndicatedHeadlineRow(
                    headline = { Text(stringResource(Lang.settings_api_playback)) },
                    onRefresh = { state.restartVideoResolution() },
                    result = presentation.videoResult,
                    Modifier.padding(top = verticalSpacing),
                )
                Text(
                    stringResource(Lang.settings_api_choose_episode),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    FastLinearProgressIndicator(
                        presentation.isResolvingVideo,
                        delayMillis = 0,
                        minimumDurationMillis = 300,
                    )
                }
                val videoResult = presentation.videoResult
                if (videoResult is ApiTestVideoResult.Success) {
                    ResolvedVideoRow(videoResult)
                }
            }
        }

        val channel = presentation.selectedChannel ?: return@LazyVerticalGrid
        itemsIndexed(
            channel.episodes,
            key = { index, _ -> "api-test-episode-$index" },
            contentType = { _, _ -> 1 },
        ) { _, episode ->
            EpisodeCard(
                episode,
                isHighlighted = presentation.videoResult?.episode == episode,
                onClick = { state.resolveVideo(subject, channel, episode) },
                Modifier
                    .fillMaxSize()
                    .padding(bottom = verticalSpacing),
            )
        }
    }
}

@Composable
private fun PageLinkRow(url: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val copiedText = stringResource(Lang.settings_mediasource_selector_test_copied)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(Lang.settings_mediasource_selector_test_copy_link)) {
                scope.launch {
                    clipboard.setClipEntryText(url)
                    toaster.toast(copiedText)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            Modifier.padding(end = 16.dp).size(24.dp),
        )
        Text(
            url,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        val uriHandler = LocalUriHandler.current
        IconButton({ uriHandler.openUri(url) }, Modifier.padding(start = 8.dp)) {
            Icon(
                Icons.Rounded.ArrowOutward,
                contentDescription = stringResource(Lang.settings_mediasource_selector_test_open_page),
            )
        }
    }
}

/**
 * 只显示视频主机, 带签名的完整地址通过复制按钮获取, 避免出现在截图里.
 */
@Composable
private fun ResolvedVideoRow(result: ApiTestVideoResult.Success, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val copiedText = stringResource(Lang.settings_mediasource_selector_test_copied)
    val host = remember(result) { runCatching { Url(result.video.m3u8Url).host }.getOrDefault(result.video.m3u8Url) }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            Modifier.padding(end = 16.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(result.episode.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Lang.settings_api_video_resolved, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            {
                scope.launch {
                    clipboard.setClipEntryText(result.video.m3u8Url)
                    toaster.toast(copiedText)
                }
            },
            Modifier.padding(start = 8.dp),
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(Lang.settings_mediasource_selector_test_copy_link),
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: BrowseEpisode,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardDefaults.cardColors(
        containerColor = if (isHighlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            CardDefaults.cardColors().containerColor
        },
    )
    Card(onClick, modifier, colors = colors) {
        ListItem(
            headlineContent = { Text(episode.name) },
            supportingContent = episode.episodeSort?.let { sort -> { Text(sort.toString()) } },
            colors = ListItemDefaults.colors(containerColor = colors.containerColor),
        )
    }
}
