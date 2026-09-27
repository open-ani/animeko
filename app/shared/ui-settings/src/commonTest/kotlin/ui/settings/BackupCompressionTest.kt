package me.him188.ani.app.ui.settings

import kotlinx.io.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BackupCompressionTest {
    @Test
    fun rawFileAndExpandedContentsHaveSeparateCaps() {
        assertEquals(10 * 1024 * 1024, MAX_BACKUP_FILE_BYTES)
        assertEquals(32 * 1024 * 1024, MAX_BACKUP_BYTES)
    }

    @Test
    fun compressedBackupRoundTrips() {
        val content = """{"format":"animeko-backup","version":1,"tracking":{"bindings":[]}}""".encodeToByteArray()
        val compressed = compressBackup(content)

        assertEquals(0x1f.toByte(), compressed[0])
        assertEquals(0x8b.toByte(), compressed[1])
        assertContentEquals(content, decompressBackup(compressed))
    }

    @Test
    fun roundTripsContentLargerThanOneChunk() {
        val content = Random(42).nextBytes(100_000)
        assertContentEquals(content, decompressBackup(compressBackup(content)))
    }

    @Test
    fun rejectsBackupThatExpandsBeyondLimit() {
        val compressed = compressBackup(ByteArray(10_000))
        assertFails { decompressBackup(compressed, maxBytes = 1_000) }
    }

    @Test
    fun boundedBackupReadAllowsLimitAndRejectsFirstByteOver() {
        fun source(bytes: ByteArray) = Buffer().apply { write(bytes, 0, bytes.size) }

        assertContentEquals(byteArrayOf(1, 2, 3), readBackupBytes(source(byteArrayOf(1, 2, 3)), maxBytes = 3))
        val oversized = source(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertFails { readBackupBytes(oversized, maxBytes = 3) }
        assertEquals(2L, oversized.size)
    }

    @Test
    fun backupTextLimitCountsUtf8Bytes() {
        requireBackupTextSize("abc", maxBytes = 3)
        assertFails { requireBackupTextSize("猫", maxBytes = 2) }
        assertFails { requireBackupTextSize("😀", maxBytes = 3) }
    }

    @Test
    fun rejectsCorruptData() {
        val compressed = compressBackup(Random(1).nextBytes(10_000))
        assertFails { decompressBackup(compressed.copyOf(compressed.size / 2)) }
    }
}
