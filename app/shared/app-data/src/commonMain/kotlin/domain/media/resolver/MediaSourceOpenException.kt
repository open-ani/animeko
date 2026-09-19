/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.app.domain.media.player.data.MediaDataProvider

/**
 * @see MediaDataProvider.open
 * @see MediaSourceOpenException
 */
enum class OpenFailures {
    /**
     * 未找到符合剧集描述的文件
     */
    NO_MATCHING_FILE,

    /**
     * 视频资源没问题, 但播放器不支持该资源. 例如尝试用一个不支持边下边播的播放器 (例如桌面端的 vlcj) 来播放种子视频 `TorrentVideoSource`.
     */
    UNSUPPORTED_VIDEO_SOURCE,

    /**
     * TorrentEngine 等被关闭.
     *
     * 这个错误实际上不太会发生, 因为当引擎关闭时会跳过使用该引擎的 `VideoSourceResolver`, 也就不会产生依赖该引擎的 [MediaDataProvider].
     * 只有在得到 [MediaDataProvider] 后引擎关闭 (用户去设置中关闭) 才会发生.
     */
    ENGINE_DISABLED,
}

class MediaSourceOpenException(
    val reason: OpenFailures,
    extraMessage: String = "",
    override val cause: Throwable? = null,
    /**
     * [OpenFailures.NO_MATCHING_FILE] 时资源里可供选择的文件, 元素是
     * [me.him188.ani.app.torrent.api.files.TorrentFileEntry.pathInTorrent].
     *
     * 只放视频文件, 且顺序与匹配时的候选顺序一致: 这是一份「让用户挑一个来播」的清单, 字幕和
     * 说明文件放进去只会让人在几十行里找视频. 其他失败原因下为空.
     */
    val filesInTorrent: List<String> = emptyList(),
) : Exception("Failed to open video due to $reason. $extraMessage", cause)
