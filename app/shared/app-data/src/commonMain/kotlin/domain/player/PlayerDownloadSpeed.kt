/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.media.player.data.DownloadingMediaData
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData

/**
 * 当前播放的媒体的下载速度, 用于缓冲提示等 UI.
 *
 * - BT 等由应用自行下载的媒体 ([DownloadingMediaData]): 取其 [DownloadingMediaData.networkStats].
 * - 在线数据源 ([UriMediaData], 由播放器自行下载): 取播放器的 [NetworkStats] 特性.
 * - 其他 (如本地文件), 或播放器不支持 [NetworkStats], 或速度未知: [FileSize.Unspecified].
 */
@OptIn(ExperimentalMediampApi::class)
fun MediampPlayer.downloadSpeedFlow(): Flow<FileSize> = mediaData.flatMapLatest { data ->
    when (data) {
        is DownloadingMediaData -> data.networkStats.map { it.downloadSpeed.toFileSizeOrUnspecified() }
        is UriMediaData -> features[NetworkStats]?.downloadSpeedBytesPerSecond
            ?.map { it.toFileSizeOrUnspecified() }
            ?: flowOf(FileSize.Unspecified)

        else -> flowOf(FileSize.Unspecified)
    }
}

/**
 * 字节每秒转换为 [FileSize]; 负数表示未知, 转换为 [FileSize.Unspecified].
 */
private fun Long.toFileSizeOrUnspecified(): FileSize = if (this < 0) FileSize.Unspecified else this.bytes
