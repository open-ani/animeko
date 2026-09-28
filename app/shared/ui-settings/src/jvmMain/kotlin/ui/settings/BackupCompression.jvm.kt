package me.him188.ani.app.ui.settings

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun compressBackup(content: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
    GZIPOutputStream(output).use { it.write(content) }
}.toByteArray()

internal actual fun decompressBackup(content: ByteArray, maxBytes: Int): ByteArray =
    GZIPInputStream(ByteArrayInputStream(content)).use { input ->
        input.readNBytes(maxBytes + 1).also {
            require(it.size <= maxBytes) { "Backup expands beyond $maxBytes bytes" }
        }
    }
