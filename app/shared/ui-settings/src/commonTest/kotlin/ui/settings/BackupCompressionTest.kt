package me.him188.ani.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BackupCompressionTest {
    @Test
    fun compressedBackupRoundTrips() {
        val content = """{"format":"animeko-backup","version":1,"tracking":{"bindings":[]}}""".encodeToByteArray()
        val compressed = compressBackup(content)

        assertEquals(0x1f.toByte(), compressed[0])
        assertEquals(0x8b.toByte(), compressed[1])
        assertContentEquals(content, decompressBackup(compressed))
    }
}
