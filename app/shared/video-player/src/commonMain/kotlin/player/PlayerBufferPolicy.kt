/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.player

import kotlin.time.Duration

/**
 * 各平台统一的播放器缓冲策略.
 *
 * - [forward]: 从当前播放位置往前预读多少内容.
 * - [backward]: 已播放的内容保留多久, 用于快速往后退而不必重新下载.
 *
 * 时长按平台区分 ([platformPlayerBufferDuration]): 移动端 30 秒, 桌面端 60 秒.
 *
 * 各后端能力不同, 具体落地方式见平台实现:
 * - Android (ExoPlayer): 两个方向都按时间限制.
 * - 桌面 (mpv): 往前按时间限制 (`cache-secs`), 往后 mpv 只支持按字节限制 (`demuxer-max-back-bytes`),
 *   因此用 [MPV_MAX_BYTES_PER_DIRECTION] 近似.
 * - iOS (AVKit): 只能设置往前预读时长 (`preferredForwardBufferDuration`), 往后由系统决定.
 */
object PlayerBufferPolicy {
    val forward: Duration get() = platformPlayerBufferDuration
    val backward: Duration get() = platformPlayerBufferDuration

    /**
     * mpv 单方向的字节上限. 60 秒按约 13 Mbps 估算, 覆盖绝大多数在线源和 BT 源的码率.
     */
    const val MPV_MAX_BYTES_PER_DIRECTION: Long = 96L * 1024 * 1024
}

/** 当前平台单方向的缓冲时长. */
internal expect val platformPlayerBufferDuration: Duration
