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

        /**
         * 控制器原本隐藏时的 seek 指示状态：只展示固定在播放器底部的独立进度条。
         */
        @Stable
        val DetachedSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = true,
        )

        /**
         * 已有底栏交互时的 seek 状态：保留底栏原进度条及其布局，其他控制器元素仅在视觉上隐藏。
         * 直接拖动进度条必须使用此状态，避免替换正在接收触摸事件的组件。
         */
        @Stable
        val InlineSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
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
    private val hasInlineProgressSliderRequester by derivedStateOf {
        inlineProgressSliderRequesters.isNotEmpty()
    }

    /**
     * 当前 UI 应当显示的状态
     */
    val visibility: ControllerVisibility by derivedStateOf {
        // 根据 hasProgressBarRequester, alwaysOn 和 fullVisible 计算正确的 `ControllerVisibility`
        if (hasInlineProgressSliderRequester) return@derivedStateOf ControllerVisibility.InlineSliderOnly
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

    private val inlineProgressSliderRequesters = SnapshotStateList<Any>()

    /**
     * 请求只显示底部控制栏内已有的 inline progress slider.
     *
     * 该进度条属于 bottom bar；进入此模式后仍保留整个 bottom bar 的布局，
     * 只是将进度条以外的控制器元素隐藏。因此正在接收触摸事件的进度条不会被替换。
     * 适用于直接拖动进度条，或控制器可见时开始的屏幕横滑。
     *
     * [setRequestProgressBar] 请求的则是 bottom bar 之外的另一个 detached progress slider，
     * 用于控制器隐藏时的屏幕横滑，不能代替正在接收触摸事件的 inline progress slider.
     *
     * @param requester 请求方；取消时必须将同一实例传给 [cancelRequestInlineProgressSlider].
     */
    fun setRequestInlineProgressSlider(requester: Any) {
        if (requester in inlineProgressSliderRequesters) return
        inlineProgressSliderRequesters.add(requester)
    }

    fun cancelRequestInlineProgressSlider(requester: Any) {
        inlineProgressSliderRequesters.remove(requester)
    }

    /**
     * 请求在控制器隐藏时显示独立的 detached progress slider.
     *
     * 该进度条位于 bottom bar 之外，是专门用于指示屏幕横滑 seek 的另一个组件；
     * 它不会保留或复用 bottom bar 内的 inline progress slider.
     * 适用于控制器隐藏时开始的屏幕横滑。
     *
     * 如果控制器当前完整显示，则完整控制器优先；控制器隐藏后，只要本请求仍存在，
     * 就会显示 detached progress slider，而不是让进度指示一并消失。
     *
     * @param requester 请求方；取消时必须将同一实例传给 [cancelRequestProgressBarVisible].
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
