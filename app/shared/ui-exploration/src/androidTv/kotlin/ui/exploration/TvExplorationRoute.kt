/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.exploration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import me.him188.ani.leanback.ui.foundation.TvNavigationEffect
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent

@Composable
fun TvExplorationRoute(
    viewModel: TvExplorationViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = viewModel.explorationPageState
    val trends = page.trendingSubjectInfoPager.collectWithLifecycle()
    val recommendations = page.recommendationPager.collectAsLazyPagingItems()
    val followed = page.followedSubjectsPager.collectAsLazyPagingItems()
    val media by viewModel.mediaState.collectAsState()
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvExplorationScreen(trends, recommendations, followed, media, viewModel::onIntent, modifier)
}
