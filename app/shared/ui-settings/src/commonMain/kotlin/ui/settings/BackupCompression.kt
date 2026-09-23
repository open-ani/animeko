package me.him188.ani.app.ui.settings

internal expect fun compressBackup(content: ByteArray): ByteArray

internal expect fun decompressBackup(content: ByteArray): ByteArray
