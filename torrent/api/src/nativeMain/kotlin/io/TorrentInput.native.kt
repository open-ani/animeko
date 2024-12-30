/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.io

import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.utils.io.SystemPath
import org.openani.mediamp.io.SeekableInput
import kotlin.coroutines.CoroutineContext

/**
 * @param pieces The corresponding pieces of the [file], must contain all bytes in the [file]. 不需要排序.
 * @param logicalStartOffset 逻辑上的偏移量, 也就是当 seek `k` 时, 实际上是在 `logicalStartOffset + k` 处.
 */
@Suppress("FunctionName")
@Throws(IOException::class)
actual fun TorrentInput(
    file: SystemPath,
    pieces: PieceList,
    logicalStartOffset: Long,
    onWait: suspend (Piece) -> Unit,
    bufferSize: Int,
    size: Long,
    awaitCoroutineContext: CoroutineContext,
): SeekableInput {
    TODO("Not yet implemented")
}