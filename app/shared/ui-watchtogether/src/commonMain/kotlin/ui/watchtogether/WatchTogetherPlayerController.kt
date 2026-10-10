/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class WatchTogetherPlayerController(
    private val onToggle: () -> Unit = {},
) {
    private val hideRequests = SnapshotStateList<Any>()

    /**
     * 悬浮气泡是否可见. 只要还有一个请求者要求隐藏就不可见.
     */
    val isDraggablePopupVisible: Boolean by derivedStateOf {
        hideRequests.isEmpty()
    }

    fun toggle() {
        onToggle()
    }

    /**
     * 请求隐藏悬浮气泡; 同一请求者重复请求是幂等的.
     *
     * 多个请求者各自持有自己的请求, 互不影响: 播放页重建 (一起看跟播切集) 时旧页面只撤销自己的请求,
     * 新页面刚提出的请求仍然有效.
     *
     * @param requester 请求方; 取消时必须传同一个实例
     */
    fun setRequestHidden(requester: Any, isHidden: Boolean) {
        if (isHidden) {
            if (requester in hideRequests) return
            hideRequests.add(requester)
        } else {
            hideRequests.remove(requester)
        }
    }

    @TestOnly
    fun getHideRequesters(): List<Any> {
        return hideRequests
    }
}

val LocalWatchTogetherPlayerController = staticCompositionLocalOf { WatchTogetherPlayerController() }
