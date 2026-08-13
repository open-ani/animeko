/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.playback

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

/**
 * 被保留在后台的那个播放会话是哪一集.
 *
 * 只放"回得去"所需的最小信息: 入口按钮拿它导航回播放页 (同一路由参数即命中同一个会话).
 * **不带标题/封面**: 那些要等条目信息加载, 而入口只需要在图标旁写"正在播放".
 */
@Immutable
data class RetainedPlaybackSessionInfo(
    val subjectId: Int,
    val episodeId: Int,
)

/**
 * 后台保留着的播放会话的入口把手.
 *
 * 会话本体 (播放器 + 整条起播流水线) 挂在应用根部一个应用级 ViewModel 上, 退出播放页不销毁;
 * 而"回到会话"的入口按钮散落在各页面里 (遥控器形态是侧边栏的"正在播放"条目). 入口经本把手
 * 判断该不该显示、结束会话, 导航则由入口自己按 [RetainedPlaybackSessionInfo] 发起 ——
 * 入口**绝不自己去持有会话**, 那样会造出第二个播放器.
 *
 * 只在开了 `AniUiBehavior.retainPlaybackSession` 的形态下有非空的 [session]; 其余形态拿到的是
 * [None], 入口自然不渲染.
 */
@Stable
interface PlaybackSessionEntry {
    /** 当前保留着的会话; `null` = 没有 (没进过播放页, 或已被结束/被新的会话替换后又结束). */
    val session: RetainedPlaybackSessionInfo?

    /**
     * 结束会话: 销毁播放器与整条流水线.
     *
     * 必须有这个出口 —— 否则会话会一直活着 (占着解码器与缓冲), 用户只能靠再点开另一集来替换它.
     */
    fun close()

    /** 不保留会话的形态 (手机 / 桌面) 用的空实现. */
    object None : PlaybackSessionEntry {
        override val session: RetainedPlaybackSessionInfo? get() = null
        override fun close() {}
    }
}

/**
 * 由 AniAppContent 在应用根部 provide (NavHost 与入口按钮都在里面).
 *
 * 默认 [PlaybackSessionEntry.None]: 预览与测试里没有宿主, 入口按钮自然不渲染, 不必到处判空.
 */
val LocalPlaybackSessionEntry = compositionLocalOf<PlaybackSessionEntry> { PlaybackSessionEntry.None }
