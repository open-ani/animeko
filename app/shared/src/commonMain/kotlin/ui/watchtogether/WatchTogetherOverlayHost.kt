/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import me.him188.ani.app.domain.watchtogether.SyncAction
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherRoomEndReason
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.EpisodeNavigationGuardRegistry
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.findLast
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_following
import me.him188.ani.app.ui.lang.watch_together_navigation_blocked
import me.him188.ani.app.ui.lang.watch_together_rejoined
import me.him188.ani.app.ui.lang.watch_together_rejoin_failed
import me.him188.ani.app.ui.lang.watch_together_room_closed
import me.him188.ani.app.ui.lang.watch_together_session_replaced
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.WatchTogetherOverlayHost(
    viewModel: WatchTogetherViewModel,
    aniNavigator: AniNavigator,
) {
    val state by viewModel.uiStateFlow.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val followingMessage = stringResource(Lang.watch_together_following)
    val navigationBlockedMessage = stringResource(Lang.watch_together_navigation_blocked)
    val roomClosedMessage = stringResource(Lang.watch_together_room_closed)
    val sessionReplacedMessage = stringResource(Lang.watch_together_session_replaced)
    val rejoinedMessage = stringResource(Lang.watch_together_rejoined)
    val rejoinFailedMessage = stringResource(Lang.watch_together_rejoin_failed)
    var dialogVisible by rememberSaveable { mutableStateOf(false) }

    OnLifecycleEvent { event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.onAppForegroundChanged(true)
            Lifecycle.Event.ON_STOP -> viewModel.onAppForegroundChanged(false)
            else -> Unit
        }
    }

    LaunchedEffect(
        viewModel,
        aniNavigator,
        followingMessage,
        roomClosedMessage,
        sessionReplacedMessage,
        rejoinedMessage,
        rejoinFailedMessage,
    ) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WatchTogetherEffect.Navigate -> when (val action = effect.action) {
                    is SyncAction.PushEpisode -> {
                        aniNavigator.navigateEpisodeDetails(
                            action.subjectId,
                            action.episodeId,
                            force = true,
                        )
                        toaster.toast(followingMessage)
                    }

                    is SyncAction.PopThenPushEpisode -> {
                        aniNavigator.currentNavigator.findLast<NavRoutes.EpisodeDetail>()
                            ?.let { aniNavigator.popBackStack(it, inclusive = true) }
                        aniNavigator.navigateEpisodeDetails(
                            action.subjectId,
                            action.episodeId,
                            force = true,
                        )
                        toaster.toast(followingMessage)
                    }

                    is SyncAction.SeekOnly,
                    is SyncAction.SwitchEpisodeInPlace,
                    -> Unit
                }

                is WatchTogetherEffect.RoomEnded -> toaster.toast(
                    when (effect.reason) {
                        WatchTogetherRoomEndReason.ROOM_CLOSED -> roomClosedMessage
                        WatchTogetherRoomEndReason.SESSION_REPLACED -> sessionReplacedMessage
                    },
                )

                WatchTogetherEffect.Rejoined -> toaster.toast(rejoinedMessage)
                WatchTogetherEffect.RejoinFailed -> toaster.toast(rejoinFailedMessage)
            }
        }
    }

    LaunchedEffect(navigationBlockedMessage) {
        EpisodeNavigationGuardRegistry.denialEvents.collect {
            toaster.toast(navigationBlockedMessage)
        }
    }

    LaunchedEffect(state.featureEnabled) {
        if (!state.featureEnabled) dialogVisible = false
    }

    if (!state.featureEnabled) return

    DraggableWatchTogetherBubble(
        state = state,
        onClick = { dialogVisible = true },
        modifier = Modifier.fillMaxSize(),
    )

    if (dialogVisible) {
        WatchTogetherDialog(
            state = state,
            onIntent = viewModel::onIntent,
            onLogin = {
                dialogVisible = false
                aniNavigator.navigateLogin()
            },
            onDismissRequest = { dialogVisible = false },
        )
    }
}

@Composable
private fun BoxScope.DraggableWatchTogetherBubble(
    state: WatchTogetherUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val marginPx = with(density) { 16.dp.toPx() }
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
        var targetX by rememberSaveable { mutableFloatStateOf(-1f) }
        var targetY by rememberSaveable { mutableFloatStateOf(-1f) }
        var dragging by remember { mutableStateOf(false) }

        val maxX = (containerWidth - bubbleSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (containerHeight - bubbleSize.height - marginPx).coerceAtLeast(marginPx)
        LaunchedEffect(containerWidth, containerHeight, bubbleSize) {
            targetX = if (targetX < 0f) maxX else targetX.coerceIn(marginPx, maxX)
            targetY = if (targetY < 0f) {
                (containerHeight * 0.68f).coerceIn(marginPx, maxY)
            } else {
                targetY.coerceIn(marginPx, maxY)
            }
        }

        val animatedX by animateFloatAsState(
            targetValue = targetX.coerceAtLeast(0f),
            animationSpec = if (dragging) snap() else spring(),
        )
        val animatedY by animateFloatAsState(
            targetValue = targetY.coerceAtLeast(0f),
            animationSpec = snap(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { bubbleSize = it }
                .alpha(if (state.inPlayer) 0.68f else 1f)
                .pointerInput(containerWidth, containerHeight, bubbleSize) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragCancel = { dragging = false },
                        onDragEnd = {
                            dragging = false
                            targetX = if (targetX + bubbleSize.width / 2f < containerWidth / 2f) {
                                marginPx
                            } else {
                                maxX
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        targetX = (targetX + dragAmount.x).coerceIn(marginPx, maxX)
                        targetY = (targetY + dragAmount.y).coerceIn(marginPx, maxY)
                    }
                }
                .offsetInParent(animatedX, animatedY),
        ) {
            WatchTogetherBubble(state, onClick)
        }
    }
}

private fun Modifier.offsetInParent(x: Float, y: Float): Modifier =
    this.then(
        Modifier.offset {
            IntOffset(x.roundToInt(), y.roundToInt())
        },
    )
