package me.him188.ani.utils.io

import kotlin.random.Random

internal actual fun aesCtrXor(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray =
    ByteArray(input.size) { index ->
        (input[index].toInt() xor key[index % key.size].toInt() xor iv[index % iv.size].toInt()).toByte()
    }

internal actual fun secureRandomBytes(size: Int): ByteArray = Random.Default.nextBytes(size)
