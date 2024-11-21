/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.videoplayer.data.VideoData
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.utils.io.SeekableInput
import kotlin.coroutines.CoroutineContext

class HttpStreamingVideoSource(
    override val uri: String,
    private val filename: String,
    val webVideo: WebVideo,
    override val extraFiles: MediaExtraFiles,
) : VideoSource<HttpStreamingVideoData> {
    override suspend fun open(): HttpStreamingVideoData {
        return HttpStreamingVideoData(uri, filename)
    }

    override fun toString(): String {
        return "HttpStreamingVideoSource(webVideo=$webVideo, filename='$filename')"
    }
}


class HttpStreamingVideoData(
    val url: String,
    override val filename: String
) : VideoData {
    override val fileLength: Long = 0
    override val networkStats: Flow<VideoData.Stats> = flowOf(VideoData.Stats.Unspecified)

    override val supportsStreaming: Boolean get() = true

    override fun computeHash(): String? = null

    override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput {
        throw UnsupportedOperationException()
    }

    override suspend fun close() {
    }
}