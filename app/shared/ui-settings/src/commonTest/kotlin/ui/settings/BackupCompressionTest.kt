package me.him188.ani.app.ui.settings

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BackupCompressionTest {
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
    fun rejectsCorruptData() {
        val compressed = compressBackup(Random(1).nextBytes(10_000))
        assertFails { decompressBackup(compressed.copyOf(compressed.size / 2)) }
    }
}
