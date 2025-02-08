/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodePresentation
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestSearchSubjectResult
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.cardHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.mediasource.EditMediaSourceTestDataCardDefaults
import me.him188.ani.app.ui.settings.mediasource.RefreshIndicatedHeadlineRow
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigurationDefaults

/**
 * 测试数据源. 编辑
 */
@Composable
fun SharedTransitionScope.SelectorTestPane(
    state: SelectorTestState,
    onViewEpisode: (SelectorTestEpisodePresentation) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val presentation by state.presentation.collectAsStateWithLifecycle(
        SelectorTestPresentation.Placeholder,
    )
    val verticalSpacing = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier,
        state.gridState,
        contentPadding,
        horizontalArrangement = Arrangement.spacedBy(currentWindowAdaptiveInfo1().windowSizeClass.cardHorizontalPadding),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "测试数据源",
                    style = MaterialTheme.typography.headlineSmall,
                )

                EditTestDataCard(
                    state,
                    Modifier
                        .padding(top = verticalSpacing)
                        .fillMaxWidth(),
                )

                RefreshIndicatedHeadlineRow(
                    headline = { Text(SelectorConfigurationDefaults.STEP_NAME_1) },
                    onRefresh = { state.restartCurrentSubjectSearch() },
                    result = presentation.subjectSearchResult,
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
                    presentation.subjectSearchResult,
                    transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                ) { result ->
                    if (result is SelectorTestSearchSubjectResult.Success) {
                        SelectorTestSubjectResultLazyRow(
                            items = result.subjects,
                            presentation.selectedSubjectIndex,
                            onSelect = { index, _ ->
                                state.selectSubjectIndex(index)
                            },
                            modifier = Modifier.padding(top = verticalSpacing - 8.dp),
                        )
                    }
                }
            }
        }

        val selectedSubject = presentation.selectedSubject
        if (selectedSubject != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    RefreshIndicatedHeadlineRow(
                        headline = { Text(SelectorConfigurationDefaults.STEP_NAME_2) },
                        onRefresh = { state.restartCurrentEpisodeSearch() },
                        result = presentation.episodeListSearchResult,
                        Modifier.padding(top = verticalSpacing),
                    )

                    val url = selectedSubject.subjectDetailsPageUrl
                    val clipboard = LocalClipboardManager.current
                    val toaster = LocalToaster.current
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = "复制条目链接") {
                                clipboard.setText(AnnotatedString(url))
                                toaster.toast("已复制")
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
                                contentDescription = "打开条目页面",
                            )
                        }
                    }

                    Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
                        FastLinearProgressIndicator(
                            presentation.isSearchingEpisode,
                            delayMillis = 0,
                            minimumDurationMillis = 300,
                        )
                    }
                }
            }

            val result = presentation.episodeListSearchResult
            if (result is SelectorTestEpisodeListResult.Success) {
                val channels = result.channels
                if (channels != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier
                                .fillMaxWidth() // workaround for grid span issue https://youtrack.jetbrains.com/issue/CMP-2102
                                .padding(bottom = (verticalSpacing - 8.dp).coerceAtLeast(0.dp)),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${channels.size} 线路")
                            LazyRow(
                                Modifier,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(channels) {
                                    FilterChip(
                                        selected = presentation.filterByChannel == it,
                                        onClick = {
                                            state.filterByChannel(if (presentation.filterByChannel == it) null else it)
                                        },
                                        label = { Text(it) },
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    presentation.filteredEpisodes ?: emptyList(),
                    key = { it.id.toString() },
                    contentType = { 1 },
                ) { episode ->
                    SelectorTestEpisodeListGridDefaults.EpisodeCard(
                        episode,
                        { onViewEpisode(episode) },
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = verticalSpacing)
                            .sharedBounds(rememberSharedContentState(episode.id), animatedVisibilityScope),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditTestDataCard(
    state: SelectorTestState,
    modifier: Modifier = Modifier,
) {
    with(EditMediaSourceTestDataCardDefaults) {
        Card(
            modifier,
            shape = cardShape,
            colors = cardColors,
        ) {
            FlowRow {
                KeywordTextField(state, Modifier.weight(1f))
                EpisodeSortTextField(state, Modifier.weight(1f))
            }
        }
    }
}
