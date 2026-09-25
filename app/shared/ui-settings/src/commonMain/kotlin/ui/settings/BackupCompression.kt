package me.him188.ani.app.ui.settings

/** Far above any settings backup; bounds memory when restoring a malformed or hostile file. */
internal const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

internal expect fun compressBackup(content: ByteArray): ByteArray

/** Throws if [content] is not a complete gzip stream or expands beyond [maxBytes]. */
internal expect fun decompressBackup(content: ByteArray, maxBytes: Int = MAX_BACKUP_BYTES): ByteArray
