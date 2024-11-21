/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.data

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.IOException
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.io.emptySeekableInput
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Holds information about a video file.
 */
@Stable
interface VideoData {
    val filename: String // 会显示在 UI

    /**
     * Returns the length of the video file in bytes.
     */
    val fileLength: Long // 用于匹配弹幕 (仅备选方案下), 一般用不到

    data class Stats(
        /**
         * The download speed in bytes per second.
         *
         * If this video data is not being downloaded, i.e. it is a local file,
         * the flow emits [FileSize.Unspecified].
         */
        val downloadSpeed: FileSize,

        /**
         * The upload speed in bytes per second.
         *
         * If this video data is not being uploaded, i.e. it is a local file,
         * the flow emits [FileSize.Unspecified].
         */
        val uploadRate: FileSize,
    ) {
        companion object {
            val Unspecified = Stats(FileSize.Unspecified, FileSize.Unspecified)
        }
    }

    val networkStats: Flow<Stats>
    
    val isCacheFinished: Flow<Boolean> get() = flowOf(false)

    /**
     * 支持边下边播
     */
    val supportsStreaming: Boolean get() = false

    /**
     * MD5 hash. 可以为 `null`.
     */
    @Throws(IOException::class)
    fun computeHash(): String? // 用于匹配弹幕 (仅备选方案下), 一般用不到

    /**
     * Opens a new input stream to the video file.
     * The returned [SeekableInput] needs to be closed when not used anymore.
     *
     * The returned [SeekableInput] must be closed before a new [createInput] can be made.
     * Otherwise, it is undefined behavior.
     */
    suspend fun createInput(coroutineContext: CoroutineContext = EmptyCoroutineContext): SeekableInput

    suspend fun close()
}

fun emptyVideoData(): VideoData = EmptyVideoData

private object EmptyVideoData : VideoData {
    override val filename: String get() = ""
    override val fileLength: Long get() = 0
    override val networkStats: Flow<VideoData.Stats> =
        flowOf(VideoData.Stats.Unspecified)

    override fun computeHash(): String? = null
    override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput = emptySeekableInput()
    override suspend fun close() {}
}
