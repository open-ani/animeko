/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.resolve
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the file cache relies on of the store: a block is held once written and never before, the
 * bitmap on disk vouches only for bytes that reached the medium, and a download finds what is
 * already there.
 */
class PikPakSparseStoreTest {
    private val block = 1024L

    private fun payload(size: Int) = ByteArray(size) { (it * 31 % 251).toByte() }

    private fun store(
        file: SystemPath,
        size: Long,
        persistInterval: Duration = 50.milliseconds,
        onHeld: (Int) -> Unit = {},
        openDataFile: (SystemPath) -> DataFile = ::openRandomAccessDataFile,
    ) = PikPakSparseStore(
        dataFile = file,
        size = size,
        parentCoroutineContext = Dispatchers.IO,
        onHeld = onHeld,
        blockSize = block,
        persistInterval = persistInterval,
        openDataFile = openDataFile,
    )

    private fun blockOf(content: ByteArray, index: Int) =
        content.copyOfRange((index * block).toInt(), minOf(content.size, ((index + 1) * block).toInt()))

    @Test
    fun `a block reads back once written and not before`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-store").resolve("ep.mkv")
        val content = payload((block * 3 + 100).toInt())
        val held = CopyOnWriteArrayList<Int>()
        val store = store(file, content.size.toLong(), onHeld = { held += it })
        try {
            assertNull(store.read("k", block, block.toInt()), "an unwritten block read back")
            store.write("k", block * 3, blockOf(content, 3))
            store.write("k", block, blockOf(content, 1))
            store.write("k", block, blockOf(content, 1))

            assertContentEquals(blockOf(content, 1), store.read("k", block, block.toInt()))
            assertContentEquals(blockOf(content, 3), store.read("k", block * 3, 100))
            // Written past the end first, so the file has a hole where block 0 goes: never zeros for it
            assertNull(store.read("k", 0, block.toInt()), "the hole before a written block read back")
            assertEquals(listOf(3, 1), held, "each block is reported held once")
            assertEquals(block + 100, store.heldBytes.value)
            assertFalse(store.isComplete)
        } finally {
            store.close()
        }
    }

    @Test
    fun `missing names only what is not held`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-missing").resolve("ep.mkv")
        val content = payload((block * 6).toInt())
        val store = store(file, content.size.toLong())
        try {
            store.write("k", block * 2, blockOf(content, 2))
            store.write("k", block * 3, blockOf(content, 3))
            assertEquals(
                listOf(0L until block * 2, block * 4 until block * 6),
                store.missing("k", listOf(0L until block * 6)),
            )
            assertEquals(
                listOf(block * 5 until block * 6, 10L..20L),
                store.missing("k", listOf(block * 5 until block * 6, block * 2 until block * 4, 10L..20L)),
                "ranges keep the order asked, held ones drop out",
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun `progress survives a restart and the file is never preallocated`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-restart").resolve("ep.mkv")
        val content = payload((block * 8).toInt())
        val first = store(file, content.size.toLong())
        first.write("k", 0, blockOf(content, 0))
        first.write("k", block, blockOf(content, 1))
        assertEquals(block * 2, file.length(), "the data file grew past what was written")
        first.close()

        val second = store(file, content.size.toLong())
        try {
            assertTrue(second.isHeld(0) && second.isHeld(1))
            assertFalse(second.isHeld(2))
            assertEquals(block * 2, second.heldBytes.value)
            assertContentEquals(blockOf(content, 1), second.read("k", block, block.toInt()))
        } finally {
            second.close()
        }
    }

    @Test
    fun `a truncated data file invalidates the blocks past its end`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-truncated").resolve("ep.mkv")
        val content = payload((block * 4).toInt())
        val first = store(file, content.size.toLong())
        for (index in 0 until 4) first.write("k", index * block, blockOf(content, index))
        first.close()
        RandomAccessFile(file, "rw").use { it.setLength(block * 2) }

        val second = store(file, content.size.toLong())
        try {
            assertEquals(listOf(true, true, false, false), (0 until 4).map { second.isHeld(it) })
            assertEquals(listOf(block * 2 until block * 4), second.missing("k", listOf(0L until block * 4)))
        } finally {
            second.close()
        }
    }

    @Test
    fun `no bit is persisted while the data file cannot be synced`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-durable").resolve("ep.mkv")
        val content = payload((block * 2).toInt())
        val store = store(file, content.size.toLong(), openDataFile = { NeverDurable(openRandomAccessDataFile(it)) })
        store.write("k", 0, blockOf(content, 0))
        assertTrue(store.isHeld(0), "a written block is held for readers at once")
        delay(300.milliseconds)
        store.close()

        val reopened = store(file, content.size.toLong())
        try {
            assertFalse(reopened.isHeld(0), "a bit reached the disk ahead of an fsync of its bytes")
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `a delete removes the file and the record of it`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-delete").resolve("ep.mkv")
        val content = payload((block * 2).toInt())
        val store = store(file, content.size.toLong())
        store.write("k", 0, blockOf(content, 0))
        store.delete()
        assertFalse(file.exists())
        assertFalse(PieceBitmap.pathFor(file).exists())
        assertEquals(0L, store.heldBytes.value)

        val fresh = store(file, content.size.toLong())
        try {
            assertEquals(0L, fresh.heldBytes.value)
        } finally {
            fresh.close()
        }
    }

    @Test
    fun `a file marked complete is held whole`() = runBlocking {
        val file = SystemPaths.createTempDirectory("sparse-complete").resolve("ep.mkv")
        val content = payload((block * 2 + 1).toInt())
        RandomAccessFile(file, "rw").use { it.write(content, 0, content.size) }
        val store = store(file, content.size.toLong())
        try {
            store.markComplete()
            assertTrue(store.isComplete)
            assertTrue(store.missing("k", listOf(0L until content.size)).isEmpty())
            assertContentEquals(blockOf(content, 2), store.read("k", block * 2, 1))
        } finally {
            store.close()
        }
    }

    /** A data file whose sync always fails: what reaches the kernel never reaches the medium. */
    private class NeverDurable(private val delegate: DataFile) : DataFile by delegate {
        override fun sync() = throw IOException("the device refused the sync")
    }
}
