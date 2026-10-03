/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectWithLifecycle
import me.him188.ani.tv.ui.foundation.TvNavigationEffect
import me.him188.ani.tv.ui.foundation.TvNavigationEvent

@Composable
fun TvExplorationRoute(
    viewModel: TvExplorationViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
) {
    val page = viewModel.explorationPageState
    val trends = page.trendingSubjectInfoPager.collectWithLifecycle()
    val recommendations = viewModel.recommendations.collectWithLifecycle()
    val followed = viewModel.followed.collectWithLifecycle()
    val media by viewModel.mediaState.collectAsStateWithLifecycle()
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvExplorationScreen(trends, recommendations, followed, media, viewModel::onIntent, modifier, navigationRailInsets)
}
