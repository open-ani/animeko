package me.him188.ani.app.ui.settings

import kotlinx.io.Source

/** Maximum size of either the selected backup file or its expanded contents. */
internal const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

/** Limit for the compressed/plain file before any decompression. */
internal const val MAX_BACKUP_FILE_BYTES = 10 * 1024 * 1024

internal expect fun compressBackup(content: ByteArray): ByteArray

/** Reads at most [maxBytes] plus one byte, so oversized sources are rejected without unbounded allocation. */
internal fun readBackupBytes(source: Source, maxBytes: Int = MAX_BACKUP_FILE_BYTES): ByteArray {
    require(maxBytes in 0 until Int.MAX_VALUE)
    val bytes = ByteArray(maxBytes + 1)
    var count = 0
    while (count < bytes.size) {
        val read = source.readAtMostTo(bytes, count, bytes.size)
        if (read == -1) break
        check(read > 0) { "Backup source made no progress" }
        count += read
    }
    require(count <= maxBytes) { "Backup file exceeds $maxBytes bytes" }
    return bytes.copyOf(count)
}

/** Rejects oversized plain-text and decompressed backups before parsing them. */
internal fun requireBackupTextSize(content: String, maxBytes: Int = MAX_BACKUP_BYTES) {
    require(maxBytes >= 0)
    var bytes = 0
    var index = 0
    while (index < content.length) {
        val code = content[index++].code
        val count = when {
            code < 0x80 -> 1
            code < 0x800 -> 2
            code in 0xD800..0xDBFF && index < content.length && content[index].code in 0xDC00..0xDFFF -> {
                index++
                4
            }
            code in 0xD800..0xDFFF -> 3
            else -> 3
        }
        require(count <= maxBytes - bytes) { "Backup contents exceed $maxBytes bytes" }
        bytes += count
    }
}

/** Throws if [content] is not a complete gzip stream or expands beyond [maxBytes]. */
internal expect fun decompressBackup(content: ByteArray, maxBytes: Int = MAX_BACKUP_BYTES): ByteArray
