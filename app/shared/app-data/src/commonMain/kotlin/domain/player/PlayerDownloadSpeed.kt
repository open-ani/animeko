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
import me.him188.ani.utils.coroutines.sampleWithInitial
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 在线数据源速度的采样周期. 播放器每次轮询 (约 100ms) 都会更新 [NetworkStats], 直接显示会跳动过快;
 * 采样到与 BT 下载速度 (每秒更新一次) 一致的频率.
 */
val PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD: Duration = 1.seconds

/**
 * 当前播放的媒体的下载速度, 用于缓冲提示等 UI.
 *
 * - BT 等由应用自行下载的媒体 ([DownloadingMediaData]): 取其 [DownloadingMediaData.networkStats].
 * - 在线数据源 ([UriMediaData], 由播放器自行下载): 取播放器的 [NetworkStats] 特性,
 *   按 [PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD] 采样; 首个值立即发出, 之后每个周期最多更新一次.
 * - 其他 (如本地文件), 或播放器不支持 [NetworkStats], 或速度未知: [FileSize.Unspecified].
 */
@OptIn(ExperimentalMediampApi::class)
fun MediampPlayer.downloadSpeedFlow(): Flow<FileSize> = mediaData.flatMapLatest { data ->
    when (data) {
        is DownloadingMediaData -> data.networkStats.map { it.downloadSpeed.toFileSizeOrUnspecified() }
        is UriMediaData -> features[NetworkStats]?.downloadSpeedBytesPerSecond
            ?.sampleWithInitial(PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD)
            ?.map { it.toFileSizeOrUnspecified() }
            ?: flowOf(FileSize.Unspecified)

        else -> flowOf(FileSize.Unspecified)
    }
}

/**
 * 字节每秒转换为 [FileSize]; 负数表示未知, 转换为 [FileSize.Unspecified].
 */
private fun Long.toFileSizeOrUnspecified(): FileSize = if (this < 0) FileSize.Unspecified else this.bytes
