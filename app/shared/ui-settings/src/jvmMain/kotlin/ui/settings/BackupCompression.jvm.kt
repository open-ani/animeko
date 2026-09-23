package me.him188.ani.app.ui.settings

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun compressBackup(content: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
    GZIPOutputStream(output).use { it.write(content) }
}.toByteArray()

internal actual fun decompressBackup(content: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(content)).use { it.readBytes() }
