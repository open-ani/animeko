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
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.logging.info
import org.openani.mediamp.io.BufferedSeekableInput
import org.openani.mediamp.io.SeekableInput
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A player's input over the file cache. Blocks the cache already holds, in memory or in the store on
// disk, come back without a request; the rest are fetched as the read position implies.
//
// Buffer player reads because media3 may traverse a container a few bytes at a time. Filling a
// window amortizes coroutine and synchronization overhead across those small reads.
internal class CloudSeekableInput(
    private val name: String,
    private val stream: SeekableInput,
    private val onClosed: () -> Unit = {},
    /**
     * Whether the entry still holds the store this input was opened on.
     *
     * deleteFiles replaces it outright, and nothing stops that happening mid-read: an input is not
     * a TorrentFileHandle, so the entry's handle count does not know it exists. This refuses to
     * keep serving a file the user asked to remove, and refuses in the one type the player treats
     * as recoverable.
     */
    private val isStale: () -> Boolean = { false },
    bufferSize: Int = BUFFER_PER_DIRECTION,
) : BufferedSeekableInput(bufferSize) {
    // The base class keeps bufferSize only to size its array; the fill needs the number too.
    private val window: Long = bufferSize.toLong()

    private val lock = SynchronizedObject()

    // Not the base class's own closed flag; see close().
    private var released = false

    override val size: Long get() = stream.size

    override fun fillBuffer() {
        if (isStale()) {
            bufferedOffsetStart = -1L
            throw IOException("[$name] the file this input was opened on was deleted")
        }
        val pos = position
        // Forward only: the bytes behind the position are ones the player has already consumed.
        fillBufferRange(pos, minOf(size, pos + window))
    }

    // Outside the lock: it blocks on the network, and close is what aborts it.
    override fun readFileToBuffer(fileOffset: Long, bufferOffset: Int, length: Int): Int {
        if (length == 0) return 0
        synchronized(lock) {
            if (released) throw IOException("[$name] closed while filling from the cloud")
        }
        if (stream.position != fileOffset) stream.seekTo(fileOffset)
        var filled = 0
        while (filled < length) {
            val read = stream.read(buf, bufferOffset + filled, length - filled)
            if (read <= 0) {
                throw IOException("[$name] the cloud stream ended at ${fileOffset + filled}, $length wanted")
            }
            filled += read
        }
        return filled
    }

    /**
     * Deliberately does not raise the base class's own closed flag.
     *
     * Its read() tests that flag before it even looks at the buffer and answers with
     * IllegalStateException, which ExoPlayer's Loader treats as a fatal load error rather than the
     * cancellation it is. Leaving it down lets a read that lands in the buffer keep answering with
     * the bytes it already holds, while anything that needs a refill gets an IOException out of
     * readFileToBuffer. Every resource is released here regardless.
     */
    override fun close() {
        synchronized(lock) {
            if (released) return
            released = true
        }
        try {
            runCatching { stream.close() }
        } finally {
            onClosed()
        }
    }

    companion object {
        // Match TorrentInput's buffer: small enough to keep cold seeks responsive.
        const val BUFFER_PER_DIRECTION = 64 * 1024
    }
}

/**
 * A player's read position over the file cache, as the SeekableInput [CloudSeekableInput] buffers.
 *
 * The stream behind it is opened on first use and opened again when its cache is closed under it:
 * an account switch retires the cache the player was reading, and media3 keeps one input for the
 * whole session, so an input that gave up with the cache would end playback for good. A stale input
 * — its file deleted — fails instead of reopening.
 *
 * It also tells the SDK when someone is waiting ([PikPakStreamReader.urgent]): after a seek and
 * before the first byte of a new playback, until a read returns bytes. Only the input can tell a
 * seek from a buffer fill; the preview input never asks, it must not outrank playback.
 */
internal class StreamSeekableInput(
    private val name: String,
    override val size: Long,
    private val open: suspend () -> Opened,
    private val urgentOnSeek: Boolean = false,
    private val isStale: () -> Boolean = { false },
    private val retryDelay: Duration = READ_RETRY_DELAY,
    private val retryWindow: Duration = READ_RETRY_WINDOW,
) : SeekableInput {
    /** A stream and whether the cache it reads from has been closed. */
    class Opened(val reader: PikPakStreamReader, val isDead: () -> Boolean = { false })

    /** Over one reader for its whole life, as a test drives it. */
    constructor(
        name: String,
        reader: PikPakStreamReader,
        retryDelay: Duration = READ_RETRY_DELAY,
        retryWindow: Duration = READ_RETRY_WINDOW,
    ) : this(name, reader.size, { Opened(reader) }, retryDelay = retryDelay, retryWindow = retryWindow)

    private val logger = logger<StreamSeekableInput>()

    @Volatile
    private var closed = false

    @Volatile
    private var current: Opened? = null

    @Volatile
    override var position: Long = 0L
        private set

    // A new playback waits on its first byte as much as a seek does
    @Volatile
    private var wantUrgent = urgentOnSeek

    override val bytesRemaining: Long get() = (size - position).coerceAtLeast(0)

    override fun seekTo(position: Long) {
        require(position >= 0) { "position must be >= 0, got $position" }
        if (position != this.position && urgentOnSeek) wantUrgent = true
        this.position = position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        readRidingOutFailures(buffer, offset, length)

    // The cache download retries the same way, but it is a background loop where a 30s
    // delay costs nothing. This one sits on the player's blocking read: the network returns a second
    // or two after screen-on while the player gives up after about three, which is why a failure
    // there ends playback by luck. Retry briefly and for a bounded stretch, so a link that is really
    // gone still reaches the player as a failure.
    private fun readRidingOutFailures(buffer: ByteArray, offset: Int, length: Int): Int {
        val since = TimeSource.Monotonic.markNow()
        while (true) {
            val opened = try {
                stream()
            } catch (e: Throwable) {
                if (e is CancellationException || e.isReadInterruption()) throw e
                throw if (e is IOException) e else IOException(e)
            }
            try {
                return asIoFailure { runBlockingInterruptible { readFrom(opened, buffer, offset, length) } }
            } catch (e: Throwable) {
                // Closing is how playback is torn down, and the reader reports it as a failure like
                // any other. Retrying it would spin for the whole window on every episode switch.
                if (closed || e is CancellationException || e.isReadInterruption()) throw e
                if (opened.isDead()) {
                    if (isStale()) throw IOException("[$name] the file this input was opened on was deleted", e)
                    // Not a failure of the read: the cache was replaced. The next pass opens the new one.
                    logger.info { "[$name] the cache closed under the stream; reopening at $position" }
                    current = null
                    continue
                }
                val waited = since.elapsedNow()
                if (waited >= retryWindow) {
                    logger.warn(e) { "[$name] read at $position failed, gave up after $waited" }
                    throw e
                }
                logger.warn(e) { "[$name] read at $position failed, retrying in $retryDelay" }
                runBlockingInterruptible { delay(retryDelay) }
            }
        }
    }

    private suspend fun readFrom(opened: Opened, buffer: ByteArray, offset: Int, length: Int): Int {
        val reader = opened.reader
        if (reader.position != position) reader.seekTo(position)
        val urgent = wantUrgent
        if (reader.urgent != urgent) reader.urgent = urgent
        val read = reader.read(buffer, offset, length)
        if (read > 0) {
            position += read
            if (urgent) {
                wantUrgent = false
                reader.urgent = false
            }
        }
        return read
    }

    private fun stream(): Opened {
        current?.takeIf { !it.isDead() }?.let { return it }
        if (closed) throw IOException("[$name] closed")
        return runBlockingInterruptible { open() }.also { opened ->
            current?.reader?.close()
            current = opened
            // close() may have run while this was opening
            if (closed) {
                opened.reader.close()
                throw IOException("[$name] closed")
            }
        }
    }

    override fun close() {
        closed = true
        current?.reader?.close()
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
