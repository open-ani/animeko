/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.DurableBlockStore
import io.github.nihildigit.pikpak.PikPakStreamReader
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One file of a torrent on disk, as the SDK's file cache keeps it: the data file at its real
 * offsets, and a bitmap of which blocks hold real bytes.
 *
 * Everything the cache fetches is offered here, playback included, so an episode watched without
 * being cached still leaves what was played on disk until the cache engine's startup pruning
 * removes the directory; a later download skips it. What a download asks for is written here and
 * awaited ([DurableBlockStore]).
 *
 * The data file grows as blocks land, never preallocated: preallocating would put a whole episode
 * on disk for the first block a player reads. On the JVM it is created sparse, which matters on
 * NTFS, where a write past the end otherwise allocates everything in between; the index at the
 * tail is among the first blocks a player reads.
 *
 * A bit is the only evidence a block holds real bytes, and nothing downstream verifies them: a
 * wrong bit reaches the player as zeros. So a block counts as held in memory, for [read] and for
 * [missing], as soon as its write has reached the kernel, where another handle on the file reads
 * it back, and the bitmap on disk only records it after an fsync of the data file. A crash loses
 * the record of the last few seconds, never a record of bytes that are not there.
 */
internal class PikPakSparseStore(
    val dataFile: SystemPath,
    val size: Long,
    parentCoroutineContext: CoroutineContext,
    /** Called once for each block that becomes held, from whichever coroutine wrote it. */
    private val onHeld: (block: Int) -> Unit = {},
    val blockSize: Long = PikPakStreamReader.DEFAULT_BLOCK_SIZE,
    private val persistInterval: Duration = PERSIST_INTERVAL,
    private val openDataFile: (SystemPath) -> DataFile = ::openRandomAccessDataFile,
) : DurableBlockStore {
    private val logger = logger<PikPakSparseStore>()

    val blockCount: Int = ((size + blockSize - 1) / blockSize).toInt()

    private val bitmap = PieceBitmap(PieceBitmap.pathFor(dataFile), blockCount)

    private val lock = SynchronizedObject()

    private val held = BooleanArray(blockCount)

    private val _heldBytes = MutableStateFlow(0L)

    /** Bytes of the file held on disk, monotonic until [delete]. */
    val heldBytes: StateFlow<Long> = _heldBytes.asStateFlow()

    val isComplete: Boolean get() = _heldBytes.value >= size

    // One handle for reads and writes alike: a seek and the I/O after it must not interleave.
    private val ioMutex = Mutex()

    @Volatile
    private var handle: DataFile? = null

    @Volatile
    private var closed = false

    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    init {
        val onDisk = bitmap.load()
        val length = runCatching { if (dataFile.exists()) dataFile.length() else 0L }.getOrDefault(0L)
        var total = 0L
        for (block in 0 until blockCount) {
            if (!onDisk[block]) continue
            // A bit whose bytes are past the end of the file vouches for nothing
            if (endOf(block) > length) {
                bitmap.clear(block)
                continue
            }
            held[block] = true
            total += lengthOf(block)
        }
        _heldBytes.value = total
        scope.launch { persistLoop() }
    }

    fun isHeld(block: Int): Boolean = synchronized(lock) { held.getOrElse(block) { false } }

    override suspend fun read(file: String, offset: Long, length: Int): ByteArray? {
        val block = blockOf(offset) ?: return null
        if (!isHeld(block) || length.toLong() != lengthOf(block)) return null
        return withContext(Dispatchers.IO_) {
            ioMutex.withLock {
                val opened = openHandle()
                val out = ByteArray(length)
                opened.seek(offset)
                opened.readFully(out, 0, length)
                out
            }
        }
    }

    override suspend fun write(file: String, offset: Long, bytes: ByteArray) {
        val block = blockOf(offset) ?: throw IllegalArgumentException("$offset is not a block boundary of $dataFile")
        require(bytes.size.toLong() == lengthOf(block)) { "block $block is ${lengthOf(block)} bytes, got ${bytes.size}" }
        if (isHeld(block)) return
        withContext(Dispatchers.IO_) {
            ioMutex.withLock {
                val opened = openHandle()
                opened.seek(offset)
                opened.write(bytes, 0, bytes.size)
                // Held from here on: other handles on the file read these bytes back. Durability
                // rides the persist cadence.
                opened.flush()
            }
        }
        val added = synchronized(lock) {
            if (held[block]) false else {
                held[block] = true
                true
            }
        }
        if (!added) return
        bitmap.set(block)
        _heldBytes.update { it + lengthOf(block) }
        onHeld(block)
    }

    override suspend fun missing(file: String, ranges: List<LongRange>): List<LongRange> {
        val out = ArrayList<LongRange>()
        synchronized(lock) {
            for (range in ranges) {
                if (range.isEmpty()) continue
                var runStart = -1L
                val first = (range.first / blockSize).toInt()
                val last = (range.last / blockSize).toInt().coerceAtMost(blockCount - 1)
                for (block in first..last) {
                    val from = maxOf(range.first, block * blockSize)
                    if (!held[block]) {
                        if (runStart < 0) runStart = from
                    } else if (runStart >= 0) {
                        out += runStart until from
                        runStart = -1
                    }
                }
                if (runStart >= 0) out += runStart..range.last
            }
        }
        return out
    }

    /**
     * Records the whole file as held, for a file that arrived complete by other means. Written
     * through at once: nothing is in flight that an fsync would have to cover.
     */
    suspend fun markComplete() {
        synchronized(lock) { held.fill(true) }
        for (block in 0 until blockCount) bitmap.set(block)
        bitmap.flush()
        _heldBytes.value = size
    }

    /** Stops persisting, writes the bitmap one last time and closes the file. */
    suspend fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        withContext(NonCancellable) {
            try {
                persist()
            } catch (e: Throwable) {
                logger.warn(e) { "[pikpak] could not persist $dataFile on close" }
            } finally {
                closeHandle()
            }
        }
    }

    /** Removes the data file and its bitmap. The store holds nothing afterwards and cannot be reused. */
    suspend fun delete() {
        close()
        withContext(NonCancellable + Dispatchers.IO_) {
            ioMutex.withLock { if (dataFile.exists()) dataFile.delete() }
            bitmap.delete()
        }
        synchronized(lock) { held.fill(false) }
        _heldBytes.value = 0
    }

    private fun openHandle(): DataFile {
        if (closed) throw IOException("the store for $dataFile is closed")
        return handle ?: run {
            dataFile.path.parent?.inSystem?.createDirectories()
            openDataFile(dataFile).also { handle = it }
        }
    }

    private suspend fun closeHandle() = withContext(Dispatchers.IO_) {
        ioMutex.withLock {
            runCatching { handle?.close() }
            handle = null
        }
    }

    private suspend fun persistLoop() {
        while (scope.isActive) {
            delay(persistInterval)
            try {
                persist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "[pikpak] could not persist the bitmap of $dataFile" }
            }
        }
    }

    // fsync first, then the bitmap, both under the I/O lock: a write landing between the two would
    // otherwise get its bit persisted while its bytes still sit in the page cache.
    private suspend fun persist() {
        withContext(Dispatchers.IO_) {
            ioMutex.withLock {
                handle?.sync()
                bitmap.flush()
            }
        }
    }

    private fun blockOf(offset: Long): Int? {
        if (offset < 0 || offset % blockSize != 0L) return null
        return (offset / blockSize).toInt().takeIf { it < blockCount }
    }

    private fun endOf(block: Int): Long = minOf(size, (block + 1) * blockSize)

    private fun lengthOf(block: Int): Long = endOf(block) - block * blockSize

    companion object {
        // How often the data file is fsynced and the bitmap written. It bounds how much recent
        // progress a crash throws away; the bytes stay on disk, only the record of them is lost,
        // so those blocks are simply fetched again.
        val PERSIST_INTERVAL = 2.seconds
    }
}

// The store touches the data file only through this seam. Production passes a RandomAccessFile;
// a test passes a handle that tells written apart from durable, which is the distinction the
// bitmap depends on and which a real file cannot be asked about.
internal interface DataFile : AutoCloseable {
    fun seek(position: Long)

    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun write(buffer: ByteArray, offset: Int, length: Int)

    fun flush()

    fun sync()
}

internal fun DataFile.readFully(buffer: ByteArray, offset: Int, length: Int) {
    var read = 0
    while (read < length) {
        val n = read(buffer, offset + read, length - read)
        if (n < 0) throw IOException("unexpected end of the data file after $read of $length bytes")
        read += n
    }
}

internal fun openRandomAccessDataFile(file: SystemPath): DataFile =
    RandomAccessDataFile(RandomAccessFile(file, "rw"))

private class RandomAccessDataFile(private val delegate: RandomAccessFile) : DataFile {
    override fun seek(position: Long) = delegate.seek(position)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override fun write(buffer: ByteArray, offset: Int, length: Int) = delegate.write(buffer, offset, length)

    override fun flush() = delegate.flush()

    override fun sync() = delegate.sync()

    override fun close() = delegate.close()
}
