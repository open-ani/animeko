/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.leanback.ui.foundation.TvNavigationEffect
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent

@Composable
fun TvEpisodeRoute(
    viewModel: TvEpisodeViewModel,
    togetherViewModel: TvWatchTogetherViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val togetherState by togetherViewModel.uiState.collectAsState()
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    LaunchedEffect(viewModel) { viewModel.onIntent(TvEpisodeIntent.UiReady) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onIntent(TvEpisodeIntent.ReleaseHeldSpeed)
            if (event == Lifecycle.Event.ON_STOP) viewModel.onIntent(TvEpisodeIntent.ForegroundChanged(false))
            if (event == Lifecycle.Event.ON_START) viewModel.onIntent(TvEpisodeIntent.ForegroundChanged(true))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onIntent(TvEpisodeIntent.ReleaseHeldSpeed)
        }
    }
    TvEpisodeScreen(
        uiState = state,
        togetherState = togetherState,
        onTogetherIntent = togetherViewModel::onIntent,
        commentsPager = viewModel.episodeCommentsPager,
        focusRequests = viewModel.focusRequests,
        actionEvents = viewModel.actionEvents,
        onIntent = viewModel::onIntent,
        video = { VideoPlayer(viewModel.player, it) },
        resolver = { viewModel.mediaResolver.ComposeContent() },
        danmaku = { TvPlayerDanmakuHost(viewModel.danmakuHostState, it) },
        modifier = modifier,
    )
}
