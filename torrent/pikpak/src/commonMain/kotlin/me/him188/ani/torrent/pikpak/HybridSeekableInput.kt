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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.app.torrent.io.readFully
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.io.BufferedSeekableInput
import org.openani.mediamp.io.SeekableInput
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// Read completed pieces locally and stream gaps without creating a playback cache.
// Lazy cloud access keeps fully imported files playable without credentials or a GCID.
//
// Buffer player reads because media3 may traverse a container a few bytes at a time. Filling a
// window amortizes coroutine, file-system and synchronization overhead across those small reads.
internal class HybridSeekableInput(
    private val name: String,
    private val dataPath: SystemPath,
    private val pieces: PieceList,
    override val size: Long,
    private val openStream: () -> CloudStream,
    private val onDelivered: (delta: Long) -> Unit,
    private val onCloudReadStarted: () -> Unit,
    private val onCloudReadFinished: () -> Unit,
    private val onClosed: () -> Unit = {},
    private val tail: TailPrefetch? = null,
    idleCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val cloudIdleTimeout: Duration = CLOUD_IDLE_TIMEOUT,
    /**
     * Whether the Stream this input was opened on is still the entry's.
     *
     * deleteFiles replaces it outright, and nothing stops that happening mid-read: an input is not
     * a TorrentFileHandle, so the entry's handle count does not know it exists. What is left over
     * is not corrupt -- the delete resets that same piece list to READY, so no disk read is even
     * attempted, and the cloud source still holds the same bytes -- so this is not about splicing.
     * It refuses to keep serving a file the user asked to remove, and refuses in the one type the
     * player treats as recoverable.
     *
     * It does not cover the window inside deleteTarget, between the unlink and the piece reset,
     * because the Stream is not replaced until that returns. Nothing needs it to: choosing the disk
     * needs dataFileExists, whose memo only caches a positive, so after the unlink either the file
     * was already open -- and an open handle outlives the unlink -- or the stat now fails and the
     * disk is not chosen at all. On Windows the unlink is refused outright while a handle is open.
     */
    private val isStale: () -> Boolean = { false },
    bufferSize: Int = BUFFER_PER_DIRECTION,
) : BufferedSeekableInput(bufferSize) {
    private val logger = logger<HybridSeekableInput>()

    // The base class keeps bufferSize only to size its array; the fill policy needs the number too.
    private val window: Long = bufferSize.toLong()

    private val lock = SynchronizedObject()

    private val idleScope = CoroutineScope(
        idleCoroutineContext + SupervisorJob(idleCoroutineContext[Job]),
    )

    private var file: RandomAccessFile? = null
    private var stream: CloudStream? = null
    private var activeCloudReads = 0
    private var idleCloseJob: Job? = null
    private var idleGeneration = 0L
    private var reportedDelivered = 0L
    private var dataFileSeen = false
    private var tailServed = false

    // Not the base class's own closed flag; see close().
    private var released = false

    // fillBuffer picks one source and readFileToBuffer has to honour it, but the seam between them
    // carries only a file offset -- and the base class, not this class, decides how many calls that
    // becomes. A field works because the two run on one thread inside a single fillBuffer call.
    private var fillSource = Source.STREAM

    private enum class Source { DISK, TAIL, STREAM }

    init {
        require(pieces.sizes.isEmpty() || pieces.sizes.dropLast(1).all { it == pieces.sizes[0] }) {
            "HybridSeekableInput needs a uniformly sized piece list"
        }
    }

    private fun dataFileExists(): Boolean = synchronized(lock) {
        if (dataFileSeen) true else dataPath.exists().also { dataFileSeen = it }
    }

    /**
     * A window can only be filled from one source.
     *
     * readFileToBuffer is handed a file offset and a length and nothing else, and the base class
     * splits the window into up to two of those calls on its own, so a range that starts on disk
     * and ends in the cloud has no seam where the change of source could be noticed. The choice is
     * therefore made here, once, and the window is clipped to where that source stops.
     */
    override fun fillBuffer() {
        if (isStale()) {
            bufferedOffsetStart = -1L
            throw IOException("[$name] the stream this input was opened on was replaced")
        }
        val pos = position
        val forwardLimit = minOf(size, pos + window)
        val backwardLimit = (pos - window).coerceAtLeast(0)

        val diskEnd = finishedRunEnd(pos, forwardLimit)
        if (diskEnd > pos) {
            fillFrom(Source.DISK, finishedRunStart(pos, backwardLimit), diskEnd)
            return
        }

        val tail = this.tail
        if (tail != null && pos >= tail.start) {
            val available = tail.availableFrom(pos)
            if (available > 0) {
                val end = minOf(forwardLimit, pos + available)
                // Extending backwards is only worth it while the same source covers it, and the
                // tail knows nothing below its own start.
                val wantedStart = backwardLimit.coerceAtLeast(tail.start)
                val start = if (tail.availableFrom(wantedStart) >= end - wantedStart) wantedStart else pos
                if (!tailServed) {
                    tailServed = true
                    logger.info { "[$name] index read at $pos served from the prefetched tail" }
                }
                fillFrom(Source.TAIL, start, end)
                return
            }
        }

        // Never backwards on the stream: those bytes are behind the playback head, and asking for
        // them is a range request for something the player has already consumed.
        fillFrom(Source.STREAM, pos, minOf(forwardLimit, nextBetterSourceStart(pos, forwardLimit)))
    }

    private fun fillFrom(source: Source, start: Long, end: Long) {
        fillSource = source
        // The base class may satisfy part of this window by shifting what the previous fill left in
        // the buffer, and that previous fill may have come from another source. Sound, because the
        // three sources hand back the same file content for the same offset -- the fetcher rejects
        // a short read and the bitmap only records a piece whose bytes are already on the medium.
        // The one thing that would break it is the bytes changing under us, which isStale rejects.
        fillBufferRange(start, end)
    }

    override fun readFileToBuffer(fileOffset: Long, bufferOffset: Int, length: Int): Int {
        if (length == 0) return 0
        return when (fillSource) {
            Source.DISK -> readDiskToBuffer(fileOffset, bufferOffset, length)
            Source.TAIL -> readTailToBuffer(fileOffset, bufferOffset, length)
            Source.STREAM -> readStreamToBuffer(fileOffset, bufferOffset, length)
        }
    }

    // One shared handle: the seek and the read that follows must be serialized, and close must not
    // land between them.
    private fun readDiskToBuffer(fileOffset: Long, bufferOffset: Int, length: Int): Int = synchronized(lock) {
        if (released) throw IOException("[$name] closed while filling from the data file")
        val handle = file ?: RandomAccessFile(dataPath, "r").also { file = it }
        handle.seek(fileOffset)
        handle.readFully(buf, bufferOffset, length)
        length
    }

    private fun readTailToBuffer(fileOffset: Long, bufferOffset: Int, length: Int): Int {
        val tail = this.tail ?: throw IOException("[$name] no prefetched tail to fill from")
        var filled = 0
        while (filled < length) {
            // TailPrefetch.read stops at a block boundary even when the next block is there, so a
            // window straddling one takes more than a single call. It cannot spin: fillBuffer only
            // chose this source for a range availableFrom had already vouched for.
            val read = tail.read(fileOffset + filled, buf, bufferOffset + filled, length - filled)
            if (read <= 0) throw IOException("[$name] the prefetched tail lost ${fileOffset + filled}")
            filled += read
        }
        return filled
    }

    // Outside the lock: it blocks on the network, and close is what aborts it.
    private fun readStreamToBuffer(fileOffset: Long, bufferOffset: Int, length: Int): Int {
        val reader = synchronized(lock) {
            if (released) throw IOException("[$name] closed while filling from the cloud")
            idleGeneration++
            idleCloseJob?.cancel()
            idleCloseJob = null
            val opened = stream ?: openStream().also {
                // Registration must precede publication so close cannot overtake it.
                onCloudReadStarted()
                reportedDelivered = 0L
                stream = it
                logger.info { "[$name] cloud stream opened at $fileOffset" }
            }
            activeCloudReads++
            opened
        }
        // The reader now tracks the fill front rather than the playback head, and the front runs
        // ahead of the player by up to a window. That is also why it is seeked far less often: once
        // per window instead of once per player read, which is what the SDK's read-ahead wants.
        var filled = 0
        try {
            // Inside the try: a seek can fail like a read (the player interrupts it, the network
            // drops), and a failure here that skipped cloudReadFinished would leave activeCloudReads
            // raised for good, with the idle close never firing again on this input.
            if (reader.position != fileOffset) {
                val jump = fileOffset - reader.position
                reader.seekTo(fileOffset)
                if (jump > FAR_SEEK || jump < -FAR_SEEK) {
                    logger.info { "[$name] stream seek $jump bytes to $fileOffset" }
                }
            }
            while (filled < length) {
                val read = reader.read(buf, bufferOffset + filled, length - filled)
                if (read <= 0) {
                    throw IOException("[$name] the cloud stream ended at ${fileOffset + filled}, $length wanted")
                }
                filled += read
            }
        } finally {
            publishDelivered()
            cloudReadFinished(reader)
        }
        return filled
    }

    private fun cloudReadFinished(reader: CloudStream) = synchronized(lock) {
        activeCloudReads = (activeCloudReads - 1).coerceAtLeast(0)
        if (released || activeCloudReads != 0 || stream !== reader) return@synchronized

        val generation = ++idleGeneration
        idleCloseJob = idleScope.launch {
            delay(cloudIdleTimeout)
            closeIdleStream(reader, generation)
        }
    }

    private fun closeIdleStream(reader: CloudStream, generation: Long) {
        val delivered = synchronized(lock) {
            if (released || activeCloudReads != 0 || stream !== reader || idleGeneration != generation) return
            idleCloseJob = null
            stream = null
            deliveredDelta(reader)
        }
        if (delivered > 0) onDelivered(delivered)
        runCatching { reader.close() }
        onCloudReadFinished()
        logger.info { "[$name] cloud stream closed after $cloudIdleTimeout idle" }
    }

    /** End of the run of finished pieces starting at [from], or [from] itself when there is none. */
    private fun finishedRunEnd(from: Long, limit: Long): Long = with(pieces) {
        if (pieces.sizes.isEmpty() || !dataFileExists()) return from
        var end = from
        while (end < limit) {
            val index = (end / pieces.sizes[0]).toInt()
            if (index >= pieces.sizes.size) break
            val piece = pieces.createPieceByListIndexUnsafe(index)
            if (piece.state != PieceState.FINISHED) break
            end = piece.dataEndOffset
        }
        return end.coerceAtMost(limit)
    }

    /** Start of that same run, walking back no further than [limit]. */
    private fun finishedRunStart(from: Long, limit: Long): Long = with(pieces) {
        if (pieces.sizes.isEmpty()) return from
        var start = from
        while (start > limit) {
            val index = ((start - 1) / pieces.sizes[0]).toInt()
            if (index < 0 || index >= pieces.sizes.size) break
            val piece = pieces.createPieceByListIndexUnsafe(index)
            if (piece.state != PieceState.FINISHED) break
            start = piece.dataStartOffset
        }
        return start.coerceAtLeast(limit)
    }

    /**
     * Where the stream should stop because something cheaper takes over: the prefetched tail, or
     * the next finished piece. Reading past it would spend a range request on bytes already held.
     */
    private fun nextBetterSourceStart(from: Long, limit: Long): Long = with(pieces) {
        var boundary = limit
        val tail = this@HybridSeekableInput.tail
        if (tail != null && from < tail.start) boundary = minOf(boundary, tail.start)
        if (pieces.sizes.isEmpty() || !dataFileExists()) return boundary
        var index = (from / pieces.sizes[0]).toInt() + 1
        var start = index * pieces.sizes[0]
        while (start < boundary && index < pieces.sizes.size) {
            if (pieces.createPieceByListIndexUnsafe(index).state == PieceState.FINISHED) return start
            index++
            start = index * pieces.sizes[0]
        }
        return boundary
    }

    private fun publishDelivered() {
        val added = synchronized(lock) {
            val reader = stream ?: return
            deliveredDelta(reader)
        }
        if (added > 0) onDelivered(added)
    }

    private fun deliveredDelta(reader: CloudStream): Long {
        val now = reader.deliveredBytes
        val delta = now - reportedDelivered
        if (delta > 0) reportedDelivered = now
        return delta.coerceAtLeast(0)
    }

    /**
     * Deliberately does not raise the base class's own closed flag.
     *
     * Its read() tests that flag before it even looks at the buffer and answers with
     * IllegalStateException, which ExoPlayer's Loader treats as a fatal load error rather than the
     * cancellation it is -- the one distinction this whole read path is built around. Leaving it
     * down lets a read that lands in the buffer keep answering with the bytes it already holds,
     * which are still the right bytes for that offset, while anything that needs a refill gets an
     * IOException out of fillBuffer. Every resource is released here regardless.
     */
    override fun close() {
        publishDelivered()
        val (openFile, openReader) = synchronized(lock) {
            if (released) return
            released = true
            idleGeneration++
            idleCloseJob?.cancel()
            idleCloseJob = null
            (file to stream).also {
                file = null
                stream = null
            }
        }
        idleScope.cancel()
        runCatching { openFile?.close() }
        onClosed()
        // tail 归 entry 所有, 由它在换变体和关闭时释放: 同一个文件的多个 input 共用一份.
        if (openReader != null) {
            runCatching { openReader.close() }
            onCloudReadFinished()
        }
    }

    companion object {
        // Below the SDK's read-ahead window a jump costs nothing; above it the stream refills.
        const val FAR_SEEK = 8L * 1024 * 1024

        // Match TorrentInput's buffer. It is small enough to keep cold seeks responsive and divides
        // both the piece and tail-prefetch block sizes, so a fill crosses at most one boundary.
        const val BUFFER_PER_DIRECTION = 64 * 1024

        val CLOUD_IDLE_TIMEOUT = 5.seconds
    }
}

/**
 * What [HybridSeekableInput] needs of a cloud byte stream.
 *
 * Exists so a test can count the reads that actually reach the cloud, which is the property the
 * buffering is there for and which asserting on the bytes read back cannot tell apart.
 * [deliveredBytes] is the one thing SeekableInput does not already carry.
 */
internal interface CloudStream : SeekableInput {
    val deliveredBytes: Long
}

// Player reads block; SDK workers must run on a separate dispatcher from these runBlocking calls.
internal class StreamSeekableInput(
    private val name: String,
    private val reader: PikPakStreamReader,
    private val retryDelay: Duration = READ_RETRY_DELAY,
    private val retryWindow: Duration = READ_RETRY_WINDOW,
) : CloudStream {
    private val logger = logger<StreamSeekableInput>()

    @Volatile
    private var closed = false

    override val size: Long get() = reader.size

    override val position: Long get() = reader.position

    override val bytesRemaining: Long get() = reader.bytesRemaining

    override val deliveredBytes: Long get() = reader.deliveredBytes

    override fun seekTo(position: Long) {
        require(position >= 0) { "position must be >= 0, got $position" }
        asIoFailure { runBlockingInterruptible { reader.seekTo(position) } }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        readRidingOutFailures(buffer, offset, length)

    // PieceFetcher and startFetching retry the same way, but they are background loops where a 30s
    // delay costs nothing. This one sits on the player's blocking read: the network returns a second
    // or two after screen-on while the player gives up after about three, which is why a failure
    // there ends playback by luck. Retry briefly and for a bounded stretch, so a link that is really
    // gone still reaches the player as a failure.
    private fun readRidingOutFailures(buffer: ByteArray, offset: Int, length: Int): Int {
        val since = TimeSource.Monotonic.markNow()
        while (true) {
            try {
                return asIoFailure { runBlockingInterruptible { reader.read(buffer, offset, length) } }
            } catch (e: Throwable) {
                // Closing is how playback is torn down, and the reader reports it as a failure like
                // any other. Retrying it would spin for the whole window on every episode switch.
                if (closed || e is CancellationException || e.isReadInterruption()) throw e
                val waited = since.elapsedNow()
                if (waited >= retryWindow) {
                    logger.warn(e) { "[$name] read at ${reader.position} failed, gave up after $waited" }
                    throw e
                }
                logger.warn(e) { "[$name] read at ${reader.position} failed, retrying in $retryDelay" }
                runBlockingInterruptible { delay(retryDelay) }
            }
        }
    }

    override fun close() {
        closed = true
        reader.close()
    }

    private companion object {
        val READ_RETRY_DELAY = 500.milliseconds
        val READ_RETRY_WINDOW = 10.seconds
    }
}

// The SDK reports every failure as PikPakException, which is a RuntimeException. ExoPlayer's Loader
// retries an IOException but treats anything else as fatal, so the DNS lookup that fails in the
// second between screen-on and the network coming back ends playback with "播放失败" instead of
// resuming. What the player does with it is its policy to decide; the type must let it decide.
private inline fun <T> asIoFailure(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e)
    }
