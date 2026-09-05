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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.watchtogether.SyncAction
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherRoomEndReason
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.EpisodeNavigationGuardRegistry
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.findLast
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_following
import me.him188.ani.app.ui.lang.watch_together_got_it
import me.him188.ani.app.ui.lang.watch_together_guidance_playing
import me.him188.ani.app.ui.lang.watch_together_navigation_blocked
import me.him188.ani.app.ui.lang.watch_together_rejoin_failed
import me.him188.ani.app.ui.lang.watch_together_rejoined
import me.him188.ani.app.ui.lang.watch_together_resynced
import me.him188.ani.app.ui.lang.watch_together_resynced_backward
import me.him188.ani.app.ui.lang.watch_together_resynced_forward
import me.him188.ani.app.ui.lang.watch_together_room_closed
import me.him188.ani.app.ui.lang.watch_together_session_replaced
import me.him188.ani.app.ui.lang.watch_together_stop_following
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun BoxScope.WatchTogetherOverlayHost(
    viewModel: WatchTogetherViewModel,
    aniNavigator: AniNavigator,
) {
    val state by viewModel.uiStateFlow.collectAsStateWithLifecycle()
    val playerController = LocalWatchTogetherPlayerController.current
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    val toastHostState = remember { SnackbarHostState() }
    val bubblePositionState = rememberDraggableBubblePositionState()

    OnLifecycleEvent { event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.onAppForegroundChanged(true)
            Lifecycle.Event.ON_STOP -> viewModel.onAppForegroundChanged(false)
            else -> Unit
        }
    }

    LaunchedEffect(viewModel, aniNavigator) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WatchTogetherEffect.Navigate -> {
                    when (val action = effect.action) {
                        is SyncAction.PushEpisode -> aniNavigator.navigateEpisodeDetails(
                            action.subjectId,
                            action.episodeId,
                            force = true,
                        )

                        is SyncAction.PopThenPushEpisode -> {
                            aniNavigator.findLast<NavRoutes.EpisodeDetail>()
                                ?.let { aniNavigator.popBackStack(it, inclusive = true) }
                            aniNavigator.navigateEpisodeDetails(
                                action.subjectId,
                                action.episodeId,
                                force = true,
                            )
                        }

                        is SyncAction.SeekOnly,
                        is SyncAction.SwitchEpisodeInPlace,
                            -> return@collect
                    }
                    val message = if (effect.subjectName != null && effect.episodeSort != null) {
                        getString(Lang.watch_together_guidance_playing, effect.subjectName!!, effect.episodeSort!!)
                    } else {
                        getString(Lang.watch_together_following)
                    }
                    launch {
                        toastHostState.showSnackbar(
                            WatchTogetherToastVisuals(message, Icons.Rounded.PlayArrow),
                        )
                    }
                }

                is WatchTogetherEffect.RoomEnded -> {
                    val message = getString(
                        when (effect.reason) {
                            WatchTogetherRoomEndReason.ROOM_CLOSED -> Lang.watch_together_room_closed
                            WatchTogetherRoomEndReason.SESSION_REPLACED -> Lang.watch_together_session_replaced
                        },
                    )
                    val gotIt = getString(Lang.watch_together_got_it)
                    launch {
                        toastHostState.showSnackbar(
                            WatchTogetherToastVisuals(
                                message,
                                Icons.Rounded.Groups,
                                actionLabel = gotIt,
                                duration = SnackbarDuration.Long,
                            ),
                        )
                    }
                }

                WatchTogetherEffect.Rejoined -> {
                    val message = getString(Lang.watch_together_rejoined)
                    launch {
                        toastHostState.showSnackbar(WatchTogetherToastVisuals(message, Icons.Rounded.Groups))
                    }
                }

                WatchTogetherEffect.RejoinFailed -> {
                    val message = getString(Lang.watch_together_rejoin_failed)
                    launch {
                        toastHostState.showSnackbar(WatchTogetherToastVisuals(message, Icons.Rounded.Info))
                    }
                }

                is WatchTogetherEffect.ResyncedWithHost -> {
                    val deltaSeconds = (abs(effect.deltaMillis) / 1000.0).roundToLong()
                    val message = when {
                        deltaSeconds == 0L -> getString(Lang.watch_together_resynced)
                        effect.deltaMillis > 0 -> getString(Lang.watch_together_resynced_forward, deltaSeconds)
                        else -> getString(Lang.watch_together_resynced_backward, deltaSeconds)
                    }
                    launch {
                        toastHostState.showSnackbar(WatchTogetherToastVisuals(message, Icons.Rounded.Sync))
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        EpisodeNavigationGuardRegistry.denialEvents.collect {
            val message = getString(Lang.watch_together_navigation_blocked)
            val stopFollowing = getString(Lang.watch_together_stop_following)
            launch {
                val result = toastHostState.showSnackbar(
                    WatchTogetherToastVisuals(
                        message,
                        Icons.Rounded.Info,
                        actionLabel = stopFollowing,
                        duration = SnackbarDuration.Long,
                    ),
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onIntent(WatchTogetherIntent.SetFollowing(false))
                }
            }
        }
    }

    SnackbarHost(
        toastHostState,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
    ) { data ->
        WatchTogetherToast(data)
    }

    LaunchedEffect(state.featureEnabled) {
        if (!state.featureEnabled) dialogVisible = false
    }

    LaunchedEffect(viewModel) {
        viewModel.dialogOpenRequests.collect {
            dialogVisible = true
        }
    }

    if (!state.featureEnabled) return

    AniAnimatedVisibility(
        visible = playerController.isDraggablePopupVisible,
    ) {
        DraggableWatchTogetherBubble(
            state = state,
            positionState = bubblePositionState,
            onClick = { dialogVisible = true },
            modifier = Modifier.fillMaxSize(),
        )
    }

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

/** Design-styled light prompt (同步引导 · 轻提示): inverse surface, leading icon, optional action. */
private class WatchTogetherToastVisuals(
    override val message: String,
    val icon: ImageVector,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

@Composable
private fun WatchTogetherToast(data: SnackbarData) {
    val visuals = data.visuals
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (visuals as? WatchTogetherToastVisuals)?.let {
                Icon(it.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp),
            )
            visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DraggableWatchTogetherBubble(
    state: WatchTogetherUiState,
    positionState: DraggableBubblePositionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val marginPx = with(density) { 16.dp.toPx() }
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
        var dragging by remember { mutableStateOf(false) }
        var dragTarget by remember { mutableStateOf<Offset?>(null) }

        val settledTarget = positionState.targetFor(containerSize, bubbleSize, marginPx)
        LaunchedEffect(containerSize, bubbleSize, settledTarget, dragging) {
            if (!dragging && settledTarget != null) {
                positionState.settle(settledTarget, containerSize, bubbleSize)
            }
        }

        val target = dragTarget ?: settledTarget
        val baseModifier = Modifier
            .align(Alignment.TopStart)
            .onSizeChanged { bubbleSize = it }

        if (target == null) {
            Box(modifier = baseModifier.alpha(0f)) {
                WatchTogetherBubble(state, onClick)
            }
            return@BoxWithConstraints
        }

        val animatedX by animateFloatAsState(
            targetValue = target.x,
            animationSpec = if (dragging) snap() else spring(),
        )
        val animatedY by animateFloatAsState(
            targetValue = target.y,
            animationSpec = if (dragging) snap() else spring(),
        )
        val currentAnimatedOffset by rememberUpdatedState(Offset(animatedX, animatedY))
        val animatedAlpha by animateFloatAsState(targetValue = if (state.inPlayer) 0.68f else 1f)
        val maxX = (containerSize.width - bubbleSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (containerSize.height - bubbleSize.height - marginPx).coerceAtLeast(marginPx)

        Box(
            modifier = baseModifier
                .offsetInParent(animatedX, animatedY)
                .alpha(animatedAlpha)
                .pointerInput(containerSize, bubbleSize, marginPx) {
                    detectDragGestures(
                        onDragStart = {
                            dragTarget = currentAnimatedOffset
                            dragging = true
                        },
                        onDragCancel = {
                            dragging = false
                            dragTarget = null
                        },
                        onDragEnd = {
                            val draggedTarget = dragTarget ?: settledTarget
                            if (draggedTarget != null) {
                                val snappedX = if (
                                    draggedTarget.x + bubbleSize.width / 2f < containerSize.width / 2f
                                ) {
                                    marginPx
                                } else {
                                    maxX
                                }
                                positionState.settle(
                                    Offset(snappedX, draggedTarget.y),
                                    containerSize,
                                    bubbleSize,
                                )
                            }
                            dragging = false
                            dragTarget = null
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val currentTarget = dragTarget ?: settledTarget ?: return@detectDragGestures
                        dragTarget = Offset(
                            x = (currentTarget.x + dragAmount.x).coerceIn(marginPx, maxX),
                            y = (currentTarget.y + dragAmount.y).coerceIn(marginPx, maxY),
                        )
                    }
                },
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
