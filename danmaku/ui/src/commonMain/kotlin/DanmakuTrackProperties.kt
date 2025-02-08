/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
class DanmakuTrackProperties(
    /**
     * Shift of the danmaku to be considered as fully out of the screen.
     */
    val visibilitySafeArea: Int = 0,
    /**
     * vertical padding of track, both top and bottom.
     */
    val verticalPadding: Int = 1,
    /**
     * speed multiplier for speed of floating danmaku.
     * represents a multiplier to speed that 2x length of base danmaku text
     */
    val speedMultiplier: Float = 1.14f,
    /**
     * fixed danmaku present duration.
     * unit: ms
     */
    val fixedDanmakuPresentDuration: Long = 5000,
    /**
     * 通常和 [DanmakuSessionFlowState.repopulateDistance][me.him188.ani.danmaku.api.DanmakuSessionFlowState.repopulateDistance] 一样.
     */
    val initialFrameTimeNanos: Long = 20_000_000_000L,
) {
    companion object {
        @Stable
        val Default = DanmakuTrackProperties()
    }
}