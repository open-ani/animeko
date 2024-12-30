/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:androidx.annotation.OptIn(UnstableApi::class)

package me.him188.ani.app.videoplayer.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaData
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * Wrap of an Ani [VideoData] into a ExoPlayer [DataSource].
 *
 * This class will not close [videoData].
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoDataDataSource(
    private val videoData: MediaData,
    private val file: SeekableInput,
) : BaseDataSource(true) {
    private companion object {
        @JvmStatic
        private val logger = logger<VideoDataDataSource>()
        private const val ENABLE_READ_LOG = false
        private const val ENABLE_TRACE_LOG = false
    }

    private var uri: Uri? = null

    private var opened = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // 性能提示: 这个函数会被非常频繁调用 (一个 byte 一次), 速度会直接影响视频首帧延迟

        if (length == 0) return 0

        if (ENABLE_READ_LOG) { // const val, optimized out
            logger.warn { "VideoDataDataSource read: offset=$offset, length=$length" }
        }

        val bytesRead = if (ENABLE_READ_LOG) {
            val (value, time) = measureTimedValue {
                file.read(buffer, offset, length)
            }
            if (time > 100.milliseconds) {
                logger.warn { "VideoDataDataSource slow read: read $offset for length $length took $time" }
            }
            value
        } else {
            file.read(buffer, offset, length)
        }
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        if (ENABLE_TRACE_LOG) logger.info { "Opening dataSpec, offset=${dataSpec.position}, length=${dataSpec.length}, videoData=$videoData" }

        val uri = dataSpec.uri
        if (opened && dataSpec.uri == this.uri) {
            if (ENABLE_TRACE_LOG) logger.info { "Double open, will not start download." }
        } else {
            this.uri = uri
            transferInitializing(dataSpec)
            opened = true
        }

        val torrentLength = videoData.fileLength() ?: 0

        if (ENABLE_TRACE_LOG) logger.info { "torrentLength = $torrentLength" }

        if (dataSpec.position >= torrentLength) {
            if (ENABLE_TRACE_LOG) logger.info { "dataSpec.position ${dataSpec.position} > torrentLength $torrentLength" }
        } else {
            if (dataSpec.position != -1L && dataSpec.position != 0L) {
                if (ENABLE_TRACE_LOG) logger.info { "Seeking to ${dataSpec.position}" }
                runBlocking { file.seek(dataSpec.position) }
            }

            if (ENABLE_TRACE_LOG) logger.info { "Open done, bytesRemaining = ${file.bytesRemaining}" }
        }

        transferStarted(dataSpec)
        return file.bytesRemaining
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (ENABLE_TRACE_LOG) logger.info { "Closing VideoDataDataSource" }
        uri = null
        if (opened) {
            transferEnded()
        }
    }
}