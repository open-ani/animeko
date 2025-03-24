/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import me.him188.ani.app.domain.media.resolver.MediaSourceOpenException
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.SeekableInputMediaData
import kotlin.coroutines.cancellation.CancellationException

/**
 * [MediaDataProvider]s are stateless: They only represent a location of the resource, not holding file descriptors or network connections, etc.
 *
 * ## Obtaining data stream
 *
 * To get the input stream of the video file, two steps are needed:
 * 1. Open a [MediaData] using [open].
 * 2. Use [SeekableInputMediaData.createInput] to get the input stream [SeekableInput].
 *
 * Note that both [MediaData] and [SeekableInput] are [AutoCloseable] and needs to be properly closed.
 *
 * In the BitTorrent scenario, [MediaDataProvider.open] is to resolve magnet links, and to download the torrent metadata file.
 * [SeekableInputMediaData.createInput] is to start downloading the actual video file.
 * Though the actual implementation might start downloading very soon (e.g. when [MediaDataProvider] is just created), so that
 * the video buffers more soon.
 *
 * @param S type of the stream
 */
interface MediaDataProvider<out S : MediaData> {
    val extraFiles: MediaExtraFiles

    /**
     * Opens the underlying video data.
     *
     * Note that [S] should be closed by the caller.
     *
     * Repeat calls to this function may return different instances so it may be desirable to store the result.
     *
     * @param scopeForCleanup a [CoroutineScope] to use for cleanup tasks.
     * When the media data [S] is closed, a job might be launched in this scope with [NonCancellable] to clean up resources.
     * Proper [CoroutineExceptionHandler] must be set to handle exceptions.
     *
     * @throws MediaSourceOpenException 当打开失败时抛出, 包含原因
     */
    @Throws(MediaSourceOpenException::class, CancellationException::class)
    suspend fun open(scopeForCleanup: CoroutineScope): S
}
