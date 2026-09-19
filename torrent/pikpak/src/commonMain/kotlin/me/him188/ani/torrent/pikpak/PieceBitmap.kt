/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.io.writeBytes

// Data files are preallocated, so file length cannot prove completion: a piece never written reads
// back as zeros at its full length. The bitmap is the only persistent record of which pieces hold
// real bytes, and nothing downstream verifies those bytes — a wrong bit reaches the player as zeros.
//
// It therefore carries a durability rule its own code cannot enforce: the caller must have fsynced
// the data file before [flush] puts a bit on disk.
internal class PieceBitmap(
    private val file: SystemPath,
    private val pieceCount: Int,
) {
    private val byteCount = (pieceCount + 7) / 8
    private val lock = SynchronizedObject()
    private var bits = ByteArray(byteCount)
    private var dirty = false

    fun load(): BooleanArray {
        val stored = runCatching { if (file.exists()) file.readBytes() else null }.getOrNull()
        val fresh = ByteArray(byteCount)
        if (stored != null && stored.size >= byteCount) {
            stored.copyInto(fresh, endIndex = byteCount)
        }
        synchronized(lock) {
            bits = fresh
            dirty = false
        }
        return BooleanArray(pieceCount) { fresh[it / 8].toInt() and (1 shl (it % 8)) != 0 }
    }

    fun set(listIndex: Int) {
        if (listIndex !in 0 until pieceCount) return
        synchronized(lock) {
            val mask = (1 shl (listIndex % 8)).toByte()
            val old = bits[listIndex / 8]
            if (old.toInt() and mask.toInt() != 0) return
            bits[listIndex / 8] = (old.toInt() or mask.toInt()).toByte()
            dirty = true
        }
    }

    fun clear(listIndex: Int) {
        if (listIndex !in 0 until pieceCount) return
        synchronized(lock) {
            val mask = 1 shl (listIndex % 8)
            val old = bits[listIndex / 8].toInt()
            if (old and mask == 0) return
            bits[listIndex / 8] = (old and mask.inv()).toByte()
            dirty = true
        }
    }

    suspend fun flush() {
        val snapshot = synchronized(lock) {
            if (!dirty) return
            dirty = false
            bits.copyOf()
        }
        try {
            withContext(Dispatchers.IO_) { writeWhole(snapshot) }
        } catch (e: Throwable) {
            synchronized(lock) { dirty = true }
            throw e
        }
    }

    private fun writeWhole(snapshot: ByteArray) {
        val temp = file.resolveSibling(file.name + ".tmp")
        try {
            temp.writeBytes(snapshot)
            temp.moveTo(file)
        } catch (e: Throwable) {
            runCatching { if (temp.exists()) temp.delete() }
            file.writeBytes(snapshot)
        }
    }

    suspend fun delete() {
        synchronized(lock) {
            bits = ByteArray(byteCount)
            dirty = false
        }
        withContext(Dispatchers.IO_) {
            runCatching { if (file.exists()) file.delete() }
        }
    }

    companion object {
        fun pathFor(dataFile: SystemPath): SystemPath = dataFile.resolveSibling(dataFile.name + ".bits")
    }
}
