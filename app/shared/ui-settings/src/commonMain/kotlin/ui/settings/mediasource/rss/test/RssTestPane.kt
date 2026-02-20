/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.rss.test

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import me.him188.ani.app.ui.foundation.layout.connectedScroll
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.settings.mediasource.EditMediaSourceTestDataCardDefaults
import me.him188.ani.app.ui.settings.mediasource.RefreshIndicatedHeadlineRow
import me.him188.ani.app.ui.settings.mediasource.rss.detail.RssViewingItem
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile

@Composable
fun RssTestPane(
    state: RssTestPaneState,
    onNavigateToDetails: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val searchResult by state.searcher.searchResultFlow.collectAsStateWithLifecycle()
    Column(
        modifier
            .padding(contentPadding),
    ) {
        val connectedScrollState = rememberConnectedScrollState()
        Column(Modifier.connectedScroll(connectedScrollState)) {
            Text("测试数据源", style = MaterialTheme.typography.headlineSmall)

            EditTestDataCard(
                state,
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth(),
            )

            RefreshIndicatedHeadlineRow(
                headline = { Text("查询结果") },
                onRefresh = { state.searcher.restartCurrentSearch() },
                result = searchResult,
                Modifier.padding(top = 20.dp),
            )

            Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
//            androidx.compose.animation.AnimatedVisibility(
//                state.isSearching,
//                enter = expandVertically(tween(300, easing = StandardDecelerate)),
//                exit = shrinkVertically(tween(300, easing = StandardAccelerate)),
//            ) {
//                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
//            }

                FastLinearProgressIndicator(
                    state.searcher.isSearching.collectAsStateWithLifecycle().value,
                    delayMillis = 0,
                    minimumDurationMillis = 300,
                )
            }
        }
        val tabs = RssTestPaneTab.entries
        val pagerState = state.pagerState

        val switchTabTasker = rememberUiMonoTasker()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = index == pagerState.currentPage,
                    onClick = { switchTabTasker.launch { pagerState.animateScrollToPage(index) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    Modifier.weight(1f),
                ) {
                    Text(
                        when (tab) {
                            RssTestPaneTab.Overview -> "总览"
                            RssTestPaneTab.RssInfo -> "RSS 信息"
                            RssTestPaneTab.FinalResult -> "最终结果"
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        Crossfade(searchResult, Modifier.padding(top = 20.dp)) { result ->

            HorizontalPager(
                pagerState,
                userScrollEnabled = LocalPlatform.current.isMobile(),
                verticalAlignment = Alignment.Top,
            ) { pageIndex ->
                if (result !is RssTestResult.Success) return@HorizontalPager

                when (RssTestPaneTab.entries[pageIndex]) {
                    RssTestPaneTab.Overview -> {
                        RssTestPaneDefaults.OverviewTab(
                            result,
                            Modifier
                                .nestedScrollWorkaround(state.overallTabGridState, connectedScrollState)
                                .nestedScroll(connectedScrollState.nestedScrollConnection),
                            state = state.overallTabGridState,
                        )
                    }

                    RssTestPaneTab.RssInfo -> {
                        RssTestPaneDefaults.RssInfoTab(
                            result.rssItems,
                            onViewDetails = { item ->
                                state.viewDetails(item)
                                onNavigateToDetails()
                            },
                            selectedItemProvider = {
                                (state.viewingItem as? RssViewingItem.ViewingRssItem)?.value
                            },
                            Modifier
                                .nestedScrollWorkaround(state.rssTabGridState, connectedScrollState)
                                .nestedScroll(connectedScrollState.nestedScrollConnection),
                            lazyStaggeredGridState = state.rssTabGridState,
                        )
                    }

                    RssTestPaneTab.FinalResult -> {
                        RssTestPaneDefaults.FinalResultTab(
                            result,
                            onViewDetails = { item ->
                                state.viewDetails(item)
                                onNavigateToDetails()
                            },
                            selectedItemProvider = {
                                (state.viewingItem as? RssViewingItem.ViewingMedia)?.value
                            },
                            Modifier
                                .nestedScrollWorkaround(state.finalResultsTabGridState, connectedScrollState)
                                .nestedScroll(connectedScrollState.nestedScrollConnection),
                            lazyGridState = state.finalResultsTabGridState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTestDataCard(
    state: RssTestPaneState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = EditMediaSourceTestDataCardDefaults.cardShape,
        colors = EditMediaSourceTestDataCardDefaults.cardColors,
    ) {
        EditMediaSourceTestDataCardDefaults.FlowRow {
            EditMediaSourceTestDataCardDefaults.KeywordTextField(state, Modifier.weight(1f))
            EditMediaSourceTestDataCardDefaults.EpisodeSortTextField(state, Modifier.weight(1f))
            if (state.showPage) {
                TextField(
                    value = state.pageString,
                    onValueChange = { state.pageString = it.trim() },
                    Modifier.weight(1f),
                    label = { Text("页码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    isError = state.pageIsError,
                )
            }
        }
    }
}

@Immutable
@Serializable
enum class RssTestPaneTab {
    Overview,
    RssInfo,
    FinalResult,
}

@Stable
object RssTestPaneDefaults

@Composable
@PreviewLightDark
fun PreviewRssTestResultItem() {
    RssTestResultMediaItem(
        TestMediaList[0],
        false,
        {},
    )
}
