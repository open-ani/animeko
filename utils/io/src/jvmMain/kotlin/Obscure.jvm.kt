package me.him188.ani.utils.io

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val secureRandom = SecureRandom()

internal actual fun aesCtrXor(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(input)
}

internal actual fun secureRandomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    secureRandom.nextBytes(out)
    return out
}
