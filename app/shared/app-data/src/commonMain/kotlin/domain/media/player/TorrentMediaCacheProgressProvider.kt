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
import androidx.collection.MutableFloatList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.count
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.app.torrent.api.pieces.forEachIndexed
import me.him188.ani.app.torrent.api.pieces.isEmpty
import me.him188.ani.app.torrent.api.pieces.mapTo
import me.him188.ani.app.torrent.api.pieces.sumOf
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.time.Duration.Companion.seconds

class TorrentMediaCacheProgressProvider(
    private val pieces: PieceList,
    flowContext: CoroutineContext = Dispatchers.IO_,
) : MediaCacheProgressProvider {
    private val chunkStates = pieces.mapTo(ArrayList(pieces.count)) { piece ->
        piece.state.toChunkState()
    }

    private val chunkWeights: FloatList = MutableFloatList(pieces.count).apply {
        val totalSize = pieces.sumOf { it.size }
        pieces.forEach { piece ->
            add(piece.size.toFloat() / totalSize.toFloat())
        }
    }

    override val flow: Flow<MediaCacheProgressInfo> = flow {
        if (pieces.isEmpty()) {
            emit(MediaCacheProgressInfo.Empty)
            return@flow
        }

        while (true) {
            val passResult = runPass()

            if (passResult.anyChanged) {
                emit(createInfo())
            }
            if (passResult.allFinished) {
                // It must also be `anyChanged`, so there is already an emission.
                break
            }

            delay(1.seconds)
        }
    }.shareIn(CoroutineScope(flowContext), SharingStarted.WhileSubscribed(), replay = 1)

    fun createInfo() = MediaCacheProgressInfo(
        chunkWeights = chunkWeights,
        chunkStates = chunkStates,
    )

    /**
     * Updates [chunkStates] and returns whether any chunk has changed.
     */
    fun runPass(): PassResult {
        // 集中算有哪些 piece 更新了. piece 数量庞大, 为它们分别启动一个协程或分配一个 state 是会有性能问题的
        var anyChanged = false
        var allFinished = true

        pieces.forEachIndexed { index, item ->
            val chunkState = item.state.toChunkState()
            if (chunkState != chunkStates[index]) {
                // 变了
                anyChanged = true

                // Mutating an element is OK (won't cause ConcurrentModificationException), 
                // as long as we don't change the size of the list
                chunkStates[index] = chunkState
            }
            if (chunkState != ChunkState.DONE) {
                allFinished = false
            }
        }

        return PassResult(anyChanged, allFinished)
    }
}

@JvmInline
value class PassResult private constructor(
    private val value: Int,
) {
    constructor(anyChanged: Boolean, allFinished: Boolean) : this(
        (if (anyChanged) 1 else 0) or (if (allFinished) (1 shl 1) else 0),
    )

    /**
     * Any chunk (state) has changed
     */
    val anyChanged: Boolean
        get() = (value and 1) == 1

    /**
     * All chunks have finished
     */
    val allFinished: Boolean
        get() = (value and (1 shl 1)) == (1 shl 1)
}

private fun PieceState.toChunkState(): ChunkState = when (this) {
    PieceState.READY -> ChunkState.NONE
    PieceState.DOWNLOADING -> ChunkState.DOWNLOADING
    PieceState.FINISHED -> ChunkState.DONE
    PieceState.NOT_AVAILABLE -> ChunkState.NOT_AVAILABLE
}
