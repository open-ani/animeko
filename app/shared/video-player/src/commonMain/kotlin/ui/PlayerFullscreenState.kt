/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * 播放器的全屏状态, 以及进入/退出全屏的请求入口.
 *
 * 「当前是否全屏」和「切换全屏」必须绑在同一个对象上. 拆成 `isFullscreen: Boolean` 加一个切换回调
 * 两个参数时, 两者可以互相错开 (图标画的是「退出全屏」, 点下去执行的却是「进入全屏」), 而这种错误
 * 编译期不报错, 运行期也只在特定状态下才暴露.
 *
 * 一律用目标态 [request] 而不是 toggle: 手势本身就是意图式的 (上滑进入、下滑退出、返回键退出),
 * toggle 在状态不同步时会做反. 确实需要「反一下」的入口 (双击、F 键) 用 [toggle], 它读的是同一个
 * [isFullscreen], 不可能与 UI 显示的方向相反.
 */
@Stable
interface PlayerFullscreenState {
    /**
     * 播放器当前是否处于全屏. 这是唯一真值, UI 的图标方向、手势方向判断都读它.
     */
    val isFullscreen: Boolean

    /**
     * 请求进入 ([fullscreen] 为 `true`) 或退出全屏.
     *
     * 实现必须幂等: 已经处于目标状态时不产生任何副作用. 调用方因此可以无条件调用, 不需要自己判断当前状态.
     */
    fun request(fullscreen: Boolean)
}

/**
 * 在当前状态基础上取反. 用于双击、F 键这类「反一下」的入口.
 */
fun PlayerFullscreenState.toggle() {
    request(!isFullscreen)
}

/**
 * 不支持全屏的场景: 预览、嵌入式播放器.
 */
@Stable
object NoOpPlayerFullscreenState : PlayerFullscreenState {
    override val isFullscreen: Boolean get() = false
    override fun request(fullscreen: Boolean) {}
}

/**
 * 自己持有状态的实现. 用于预览和没有平台副作用 (改窗口、改屏幕方向) 的场景;
 * 真正的播放页用 [rememberPlayerFullscreenState] 接到 app 的全屏状态上.
 */
@Stable
class MutablePlayerFullscreenState(
    initialIsFullscreen: Boolean = false,
) : PlayerFullscreenState {
    override var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)
        private set

    override fun request(fullscreen: Boolean) {
        isFullscreen = fullscreen
    }
}

/**
 * 把外部持有的全屏状态 ([isFullscreen]) 和切换全屏的副作用 ([onRequest]) 组合成一个
 * [PlayerFullscreenState].
 *
 * [onRequest] 只在目标状态与当前状态不同时调用, 因此实现里不需要再判断一次.
 */
@Composable
fun rememberPlayerFullscreenState(
    isFullscreen: () -> Boolean,
    onRequest: (fullscreen: Boolean) -> Unit,
): PlayerFullscreenState {
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnRequest by rememberUpdatedState(onRequest)
    return remember {
        object : PlayerFullscreenState {
            override val isFullscreen: Boolean get() = currentIsFullscreen()

            override fun request(fullscreen: Boolean) {
                if (currentIsFullscreen() == fullscreen) return
                currentOnRequest(fullscreen)
            }
        }
    }
}
