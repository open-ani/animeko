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
import me.him188.ani.app.torrent.api.pieces.minOf
import me.him188.ani.utils.io.BufferedInput.Companion.DEFAULT_BUFFER_PER_DIRECTION
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.length

/**
 * @param pieces The corresponding pieces of the [file], must contain all bytes in the [file]. 不需要排序.
 * @param logicalStartOffset 逻辑上的偏移量, 也就是当 seek `k` 时, 实际上是在 `logicalStartOffset + k` 处.
 */
@Suppress("FunctionName")
@Throws(IOException::class)
expect fun TorrentInput(
    file: SystemPath,
    pieces: PieceList, // must support random access
    logicalStartOffset: Long = pieces.minOf { it.dataStartOffset }, // 默认为第一个 piece 开头
    onWait: suspend (Piece) -> Unit = { },
    bufferSize: Int = DEFAULT_BUFFER_PER_DIRECTION,
    size: Long = file.length()
): SeekableInput

@RequiresOptIn(
    "Only construct it when implement remote TorrentInput, callback `onWait` should be considered.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class RawTorrentInputConstructorParameter

class TorrentInputConstructor 
@RawTorrentInputConstructorParameter constructor(
    val file: SystemPath,
    val pieces: PieceList,
    val logicalStartOffset: Long,
    val onWait: suspend (Piece) -> Unit,
    val bufferSize: Int,
    val size: Long
)