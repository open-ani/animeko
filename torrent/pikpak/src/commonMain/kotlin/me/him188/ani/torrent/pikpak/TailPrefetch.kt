/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakStreamReader
import io.github.nihildigit.pikpak.RangeSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

// A demuxer cannot build a seek table without the container's index, and mkv writes that index last:
// the player reads the header, jumps to the end for the Cues, then jumps back, all before the first
// frame. Both jumps are free on a local file and cost a round trip each here, so fetch the tail
// while the header is being read and serve those reads from memory.
//
// Deliberately not routed through PikPakStreamReader: its read-ahead only claims blocks inside the
// window around the read position, and moving that position with seekTo would cancel the header
// fetches already in flight — the very cost this is meant to remove.
internal class TailPrefetch(
    private val name: String,
    private val source: RangeSource,
    size: Long,
    parentCoroutineContext: CoroutineContext,
    tailBytes: Long = TAIL_BYTES,
    private val blockSize: Long = BLOCK_SIZE,
    concurrency: Int = CONCURRENCY,
) {
    private val logger = logger<TailPrefetch>()

    private val lock = SynchronizedObject()

    /** First offset this holds. Reads below it are none of its business. */
    val start: Long = (size - tailBytes).coerceAtLeast(0)

    private val end: Long = size

    private val blocks = arrayOfNulls<ByteArray>(((end - start + blockSize - 1) / blockSize).toInt())

    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    init {
        // Container indexes commonly end at EOF, so fetch backwards to make the final bytes
        // available first.
        val pending = ArrayDeque(blocks.indices.reversed().toList())
        repeat(concurrency) {
            scope.launch {
                while (true) {
                    val index = synchronized(lock) { pending.removeFirstOrNull() } ?: break
                    val offset = start + index * blockSize
                    val length = minOf(blockSize, end - offset)
                    try {
                        val bytes = source.readBytes(offset, length, PREFETCH_PRIORITY)
                        synchronized(lock) { blocks[index] = bytes }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // The read path falls back to the stream, so a failure here costs nothing
                        // beyond the round trip it already spent.
                        logger.warn(e) { "[$name] tail prefetch stopped at $offset" }
                        break
                    }
                }
            }
        }
    }

    /**
     * Copies what it already holds for [from], or returns 0 when it holds nothing there yet.
     *
     * Never waits: a block that has not arrived is served by the stream instead, so a slow prefetch
     * can only cost bandwidth, never latency.
     */
    fun read(from: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (from < start || from >= end) return 0
        val index = ((from - start) / blockSize).toInt()
        val block = synchronized(lock) { blocks.getOrNull(index) } ?: return 0
        val inBlock = (from - (start + index * blockSize)).toInt()
        if (inBlock >= block.size) return 0
        val count = minOf(length, block.size - inBlock)
        block.copyInto(buffer, offset, inBlock, inBlock + count)
        return count
    }

    /**
     * How many contiguous bytes it already holds from [from], 0 when it holds nothing there.
     *
     * [read] cannot answer this: it stops at a block boundary even when the next block has arrived,
     * and a caller filling a fixed-size window has to know the length before it starts copying.
     */
    fun availableFrom(from: Long): Long {
        if (from < start || from >= end) return 0
        return synchronized(lock) {
            var at = from
            while (at < end) {
                val index = ((at - start) / blockSize).toInt()
                val block = blocks.getOrNull(index) ?: break
                val inBlock = (at - (start + index * blockSize)).toInt()
                if (inBlock >= block.size) break
                at += block.size - inBlock
            }
            at - from
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        // Keep the high-priority prefetch small: it should cover a typical trailing index without
        // delaying the header read that lets the demuxer discover that index.
        const val TAIL_BYTES: Long = 512L * 1024

        const val BLOCK_SIZE: Long = 256L * 1024

        // Use two of the file's eight connection slots so other playback reads retain capacity.
        const val CONCURRENCY = 2

        // Ranking above the playback head looks wrong and is not: media3 renders no first frame
        // until the demuxer reports a seek table, and mkv writes the Cues after the clusters, so
        // before the Cues arrive there is no playback position for anything to be blocked at. What
        // this outranks during those seconds is the read for a frame that cannot be shown yet.
        //
        // Missing blocks never delay [read]; they fall through to the stream. The higher priority
        // lets the bounded tail window finish before the demuxer performs its end-of-file seek.
        const val PREFETCH_PRIORITY = PikPakStreamReader.BLOCKING_PRIORITY + 1
    }
}
