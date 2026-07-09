/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.ui.foundation.interaction.hoverable
import me.him188.ani.utils.platform.annotations.TestOnly


/**
 * @param initialVisibility 变更不会更新
 */
@Composable
fun rememberVideoControllerState(
    initialVisibility: ControllerVisibility = PlayerControllerState.DEFAULT_INITIAL_VISIBILITY
): PlayerControllerState {
    return remember {
        PlayerControllerState(initialVisibility)
    }
}

enum class PlayerFocusTarget {
    PLAYER,
    TEXT_INPUT,
}

/** Coordinates focus policy; [preferredTarget] is intent, not the currently focused Compose node. */
@Stable
class PlayerFocusState {
    var preferredTarget: PlayerFocusTarget by mutableStateOf(PlayerFocusTarget.PLAYER)
        private set

    internal val requester = FocusRequester()
    private var textInputOwner: Any? = null

    fun preferPlayer() {
        textInputOwner = null
        preferredTarget = PlayerFocusTarget.PLAYER
    }

    internal fun preferTextInput(owner: Any) {
        textInputOwner = owner
        preferredTarget = PlayerFocusTarget.TEXT_INPUT
    }

    internal fun releaseTextInput(owner: Any) {
        if (textInputOwner === owner) {
            preferPlayer()
        }
    }

    internal fun requestPlayerFocus() {
        preferPlayer()
        requester.requestFocus()
    }

    internal fun restorePlayerFocusIfPreferred() {
        if (preferredTarget == PlayerFocusTarget.PLAYER) {
            requester.requestFocus()
        }
    }
}

/** Attaches the player requester and restores focus when its policy or [reapplyKey] changes. */
@Composable
internal fun Modifier.playerFocusHost(
    state: PlayerFocusState,
    reapplyKey: Any?,
): Modifier {
    val preferredTarget = state.preferredTarget

    LaunchedEffect(state, preferredTarget, reapplyKey) {
        state.restorePlayerFocusIfPreferred()
    }
    return focusRequester(state.requester)
}

fun Modifier.playerTextInputFocus(
    state: PlayerFocusState,
    onEscape: (() -> Unit)? = null,
): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(state, owner) {
        onDispose {
            state.releaseTextInput(owner)
        }
    }

    onFocusChanged {
        if (it.hasFocus) {
            state.preferTextInput(owner)
        }
    }.onKey(ComposeKey.Escape) {
        if (onEscape == null) {
            state.requestPlayerFocus()
        } else {
            onEscape()
        }
    }
}

@Immutable
data class ControllerVisibility(
    val topBar: Boolean,
    val bottomBar: Boolean,
    val floatingBottomEnd: Boolean,
    val rhsBar: Boolean,
    val gestureLock: Boolean,
    val detachedSlider: Boolean
) {
    companion object {
        @Stable
        val Visible = ControllerVisibility(
            topBar = true,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = true,
            gestureLock = true,
            detachedSlider = false,
        )

        @Stable
        val Invisible = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = true,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )

        @Stable
        val DetachedSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = true,
        )
    }
}

@Stable
class PlayerControllerState(
    initialVisibility: ControllerVisibility = DEFAULT_INITIAL_VISIBILITY
) {
    companion object {
        val DEFAULT_INITIAL_VISIBILITY = ControllerVisibility.Invisible
    }

    val focusState = PlayerFocusState()

    private var fullVisible by mutableStateOf(initialVisibility == ControllerVisibility.Visible)
    private val hasProgressBarRequester by derivedStateOf { progressBarRequesters.isNotEmpty() }

    /**
     * 当前 UI 应当显示的状态
     */
    val visibility: ControllerVisibility by derivedStateOf {
        // 根据 hasProgressBarRequester, alwaysOn 和 fullVisible 计算正确的 `ControllerVisibility`
        if (alwaysOn) return@derivedStateOf ControllerVisibility.Visible
        if (fullVisible) return@derivedStateOf ControllerVisibility.Visible
        if (hasProgressBarRequester) return@derivedStateOf ControllerVisibility.DetachedSliderOnly
        ControllerVisibility.Invisible
    }

    /**
     * 切换显示或隐藏整个控制器.
     *
     * 此操作拥有比 [setRequestProgressBar] 更低的优先级.
     * 如果此时有人请求显示进度条, `toggleEntireVisible(false)` 将会延迟到那个人取消请求后才隐藏进度条.
     * 如果此时没有人请求显示进度条, 此函数将立即生效.
     *
     * @param visible 为 `true` 时显示整个控制器
     */
    fun toggleFullVisible(visible: Boolean? = null) {
        fullVisible = visible ?: !fullVisible
    }

    val setFullVisible: (visible: Boolean) -> Unit = {
        fullVisible = it
    }

    private val alwaysOnRequests = SnapshotStateList<Any>()

    /**
     * 总是显示. 也就是不要在 5 秒后自动隐藏.
     */
    val alwaysOn: Boolean by derivedStateOf {
        alwaysOnRequests.isNotEmpty()
    }

    /**
     * 请求控制器总是显示.
     */
    fun setRequestAlwaysOn(requester: Any, isAlwaysOn: Boolean) {
        if (isAlwaysOn) {
            if (requester in alwaysOnRequests) return
            alwaysOnRequests.add(requester)
        } else {
            alwaysOnRequests.remove(requester)
        }
    }

    private val progressBarRequesters = SnapshotStateList<Any>()

    /**
     * 请求显示进度条
     * 当目前没有显示进度条时, 将显示独立的进度条.
     * 若目前已经有进度条, 则会保持该状态, 防止自动关闭.
     *
     * @param requester 是谁希望请求显示进度条. 在 [cancelRequestProgressBarVisible] 时需要传入相同实例. 同一时刻有任一 requester 则会让进度条一直显示.
     */
    fun setRequestProgressBar(requester: Any) {
        if (requester in progressBarRequesters) return
        progressBarRequesters.add(requester)
    }

    /**
     * 取消显示进度条
     */
    fun cancelRequestProgressBarVisible(requester: Any) {
        progressBarRequesters.remove(requester)
    }

    @TestOnly
    fun getAlwaysOnRequesters(): List<Any> {
        return alwaysOnRequests
    }
}

interface AlwaysOnRequester {
    fun request()
    fun cancelRequest()
}

@Composable
fun rememberAlwaysOnRequester(
    controllerState: PlayerControllerState,
    debugName: String
): AlwaysOnRequester {
    val requester = remember(controllerState, debugName) {
        object : AlwaysOnRequester {
            override fun request() {
                controllerState.setRequestAlwaysOn(this, true)
            }

            override fun cancelRequest() {
                controllerState.setRequestAlwaysOn(this, false)
            }

            override fun toString(): String {
                return "AlwaysOnRequester($debugName)"
            }
        }
    }
    DisposableEffect(requester) {
        onDispose {
            requester.cancelRequest()
        }
    }
    return requester
}

fun Modifier.hoverToRequestAlwaysOn(
    requester: AlwaysOnRequester
): Modifier = hoverable(
    onHover = {
        requester.request()
    },
    onUnhover = {
        requester.cancelRequest()
    },
)
