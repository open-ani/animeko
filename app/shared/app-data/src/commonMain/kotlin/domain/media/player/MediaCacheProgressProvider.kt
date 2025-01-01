/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player

import androidx.collection.FloatList
import androidx.collection.floatListOf
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 视频播放器进度条的缓存进度
 */
interface MediaCacheProgressProvider {
    val flow: Flow<MediaCacheProgressInfo>
}

@Immutable
data class MediaCacheProgressInfo(
    /**
     * 区块的权重列表. 每个区块的宽度由权重决定.
     *
     * 所有 chunks 的 weight 之和应当 (约) 等于 1, 否则将会导致绘制超出进度条的区域 (即会被忽略).
     */
    val chunkWeights: FloatList,
    /**
     * 区块的状态列表. [chunkStates] 和 [chunkWeights] 的长度应当相等.
     */
    val chunkStates: List<ChunkState>,
) {
    init {
        require(chunkWeights.size == chunkStates.size) {
            "chunkWeights.size (${chunkWeights.size}) != chunkStates.size (${chunkStates.size})"
        }
    }

    companion object {
        val Empty = MediaCacheProgressInfo(
            chunkWeights = floatListOf(),
            chunkStates = listOf(),
        )
    }

    val size = chunkWeights.size
    val lastIndex get() = chunkWeights.size - 1
    fun isEmpty(): Boolean = chunkWeights.isEmpty()
}

enum class ChunkState {
    /**
     * 初始状态
     */
    NONE,

    /**
     * 正在下载
     */
    DOWNLOADING,

    /**
     * 下载完成
     */
    DONE,

    /**
     * 对应 BT 的没有任何 peer 有这个 piece 的状态
     */
    NOT_AVAILABLE
}

private val StaticMediaCacheProgressStateNone = StaticMediaCacheProgressProvider(ChunkState.NONE)
private val StaticMediaCacheProgressStateDone = StaticMediaCacheProgressProvider(ChunkState.DONE)

fun staticMediaCacheProgressState(
    chunkState: ChunkState
): MediaCacheProgressProvider {
    if (chunkState == ChunkState.NONE) return StaticMediaCacheProgressStateNone
    if (chunkState == ChunkState.DONE) return StaticMediaCacheProgressStateDone
    return StaticMediaCacheProgressProvider(chunkState)
}

private class StaticMediaCacheProgressProvider(chunkState: ChunkState) : MediaCacheProgressProvider {
    override val flow: Flow<MediaCacheProgressInfo> = flowOf(
        MediaCacheProgressInfo(
            chunkWeights = floatListOf(1f),
            chunkStates = listOf(chunkState),
        ),
    )
}
