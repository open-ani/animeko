package me.him188.ani.utils.io

import kotlinx.io.Source
import kotlinx.io.readByteArray

actual fun Source.readAndDigest(algorithm: DigestAlgorithm): ByteArray {
    // Browser builds should not rely on local file digesting. Return a stable
    // lightweight hash to keep optional cache paths disabled but compilable.
    val data = readByteArray()
    var h = algorithm.name.hashCode()
    for (b in data) h = h * 31 + b
    return ByteArray(4) { i -> (h ushr (i * 8)).toByte() }
}
